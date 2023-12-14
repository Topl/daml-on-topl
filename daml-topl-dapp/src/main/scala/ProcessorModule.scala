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
import co.topl.brambl.syntax._
import co.topl.brambl.builders.TransactionBuilderApi.implicits._
import co.topl.brambl.constants.NetworkConstants
import cats.effect.kernel.Async
import cats.implicits._

trait ProcessorModule {

  def processWalletFellowInvitation[F[_]: Async](
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletFellowInvitation.TEMPLATE_ID) {
      Async[F].blocking(
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
      Async[F].delay(evt)

  def processWalletInvitationAccepted[F[_]: Async](
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletInvitationAccepted.TEMPLATE_ID) {
      Async[F].blocking(
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
      Async[F].delay(evt)

  def processWalletConversationInvitation[F[_]: Async](
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (evt.getTemplateId() == WalletConversationInvitation.TEMPLATE_ID) {
      Async[F].blocking(
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
      Async[F].delay(evt)

  def processConversationInvitationState[F[_]: Async](
    paramConfig: CLIParamConfig,
    client:      DamlLedgerClient,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  ) =
    if (
      evt.getTemplateId() == ConversationInvitationState.TEMPLATE_ID &&
      ConversationInvitationState.valueDecoder.decode(evt.getArguments()).invitedFellows.isEmpty()
    ) {
      import co.topl.brambl.codecs.LockTemplateCodecs.decodeLockTemplate
      import io.circe.parser.parse
      val walletKeyApi = WalletKeyApi.make[F]()
      val walletApi = WalletApi.make(walletKeyApi)
      val conversationInvitationState = ConversationInvitationState.valueDecoder.decode(evt.getArguments())
      import cats.implicits._
      for {
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
        _ <- Async[F].blocking(
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
                        lock.lockAddress(paramConfig.network.networkId, NetworkConstants.MAIN_LEDGER_ID).toBase58()
                      )
                  ).asJava
                )
                .withActAs(paramConfig.dappParty)
            )
        )

      } yield evt
    } else
      Async[F].delay(evt)

}
