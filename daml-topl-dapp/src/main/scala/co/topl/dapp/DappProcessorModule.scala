package co.topl.dapp

import java.util.UUID

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.implicits._
import co.topl.brambl.builders.TransactionBuilderApi.implicits._
import co.topl.brambl.constants.NetworkConstants
import co.topl.brambl.models.transaction.IoTransaction
import co.topl.brambl.servicekit.WalletKeyApi
import co.topl.brambl.syntax._
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.CredentiallerInterpreter
import co.topl.brambl.wallet.WalletApi
import co.topl.crypto.encryption.VaultStore
import co.topl.daml.api.model.topl.levels.LvlTransferUnproved
import co.topl.daml.api.model.topl.wallet.ConversationInvitationState
import co.topl.daml.api.model.topl.wallet.CurrentInteraction
import co.topl.daml.api.model.topl.wallet.Vault
import co.topl.daml.api.model.topl.wallet.WalletConversationInvitation
import co.topl.daml.api.model.topl.wallet.WalletFellowInvitation
import co.topl.daml.api.model.topl.wallet.WalletFellowship
import co.topl.daml.api.model.topl.wallet.WalletInvitationAccepted
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.interop.reactivestreams._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._
import quivr.models.VerificationKey

trait DappProcessorModule {

  def processWalletFellowInvitation[F[_]: Async](
    paramConfig: DappCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletFellowInvitation.TEMPLATE_ID) {
      Async[F]
        .blocking(
          client
            .getCommandClient()
            .submitAndWaitForTransaction(
              CommandsSubmission
                .create(
                  "damlhub",
                  UUID.randomUUID().toString,
                  List(
                    WalletFellowInvitation.Contract
                      .fromCreatedEvent(evt)
                      .id
                      .exerciseWalletFellowInvitation_Accept(true)
                  ).asJava
                )
                .withActAs(paramConfig.dappParty)
            )
        )
        .map(_ => evt)
    } else
      Async[F].delay(evt)

  def processWalletInvitationAccepted[F[_]: Async](
    paramConfig: DappCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletInvitationAccepted.TEMPLATE_ID) {
      Async[F]
        .blocking(
          client
            .getCommandClient()
            .submitAndWaitForTransaction(
              CommandsSubmission
                .create(
                  "damlhub",
                  UUID.randomUUID().toString, {
                    val walletInvitationAccepted = WalletInvitationAccepted.valueDecoder.decode(evt.getArguments())
                    if (
                      (walletInvitationAccepted.owner == paramConfig.dappParty) &&
                      (walletInvitationAccepted.invitee == paramConfig.dappParty) &&
                      (walletInvitationAccepted.fellows.size == 1) &&
                      (walletInvitationAccepted.fellows.get(0) == paramConfig.dappParty)
                    )
                      List(
                        WalletInvitationAccepted.Contract
                          .fromCreatedEvent(evt)
                          .id
                          .exerciseWalletInvitationAccepted_MakePrivate("self")
                      ).asJava
                    else
                      List(
                        WalletInvitationAccepted.Contract
                          .fromCreatedEvent(evt)
                          .id
                          .exerciseWalletInvitationAccepted_MakePrivate(UUID.randomUUID().toString)
                      ).asJava
                  }
                )
                .withActAs(paramConfig.dappParty)
            )
        )
        .map(_ => evt)
    } else
      Async[F].delay(evt)

  def processWalletConversationInvitation[F[_]: Async: Logger](
    paramConfig: DappCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletConversationInvitation.TEMPLATE_ID) {
      info"Processing WalletConversationInvitation" *> Async[F]
        .blocking(
          client
            .getCommandClient()
            .submitAndWaitForTransaction(
              CommandsSubmission
                .create(
                  "damlhub",
                  UUID.randomUUID().toString,
                  List(
                    WalletConversationInvitation.Contract
                      .fromCreatedEvent(evt)
                      .id
                      .exerciseWalletConversationInvitation_Accept("self", "default")
                  ).asJava
                )
                .withActAs(paramConfig.dappParty)
            )
        )
        .map(_ => evt)
    } else
      Async[F].delay(evt)

