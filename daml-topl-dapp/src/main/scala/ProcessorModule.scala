import java.util.UUID

import scala.jdk.CollectionConverters._
import cats.effect.IO
import co.topl.daml.api.model.topl.wallet.WalletFellowInvitation
import co.topl.daml.api.model.topl.wallet.WalletInvitationAccepted
import com.daml.ledger.rxjava.DamlLedgerClient
import com.daml.ledger.javaapi.data.CommandsSubmission
import co.topl.daml.api.model.topl.wallet.WalletConversationInvitation
import co.topl.daml.api.model.topl.wallet.ConversationInvitationState
import co.topl.brambl.servicekit.WalletKeyApi
import co.topl.brambl.wallet.WalletApi
import quivr.models.VerificationKey
import co.topl.brambl.utils.Encoding

trait ProcessorModule {

  def processWalletFellowInvitation(
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletFellowInvitation.TEMPLATE_ID) {
      IO.blocking(
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
      ).map(_ => evt)
    } else
      IO(evt)

  def processWalletInvitationAccepted(
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletInvitationAccepted.TEMPLATE_ID) {
      IO.blocking(
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
      ).map(_ => evt)
    } else
      IO(evt)

  def processWalletConversationInvitation(
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletConversationInvitation.TEMPLATE_ID) {
      IO.blocking(
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
      ).map(_ => evt)
    } else
      IO(evt)

  def processConversationInvitationState(
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (
      evt.getTemplateId() == ConversationInvitationState.TEMPLATE_ID &&
      ConversationInvitationState.valueDecoder.decode(evt.getArguments()).invitedFellows.isEmpty()
    ) {
      val walletKeyApi = WalletKeyApi.make[IO]()
      val walletApi = WalletApi.make(walletKeyApi)
      val conversationInvitationState = ConversationInvitationState.valueDecoder.decode(evt.getArguments())
      import cats.implicits._
      for {
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
        _ <- IO.blocking(
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
                      .exerciseConversationInvitationState_GetInteraction(paramConfig.dappParty, "default")
                  ).asJava
                )
                .withActAs(paramConfig.dappParty)
            )
        )

      } yield evt
    } else
      IO(evt)

}
