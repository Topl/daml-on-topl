package co.topl.broker

import java.util.UUID

import cats.effect.kernel.Async
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.dataApi.GenusQueryAlgebra
import co.topl.brambl.models.box.Lock
import co.topl.brambl.syntax.LvlType
import co.topl.brambl.utils.Encoding
import co.topl.daml.api.model.topl.levels.LvlTransferRequest
import com.daml.ledger.javaapi.data.CommandsSubmission
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

object LvlTransferRequestModule {

  def createLvlTransferRequestCommandsSubmission[F[_]: Async: Logger](
    paramConfig:     BrokerCLIParamConfig,
    evt:             com.daml.ledger.javaapi.data.CreatedEvent,
    transferRequest: LvlTransferRequest
  )(implicit
    utxoAlgebra:           GenusQueryAlgebra[F],
    transactionBuilderApi: TransactionBuilderApi[F]
  ): F[CommandsSubmission] = {

    import co.topl.brambl.codecs.AddressCodecs._
    import cats.implicits._
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
    } yield CommandsSubmission
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
  }

  def processLvlTransferRequest[F[_]: Async: Logger](
    paramConfig: BrokerCLIParamConfig,
    evt:         com.daml.ledger.javaapi.data.CreatedEvent
  )(implicit
    utxoAlgebra:           GenusQueryAlgebra[F],
    transactionBuilderApi: TransactionBuilderApi[F]
  ) =
    if (evt.getTemplateId() == LvlTransferRequest.TEMPLATE_ID) {
      val transferRequest =
        LvlTransferRequest.valueDecoder().decode(evt.getArguments())
      import cats.implicits._
      info"Starting Processing LvlTransferRequest" >>
      createLvlTransferRequestCommandsSubmission(paramConfig, evt, transferRequest).map(_.some)
    } else
      Async[F].delay(None)
}
