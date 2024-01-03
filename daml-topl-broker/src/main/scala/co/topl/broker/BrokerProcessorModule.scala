package co.topl.broker

import java.time.Instant
import java.util.UUID

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import cats.implicits._
import co.topl.brambl.Context
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.builders.TransactionBuilderApi.implicits._
import co.topl.brambl.constants.NetworkConstants
import co.topl.brambl.dataApi.BifrostQueryAlgebra
import co.topl.brambl.dataApi.GenusQueryAlgebra
import co.topl.brambl.dataApi.RpcChannelResource
import co.topl.brambl.models.Datum
import co.topl.brambl.models.Event
import co.topl.brambl.models.box.Lock
import co.topl.brambl.models.transaction.IoTransaction
import co.topl.brambl.servicekit.WalletKeyApi
import co.topl.brambl.syntax.LvlType
import co.topl.brambl.syntax._
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.CredentiallerInterpreter
import co.topl.brambl.wallet.WalletApi
import co.topl.daml.api.model.topl.levels.LvlTransferProved
import co.topl.daml.api.model.topl.levels.LvlTransferRequest
import co.topl.daml.api.model.topl.levels.LvlTransferUnproved
import co.topl.daml.api.model.topl.utils.ProveStatus
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import co.topl.daml.api.model.topl.wallet.ConversationInvitationState
import co.topl.shared.WalletStateAlgebraDAML
import com.daml.ledger.javaapi.data
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.rxjava.DamlLedgerClient
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._
import quivr.models.VerificationKey

trait BrokerProcessorModule extends RpcChannelResource {

  def processLvlTransferRequest[F[_]: Async: Logger](
    paramConfig: BrokerCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == LvlTransferRequest.TEMPLATE_ID) {
      val transferRequest =
        LvlTransferRequest.valueDecoder().decode(evt.getArguments())
      import co.topl.brambl.codecs.AddressCodecs._
      import cats.implicits._
      val utxoAlgebra = GenusQueryAlgebra.make[F](
        channelResource(
          paramConfig.bifrostHost,
          paramConfig.bifrostPort,
          paramConfig.bifrostSecurityEnabled
        )
      ) // FIXME: pass as param
      val transactionBuilderApi = TransactionBuilderApi.make[F](
        paramConfig.network.networkId,
        NetworkConstants.MAIN_LEDGER_ID
      )
      for {
        fromAddress   <- decodeAddress(transferRequest.from.address).liftTo[F]
        toAddress     <- decodeAddress(transferRequest.to.address).liftTo[F]
        changeAddress <- decodeAddress(transferRequest.changeAddress).liftTo[F]
        txos <- utxoAlgebra
          .queryUtxo(fromAddress)
        eitherIoTransaction <- transactionBuilderApi
          .buildTransferAmountTransaction(
            LvlType,
            txos,
            Lock.Predicate.parseFrom(
              Encoding.decodeFromBase58Check(transferRequest.lockProposition).toOption.get
            ),
            transferRequest.to.amount,
            toAddress,
            changeAddress,
            transferRequest.fee
          )
        ioTransaction <- eitherIoTransaction.liftTo[F]
        _             <- info"Processing LvlTransferRequest"
        _ <- Async[F]
          .blocking(
            client
              .getCommandClient()
              .submitAndWaitForTransaction(
                CommandsSubmission
                  .create(
                    "damlhub",
                    UUID.randomUUID().toString,
                    LvlTransferRequest.Contract
                      .fromCreatedEvent(evt)
                      .id
                      .exerciseLvlTransferRequest_Accept(
                        Encoding.encodeToBase58(ioTransaction.toByteString.toByteArray())
                      )
                      .commands()
                  )
                  .withActAs(paramConfig.operatorParty)
              )
          )
      } yield evt
    } else
      Async[F].delay(evt)

