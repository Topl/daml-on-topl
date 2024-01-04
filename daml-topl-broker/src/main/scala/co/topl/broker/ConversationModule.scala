package co.topl.broker

import java.util.UUID

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Sync
import cats.implicits._
import co.topl.brambl.builders.TransactionBuilderApi.implicits._
import co.topl.brambl.constants.NetworkConstants
import co.topl.brambl.dataApi.RpcChannelResource
import co.topl.brambl.syntax._
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.WalletApi
import co.topl.daml.api.model.topl.wallet.ConversationInvitationState
import com.daml.ledger.javaapi.data.CommandsSubmission
import quivr.models.VerificationKey
import org.typelevel.log4cats.Logger

trait ConversationModule extends RpcChannelResource {

  def createConversationModuleCommandSumission[F[_]: Sync](
    paramConfig:                 BrokerCLIParamConfig,
    conversationInvitationState: ConversationInvitationState,
    evt:                         com.daml.ledger.javaapi.data.CreatedEvent
  )(implicit walletApi:          WalletApi[F]) = {
    import co.topl.brambl.codecs.LockTemplateCodecs.decodeLockTemplate
    import io.circe.parser.parse
    assert(conversationInvitationState.invitedFellows.isEmpty())
    assert(conversationInvitationState.toGetConversation.isEmpty())
    for {
      json    <- Sync[F].fromEither(parse(conversationInvitationState.lockTemplate))
      decoded <- Sync[F].fromEither(decodeLockTemplate[F](json))
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
      lockFirst <- Sync[F].fromEither(
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
      lockChange <- Sync[F].fromEither(
        eitherLockChange
      )
    } yield CommandsSubmission
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
  }

  def processConversationInvitationState[F[_]: Sync: Logger](
    paramConfig:        BrokerCLIParamConfig,
    evt:                com.daml.ledger.javaapi.data.CreatedEvent
  )(implicit walletApi: WalletApi[F]): F[Option[CommandsSubmission]] =
    if (evt.getTemplateId() == ConversationInvitationState.TEMPLATE_ID) {
      import cats.implicits._
      import org.typelevel.log4cats.syntax._
      val conversationInvitationState =
        ConversationInvitationState.valueDecoder().decode(evt.getArguments())
      if (
        conversationInvitationState.invitedFellows.isEmpty() &&
        conversationInvitationState.toGetConversation.isEmpty()
      )
        info"Starting Processing ConversationInvitationState" >>
        createConversationModuleCommandSumission(
          paramConfig,
          conversationInvitationState,
          evt
        ).map(_.some)
      else Sync[F].delay(None)
    } else
      Sync[F].delay(None)

}
