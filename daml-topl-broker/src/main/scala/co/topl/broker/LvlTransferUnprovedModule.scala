package co.topl.broker

import java.util.UUID

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import co.topl.brambl.Context
import co.topl.brambl.dataApi.BifrostQueryAlgebra
import co.topl.brambl.dataApi.RpcChannelResource
import co.topl.brambl.models.Datum
import co.topl.brambl.models.Event
import co.topl.brambl.models.transaction.IoTransaction
import co.topl.brambl.syntax._
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.CredentiallerInterpreter
import co.topl.brambl.wallet.WalletApi
import co.topl.daml.api.model.topl.levels.LvlTransferUnproved
import co.topl.daml.api.model.topl.utils.ProveStatus
import co.topl.shared.WalletStateAlgebraDAML
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.rxjava.DamlLedgerClient
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

object LvlTransferUnprovedModule extends RpcChannelResource {

  def checkSignatures[F[_]: Async: Logger](
    paramConfig:        BrokerCLIParamConfig,
    client:             DamlLedgerClient,
    tx:                 IoTransaction
  )(implicit walletApi: WalletApi[F]) = {
    import cats.implicits._

    import co.topl.crypto.signing.ExtendedEd25519
    import quivr.models.KeyPair
    val mockKeyPair: KeyPair = (new ExtendedEd25519).deriveKeyPairFromSeed(
      Array.fill(96)(0: Byte)
    )
    val walletStateAlgebraDAML = WalletStateAlgebraDAML
      .make[F](
        paramConfig.operatorParty,
        client
      )

    for {
      credentialer <- Async[F].delay(
        CredentiallerInterpreter
          .make[F](
            walletApi,
            walletStateAlgebraDAML,
            mockKeyPair
          )
      )
      tipBlockHeader <- BifrostQueryAlgebra
        .make[F](
          channelResource(
            paramConfig.bifrostHost,
            paramConfig.bifrostPort,
            paramConfig.bifrostSecurityEnabled
          )
        )
        .blockByDepth(1L)
        .map(_.get._2)
      context <- Async[F].delay(
        Context[F](
          tx,
          tipBlockHeader.slot,
          Map(
            "header" -> Datum().withHeader(
              Datum.Header(Event.Header(tipBlockHeader.height))
            )
          ).lift
        )
      )
      validationErrors <- credentialer.validate(tx, context)
    } yield validationErrors
  }

  def processLvlTransferUnproved[F[_]: Async: Logger](
    paramConfig:        BrokerCLIParamConfig,
    client:             DamlLedgerClient,
    evt:                com.daml.ledger.javaapi.data.CreatedEvent
  )(implicit walletApi: WalletApi[F]): F[Option[CommandsSubmission]] =
    if (evt.getTemplateId() == LvlTransferUnproved.TEMPLATE_ID) {
      val lvlTransferUnproved =
        LvlTransferUnproved.valueDecoder().decode(evt.getArguments())
      import cats.implicits._

      if (
        lvlTransferUnproved.someProvedTx.isPresent() &&
        lvlTransferUnproved.proofStatus == ProveStatus.TOVALIDATE
      ) {
        val provedTx = lvlTransferUnproved.someProvedTx.get()
        val ioTx = IoTransaction.parseFrom(Encoding.decodeFromBase58(provedTx).toOption.get)
        for {
          _      <- info"Validating Transaction LvlTransferUnproved"
          errors <- checkSignatures(paramConfig, client, ioTx)
        } yield
          if (errors.isEmpty)
            CommandsSubmission
              .create(
                "damlhub",
                UUID.randomUUID().toString,
                List(
                  LvlTransferUnproved.Contract
                    .fromCreatedEvent(evt)
                    .id
                    .exerciseLvlTransferUnproved_ProofCompleted()
                ).asJava
              )
              .withActAs(paramConfig.operatorParty)
              .some
          else
            CommandsSubmission
              .create(
                "damlhub",
                UUID.randomUUID().toString,
                List(
                  LvlTransferUnproved.Contract
                    .fromCreatedEvent(evt)
                    .id
                    .exerciseLvlTransferUnproved_ProofIncomplete()
                ).asJava
              )
              .withActAs(paramConfig.operatorParty)
              .some
      } else
        Async[F].delay(None)
    } else
      Async[F].delay(None)
}
