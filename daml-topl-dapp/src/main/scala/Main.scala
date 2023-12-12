import java.util.Collections

import scala.jdk.CollectionConverters._

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
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
import scopt.OParser

object Main extends IOApp with ParameterProcessorModule with InitializerModule with ProcessorModule {

  case class LoginResponse(access_token: String, token: String)

  implicit val loginResponseDecoder: EntityDecoder[IO, LoginResponse] = jsonOf[IO, LoginResponse]

  def login2DamlHub(
    damlHost:        String,
    damlAccessToken: String
  ): IO[Option[String]] = for {
    uri <- IO.fromEither(
      Uri.fromString(s"https://${damlHost}/.hub/v1/sa/login")
    )
    req = Request[IO](
      method = Method.POST,
      uri = uri
    ).withHeaders(
      Headers(
        Authorization(BasicCredentials(damlAccessToken))
      )
    )
    res <- EmberClientBuilder
      .default[IO]
      .build
      .use(c => c.run(req).use(r => r.as[LoginResponse]))
  } yield Some(res.access_token)

  def createClient(
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
    IO {
      enableSecurity
        .compose(enableAccessToken)(DamlLedgerClient.newBuilder(host, port))
        .build()
    }
  }

  override def run(args: List[String]): IO[ExitCode] =
    OParser.runParser(parser, args, CLIParamConfig()) match {
      case (None, effects) =>
        IO(OParser.runEffects(effects.tail)) *> IO.pure(ExitCode.Error)
      case (Some(config), _) =>
        runWithParams(config)
    }

  def getTransactions(client: DamlLedgerClient, operatorParty: String) = IO(
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

  def connect(tries: Int, client: DamlLedgerClient): IO[Unit] =
    if (tries > 5) IO.raiseError(new RuntimeException("Could not connect to the ledger"))
    else
      IO(client.connect()).recoverWith { case _ =>
        IO.println("Retrying connection...") *> connect(tries + 1, client)
      }

  def runWithParams(paramConfig: CLIParamConfig): IO[ExitCode] = {
    import cats.implicits._
    for {
      someAccessToken <-
        if (paramConfig.damlHub)
          paramConfig.damlAccessToken
            .map(t => login2DamlHub(paramConfig.damlHost, t))
            .getOrElse(IO.pure(None))
        else IO.pure(paramConfig.damlAccessToken)
      client <- createClient(
        paramConfig.damlHost,
        paramConfig.damlPort,
        paramConfig.damlSecurityEnabled,
        someAccessToken
      )
      _                    <- connect(1, client)
      activeContractClient <- IO(client.getActiveContractSetClient())
      _                    <- createVaultState(client, activeContractClient, paramConfig)
      _                    <- createWalletFellowship(client, activeContractClient, paramConfig)
      _                    <- createWalletLockTemplate(client, activeContractClient, paramConfig)
      _                    <- createCurrentInteraction(client, activeContractClient, paramConfig)
      transactions         <- getTransactions(client, paramConfig.dappParty)
      // process all invitations
      fs2Transaction <- IO(fromPublisher(transactions, 1)(IO.asyncForIO))
      _ <- fs2Transaction
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
                processConversationInvitationState(paramConfig, client, evt),
              ).sequence
            )
        )
        .compile
        .drain
    } yield ExitCode.Success
  }

}
