package co.topl.broker

import java.time.Instant
import java.util.UUID

import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import co.topl.brambl.dataApi.BifrostQueryAlgebra
import co.topl.brambl.models.TransactionId
import co.topl.brambl.models.transaction.IoTransaction
import co.topl.brambl.utils.Encoding
import co.topl.daml.api.model.topl.levels.LvlTransferProved
import co.topl.daml.api.model.topl.utils.sendstatus.Confirmed
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import com.daml.ledger.javaapi.data
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.javaapi.data.CreatedEvent
import io.grpc.ManagedChannel
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

object TransferProvedModule {

  def archiveCommandSubmission(paramConfig: BrokerCLIParamConfig, evt: CreatedEvent) = CommandsSubmission
    .create(
      "damlhub",
      UUID.randomUUID().toString,
      List(
        LvlTransferProved.Contract
          .fromCreatedEvent(evt)
          .id
          .exerciseLvlTransferProved_Archive()
      ).asJava
    )
    .withActAs(paramConfig.operatorParty)

  def confirmCommandSubmission(
    paramConfig:       BrokerCLIParamConfig,
    lvlTransferProved: LvlTransferProved,
    evt:               CreatedEvent
  ) = CommandsSubmission
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

  def sentCommandSubmission(
    txId:              TransactionId,
    paramConfig:       BrokerCLIParamConfig,
    lvlTransferProved: LvlTransferProved,
    evt:               CreatedEvent
  ) =
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
                Encoding.encodeToBase58(txId.value.toByteArray())
              )
            )
        ).asJava
      )
      .withActAs(paramConfig.operatorParty)

  def processLvlTransferProved[F[_]: Async: Logger](
    paramConfig:              BrokerCLIParamConfig,
    evt:                      CreatedEvent
  )(implicit channelResource: Resource[F, ManagedChannel]) =
    if (evt.getTemplateId() == LvlTransferProved.TEMPLATE_ID) {
      val lvlTransferProved =
        LvlTransferProved.valueDecoder().decode(evt.getArguments())
      import cats.implicits._

      if (lvlTransferProved.sendStatus.isInstanceOf[Confirmed]) {
        info"Archiving Transaction LvlTransferProved" >>
        archiveCommandSubmission(paramConfig, evt).some.pure[F]
      } else if (lvlTransferProved.sendStatus.isInstanceOf[Sent]) {
        info"Confirming Transaction LvlTransferProved"
        confirmCommandSubmission(paramConfig, lvlTransferProved, evt).some.pure[F]
      } else if (lvlTransferProved.sendStatus == new Pending(data.Unit.getInstance())) {
        val ioTx = IoTransaction.parseFrom(Encoding.decodeFromBase58(lvlTransferProved.provedTx).toOption.get)
        for {
          _ <- info"Sending Transaction LvlTransferProved"
          txId <- BifrostQueryAlgebra
            .make[F](
              channelResource
            )
            .broadcastTransaction(ioTx)
        } yield sentCommandSubmission(txId, paramConfig, lvlTransferProved, evt).some
      } else
        Async[F].delay(None)
    } else
      Async[F].delay(None)
}
