import java.util.Collections

import scala.jdk.CollectionConverters._

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Async
import cats.effect.std
import cats.implicits._
import com.daml.ledger.javaapi.data.FiltersByParty
import com.daml.ledger.javaapi.data.LedgerOffset
import com.daml.ledger.javaapi.data.NoFilter
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.interop.reactivestreams._
import io.circe.generic.auto._
import io.grpc.netty.GrpcSslContexts
import org.http4s.BasicCredentials
import org.http4s.EntityDecoder
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scopt.OParser

object Main extends IOApp with ParameterProcessorModule with InitializerModule with ProcessorModule {

  case class LoginResponse(access_token: String, token: String)

  def login2DamlHub[F[_]: Async](
    damlHost:        String,
    damlAccessToken: String
  ): F[Option[String]] = {
    implicit val loginResponseDecoder: EntityDecoder[F, LoginResponse] = jsonOf[F, LoginResponse]
    for {
      uri <- Async[F].fromEither(
        Uri.fromString(s"https://${damlHost}/.hub/v1/sa/login")
      )
      req = Request[F](
        method = Method.POST,
        uri = uri
      ).withHeaders(
        Headers(
          Authorization(BasicCredentials(damlAccessToken))
        )
      )
      res <- EmberClientBuilder
        .default
        .build
        .use(c => c.run(req).use(r => r.as[LoginResponse]))
    } yield Some(res.access_token)
  }

  def createClient[F[_]: Async](
    host:                String,
    port:                Int,
    damlSecurityEnabled: Boolean,
    damlAccessToken:     Option[String]
  ) = {
    import cats._
    val enableSecurity: Endo[DamlLedgerClient.Builder] =
      (if (damlSecurityEnabled)
         _.withSslContext(GrpcSslContexts.forClient().build())
       else identity _)
    val enableAccessToken: Endo[DamlLedgerClient.Builder] =
      damlAccessToken
        .map(x => (y: DamlLedgerClient.Builder) => y.withAccessToken(x))
        .getOrElse(identity _)
    Async[F].delay {
      enableSecurity
        .compose(enableAccessToken)(DamlLedgerClient.newBuilder(host, port))
        .build()
    }
  }

  override def run(args: List[String]): IO[ExitCode] =
    OParser.runParser(parser, args, CLIParamConfig()) match {
      case (Some(_), h :: tail) =>
        IO(OParser.runEffects(h :: tail)) *> IO.pure(ExitCode.Error)
      case (Some(config), Nil) =>
        implicit val logging = Slf4jFactory.create[IO]
        val logger = logging.getLogger
        runWithParams(config)(IO.asyncForIO, logger, IO.consoleForIO)
      case (None, effects) =>
        IO(OParser.runEffects(effects.reverse.tail.reverse)) *> IO.pure(ExitCode.Error)
    }

  def getTransactions[F[_]: Async](client: DamlLedgerClient, operatorParty: String) = Async[F].delay(
    client
      .getTransactionsClient()
      .getTransactions(
        LedgerOffset.LedgerEnd.getInstance(),
        new FiltersByParty(
          Collections.singletonMap(operatorParty, NoFilter.instance)
        ),
        true
      )
  )

  def connect[F[_]: Async: std.Console](tries: Int, client: DamlLedgerClient): F[Unit] =
    if (tries > 5) Async[F].raiseError(new RuntimeException("Could not connect to the ledger"))
    else
      Async[F].delay(client.connect()).recoverWith { case _ =>
        std.Console[F].println("Retrying connection...") *> connect(tries + 1, client)
      }

  def runWithParams[F[_]: Async: Logger: std.Console](paramConfig: CLIParamConfig): F[ExitCode] = {
    import cats.implicits._
    import org.typelevel.log4cats.syntax._
    for {
      someAccessToken <-
        if (paramConfig.damlHub)
          paramConfig.damlAccessToken
            .map(t => login2DamlHub[F](paramConfig.damlHost, t))
            .getOrElse(Async[F].pure(None))
        else Async[F].pure(paramConfig.damlAccessToken)
      client <- createClient(
        paramConfig.damlHost,
        paramConfig.damlPort,
        paramConfig.damlSecurityEnabled,
        someAccessToken
      )
      _            <- connect(1, client)
      transactions <- getTransactions(client, paramConfig.dappParty)
      // process all invitations
      fs2Transaction <- Async[F].delay(fromPublisher(transactions, 1)(Async[F]))
      _              <- info"Starting processor"
      _ <- Async[F]
        .background(
          fs2Transaction
            .evalMap(t =>
              t.getEvents()
                .asScala
                .toList
                .collect { case x: com.daml.ledger.javaapi.data.CreatedEvent => x }
                .traverse(evt =>
                  List(
                    processWalletFellowInvitation(paramConfig, client, evt),
                    processWalletInvitationAccepted(paramConfig, client, evt),
                    processWalletConversationInvitation(paramConfig, client, evt),
                    processConversationInvitationState(paramConfig, client, evt)
                  ).sequence
                )
            )
            .compile
            .drain
        )
        .allocated
      activeContractClient <- Async[F].delay(client.getActiveContractSetClient())
      _                    <- info"Creating vault"
      _                    <- createVault(client, activeContractClient, paramConfig)
      _                    <- info"Creating vault state"
      _                    <- createVaultState(client, activeContractClient, paramConfig)
      _                    <- info"Creating wallet fellowship"
      _                    <- createWalletFellowship(client, activeContractClient, paramConfig)
      _                    <- info"Creating wallet lock template"
      _                    <- createWalletLockTemplate(client, activeContractClient, paramConfig)
      _                    <- info"Creating current interaction"
      _                    <- createCurrentInteraction(client, activeContractClient, paramConfig)
      _ <- Async[F].never[Unit]
    } yield ExitCode.Success
  }

}
