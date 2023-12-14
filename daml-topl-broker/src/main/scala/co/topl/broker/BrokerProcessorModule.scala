package co.topl.broker

import java.util.UUID

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import cats.implicits._
import co.topl.brambl.builders.TransactionBuilderApi.implicits._
import co.topl.brambl.constants.NetworkConstants
import co.topl.brambl.servicekit.WalletKeyApi
import co.topl.brambl.syntax._
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.WalletApi
import co.topl.daml.api.model.topl.wallet.ConversationInvitationState
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.rxjava.DamlLedgerClient
import quivr.models.VerificationKey

trait BrokerProcessorModule {

  def processConversationInvitationState[F[_]: Async](
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