  def checkSignatures[F[_]: Async: Logger](
    paramConfig: BrokerCLIParamConfig,
    client:      DamlLedgerClient,
    tx:          IoTransaction
  ) = {
    import cats.implicits._

    import co.topl.crypto.signing.ExtendedEd25519
    import quivr.models.KeyPair
    val mockKeyPair: KeyPair = (new ExtendedEd25519).deriveKeyPairFromSeed(
      Array.fill(96)(0: Byte)
    )
    val walletKeyApi = WalletKeyApi.make[F]() // FIXME: pass as param
    val walletApi = WalletApi.make(walletKeyApi) // FIXME: pass as param
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
    paramConfig: BrokerCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
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
          _ <- Async[F]
            .blocking(
              client
                .getCommandClient()
                .submitAndWaitForTransaction(
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
                )
            )
            .whenA(errors.isEmpty)
          _ <- Async[F]
            .blocking(
              client
                .getCommandClient()
                .submitAndWaitForTransaction(
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
                )
            )
            .unlessA(errors.isEmpty)
        } yield evt
      } else
        Async[F].delay(evt)
    } else
      Async[F].delay(evt)

  def processLvlTransferProved[F[_]: Async: Logger](
    paramConfig: BrokerCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == LvlTransferProved.TEMPLATE_ID) {
      val lvlTransferProved =
        LvlTransferProved.valueDecoder().decode(evt.getArguments())
      import cats.implicits._

      if (lvlTransferProved.sendStatus.isInstanceOf[Sent]) {
        for {
          _ <- info"Confirming Transaction LvlTransferProved"
          _ <- Async[F]
            .blocking(
              client
                .getCommandClient()
                .submitAndWaitForTransaction(
                  CommandsSubmission
                    .create(
                      "damlhub",
                      UUID.randomUUID().toString,
                      List(
                        LvlTransferProved.Contract
                          .fromCreatedEvent(evt)
                          .id
                          .exerciseLvlTransferProved_Confirm(
                            lvlTransferProved.requestId,
                            1
                          )
                      ).asJava
                    )
                    .withActAs(paramConfig.operatorParty)
                )
            )
        } yield evt
      } else if (lvlTransferProved.sendStatus == new Pending(data.Unit.getInstance())) {
        val ioTx = IoTransaction.parseFrom(Encoding.decodeFromBase58(lvlTransferProved.provedTx).toOption.get)
        for {
          _ <- info"Sending Transaction LvlTransferProved"
          _ <- BifrostQueryAlgebra
            .make[F](
              channelResource(
                paramConfig.bifrostHost,
                paramConfig.bifrostPort,
                paramConfig.bifrostSecurityEnabled
              )
            )
            .broadcastTransaction(ioTx)
          _ <- Async[F]
            .blocking(
              client
                .getCommandClient()
                .submitAndWaitForTransaction(
                  CommandsSubmission
                    .create(
                      "damlhub",
                      UUID.randomUUID().toString,
                      List(
                        LvlTransferProved.Contract
                          .fromCreatedEvent(evt)
                          .id
                          .exerciseLvlTransferProved_Sent(
                            new Sent(
                              Instant.now(),
                              lvlTransferProved.requestor,
                              lvlTransferProved.requestId
                            )
                          )
                      ).asJava
                    )
                    .withActAs(paramConfig.operatorParty)
                )
            )
        } yield evt
      } else
        Async[F].delay(evt)
    } else
      Async[F].delay(evt)

  def processConversationInvitationState[F[_]: Async: Logger](
    paramConfig: BrokerCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == ConversationInvitationState.TEMPLATE_ID) {
      val conversationInvitationState =
        ConversationInvitationState.valueDecoder().decode(evt.getArguments())
      import co.topl.brambl.codecs.LockTemplateCodecs.decodeLockTemplate
      import io.circe.parser.parse
      val walletKeyApi = WalletKeyApi.make[F]() // FIXME: pass as param
      val walletApi = WalletApi.make(walletKeyApi) // FIXME: pass as param
      if (
        conversationInvitationState.invitedFellows.isEmpty() &&
        conversationInvitationState.toGetConversation.isEmpty()
      )
        for {
          _       <- info"Starting Processing ConversationInvitationState"
          json    <- Async[F].fromEither(parse(conversationInvitationState.lockTemplate))
          decoded <- Async[F].fromEither(decodeLockTemplate[F](json))
          vksFirst <- conversationInvitationState.acceptedParties.asScala.toList
            .map(_.vk.get())
            .map(vk =>
              walletApi.deriveChildVerificationKey(
                VerificationKey.parseFrom(
                  Encoding.decodeFromBase58(vk).toOption.get
                ),
                1
              )
            )
            .sequence
          eitherLock <- decoded.build(vksFirst)
          lockFirst <- Async[F].fromEither(
            eitherLock
          )
          vksChange <- conversationInvitationState.acceptedParties.asScala.toList
            .map(_.vk.get())
            .map(vk =>
              walletApi.deriveChildVerificationKey(
                VerificationKey.parseFrom(
                  Encoding.decodeFromBase58(vk).toOption.get
                ),
                2
              )
            )
            .sequence
          eitherLockChange <- decoded.build(vksChange)
          lockChange <- Async[F].fromEither(
            eitherLockChange
          )
          _ <- info"Processing ConversationInvitationState"
          _ <- Async[F]
            .blocking(
              client
                .getCommandClient()
                .submitAndWaitForTransaction(
                  CommandsSubmission
                    .create(
                      "damlhub",
                      UUID.randomUUID().toString,
                      List(
                        ConversationInvitationState.Contract
                          .fromCreatedEvent(evt)
                          .id
                          .exerciseConversationInvitationState_ToInteraction(
                            lockFirst
                              .lockAddress(paramConfig.network.networkId, NetworkConstants.MAIN_LEDGER_ID)
                              .toBase58(),
                            lockChange
                              .lockAddress(paramConfig.network.networkId, NetworkConstants.MAIN_LEDGER_ID)
                              .toBase58(),
                            Encoding.encodeToBase58Check(lockFirst.getPredicate.toByteArray)
                          )
                      ).asJava
                    )
                    .withActAs(paramConfig.operatorParty)
                )
            )

        } yield evt
      else Async[F].delay(evt)
    } else
      Async[F].delay(evt)

}
