package co.topl.dapp

import scala.jdk.CollectionConverters._

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Async
import co.topl.shared.SharedDAMLUtils
import fs2.interop.reactivestreams._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scopt.OParser

object Main extends IOApp with ParameterProcessorModule with InitializerModule with DappProcessorModule {

  override def run(args: List[String]): IO[ExitCode] =
    OParser.runParser(parser, args, DappCLIParamConfig()) match {
      case (Some(_), h :: tail) =>
        IO(OParser.runEffects(h :: tail)) *> IO.pure(ExitCode.Error)
      case (Some(config), Nil) =>
        implicit val logging = Slf4jFactory.create[IO]
        val logger = logging.getLogger
        runWithParams(config)(IO.asyncForIO, logger)
      case (None, effects) =>
        IO(OParser.runEffects(effects.reverse.tail.reverse)) *> IO.pure(ExitCode.Error)
    }

  def runWithParams[F[_]: Async: Logger](paramConfig: DappCLIParamConfig): F[ExitCode] = {
    import cats.implicits._
    import org.typelevel.log4cats.syntax._
    for {
      someAccessToken <-
        if (paramConfig.damlHub)
          paramConfig.damlAccessToken
            .map(t => SharedDAMLUtils.login2DamlHub[F](paramConfig.damlHost, t))
            .getOrElse(Async[F].pure(None))
        else Async[F].pure(paramConfig.damlAccessToken)
      client <- SharedDAMLUtils.createClient(
        paramConfig.damlHost,
        paramConfig.damlPort,
        paramConfig.damlSecurityEnabled,
        someAccessToken
      )
      _            <- SharedDAMLUtils.connect(1, client)
      transactions <- SharedDAMLUtils.getTransactions(client, paramConfig.dappParty)
      // process all invitations
      fs2Transaction <- Async[F].delay(fromPublisher(transactions, 1)(Async[F]))
      _              <- info"Starting DAPP processor"
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
                    processConversationInvitationState(paramConfig, client, evt),
                    processSignTransaction(paramConfig, client, evt)
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
      _                    <- Async[F].never[Unit]
    } yield ExitCode.Success
  }

}
