package co.topl.broker

import scala.jdk.CollectionConverters._

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.kernel.Async
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.constants.NetworkConstants
import co.topl.brambl.dataApi.GenusQueryAlgebra
import co.topl.brambl.dataApi.RpcChannelResource
import co.topl.brambl.servicekit.WalletKeyApi
import co.topl.brambl.wallet.WalletApi
import co.topl.shared.SharedDAMLUtils
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.interop.reactivestreams._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scopt.OParser

object Main extends IOApp with ParameterProcessorModule with RpcChannelResource {

  override def run(args: List[String]): IO[ExitCode] =
    OParser.runParser(parser, args, BrokerCLIParamConfig()) match {
      case (Some(_), h :: tail) =>
        IO(OParser.runEffects(h :: tail)) *> IO.pure(ExitCode.Error)
      case (Some(config), Nil) =>
        implicit val logging = Slf4jFactory.create[IO]
        val logger = logging.getLogger
        runWithParams(config)(IO.asyncForIO, logger)
      case (None, effects) =>
        IO(OParser.runEffects(effects.reverse.tail.reverse)) *> IO.pure(ExitCode.Error)
    }

  def runRequest[F[_]: Async](someCommandSubmission: Option[CommandsSubmission], client: DamlLedgerClient) = {
    import cats.implicits._
    someCommandSubmission
      .map(commandsSubmission =>
        Async[F]
          .blocking(
            client
              .getCommandClient()
              .submitAndWaitForTransaction(commandsSubmission)
          )
      )
      .sequence
  }

  def runWithParams[F[_]: Async: Logger](paramConfig: BrokerCLIParamConfig): F[ExitCode] = {
    import cats.implicits._
    import org.typelevel.log4cats.syntax._

    val walletKeyApi = WalletKeyApi.make[F]()
    implicit val walletApi = WalletApi.make(walletKeyApi)
    implicit val transactionBuilderApi = TransactionBuilderApi.make[F](
      paramConfig.network.networkId,
      NetworkConstants.MAIN_LEDGER_ID
    )
    implicit val channelRes = channelResource(
      paramConfig.bifrostHost,
      paramConfig.bifrostPort,
      paramConfig.bifrostSecurityEnabled
    )
    implicit val utxoAlgebra = GenusQueryAlgebra.make[F](
      channelRes
    )
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
      transactions <- SharedDAMLUtils.getTransactions(client, paramConfig.operatorParty)
      // // process all invitations
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
                    ConversationModule.processConversationInvitationState(paramConfig, evt),
                    LvlTransferRequestModule.processLvlTransferRequest(paramConfig, evt),
                    LvlTransferUnprovedModule.processLvlTransferUnproved(paramConfig, client, evt),
                    TransferProvedModule.processLvlTransferProved(paramConfig, evt)
                  ).map(_.flatMap(x => runRequest(x, client))).sequence
                )
            )
            .compile
            .drain
        )
        .allocated
      _ <- Async[F].never[Unit]
    } yield ExitCode.Success
  }

}