  def processSignTransaction[F[_]: Async: Logger](
    paramConfig: DappCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == LvlTransferUnproved.TEMPLATE_ID) {
      import co.topl.crypto.encryption.VaultStore.Codecs._
      import io.circe.parser.decode
      val lvlTransferUnproved = LvlTransferUnproved.valueDecoder.decode(evt.getArguments())
      val lvlTransferUnprovedTx = Encoding.decodeFromBase58(lvlTransferUnproved.unprovedTx).toOption.get
      val ioTransaction = IoTransaction.parseFrom(lvlTransferUnprovedTx)
      val walletStateAlgebraDAML = WalletStateAlgebraDAML
        .make[F](
          paramConfig,
          client
        )
      if (lvlTransferUnproved.provedBy.asScala.find(_ == paramConfig.dappParty).isEmpty) {
        for {
          _                    <- info"Processing LvlTransferUnproved [unsigned]"
          activeContractClient <- Async[F].delay(client.getActiveContractSetClient())
          _ <- Async[F].timeout(
            (for {
              _ <- info"Waiting for wallet fellowship"
              activeContracts <- Async[F].delay(
                activeContractClient.getActiveContracts(
                  WalletFellowship.contractFilter(),
                  Set(paramConfig.dappParty).asJava,
                  false
                )
              )
              activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
              activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
              _ <-
                if (activeContracts.isEmpty) Sync[F].sleep(1000.millis)
                else Sync[F].unit
            } yield activeContracts.isEmpty).iterateWhile(identity),
            10.seconds // die after 10 seconds
          )
          vaults <- Async[F].delay(
            activeContractClient.getActiveContracts(Vault.contractFilter(), Set(paramConfig.dappParty).asJava, false)
          )
          vault <- fromPublisher(vaults, 1)(Async[F]).compile.toList.map(_.get(0).get.activeContracts.get(0))
          activeContracts <- Async[F].delay(
            activeContractClient.getActiveContracts(
              CurrentInteraction.contractFilter(),
              Set(paramConfig.dappParty).asJava,
              false
            )
          )
          keyData = vault.data.vault
          walletKeyApi = WalletKeyApi.make[F]()
          walletApi = WalletApi.make(walletKeyApi)
          vaultStore <- Async[F].fromEither(
            decode[VaultStore[F]](keyData)
          )
          eitherKeyPair <- walletApi.extractMainKey(vaultStore, paramConfig.password.getBytes())
          keyPair <- Async[F].fromEither(
            eitherKeyPair
          )
          credentialer <- Sync[F]
            .delay(
              CredentiallerInterpreter
                .make[F](walletApi, walletStateAlgebraDAML, keyPair)
            )
          provedTransaction <- credentialer.prove(ioTransaction)
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
                          .exerciseLvlTransferUnproved_Prove(
                            paramConfig.dappParty,
                            Encoding.encodeToBase58(provedTransaction.toByteArray)
                          )
                      ).asJava
                    )
                    .withActAs(paramConfig.dappParty)
                )
            )
            .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
        } yield evt
      } else
        Async[F].delay(evt)
    } else
      Async[F].delay(evt)

  def processConversationInvitationState[F[_]: Async: Logger](
    paramConfig: DappCLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (
      evt.getTemplateId() == ConversationInvitationState.TEMPLATE_ID &&
      ConversationInvitationState.valueDecoder.decode(evt.getArguments()).invitedFellows.isEmpty() &&
      ConversationInvitationState.valueDecoder
        .decode(evt.getArguments())
        .toGetConversation
        .asScala
        .toList
        .find(_.party == paramConfig.dappParty)
        .isDefined
    ) {
      import co.topl.brambl.codecs.LockTemplateCodecs.decodeLockTemplate
      import io.circe.parser.parse
      val walletKeyApi = WalletKeyApi.make[F]()
      val walletApi = WalletApi.make(walletKeyApi)
      val conversationInvitationState = ConversationInvitationState.valueDecoder.decode(evt.getArguments())
      import cats.implicits._
      for {
        _       <- info"Exercising ConversationInvitationState.ConversationInvitationState_GetInteraction"
        json    <- Async[F].fromEither(parse(conversationInvitationState.lockTemplate))
        decoded <- Async[F].fromEither(decodeLockTemplate[F](json))
        vks <- conversationInvitationState.acceptedParties.asScala.toList
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
        eitherLock <- decoded.build(vks)
        lock <- Async[F].fromEither(
          eitherLock
        )
        vkIdx = conversationInvitationState.acceptedParties.asScala.toList.zipWithIndex
          .find(_._1.party == paramConfig.dappParty)
          .map(_._2)
          .get
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
                        .exerciseConversationInvitationState_GetInteraction(
                          paramConfig.dappParty,
                          lock.lockAddress(paramConfig.network.networkId, NetworkConstants.MAIN_LEDGER_ID).toBase58(),
                          Encoding.encodeToBase58(vks(vkIdx).toByteArray)
                        )
                    ).asJava
                  )
                  .withActAs(paramConfig.dappParty)
              )
          )
          .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
      } yield evt
    } else
      Async[F].delay(evt)

}
