package co.topl.broker.processors

import java.util.stream

import cats.effect.kernel.Sync
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.dataApi.GenusQueryAlgebra
import co.topl.brambl.dataApi.RpcChannelResource
import co.topl.brambl.models.box.Lock
import co.topl.brambl.syntax.LvlType
import co.topl.brambl.utils.Encoding
import co.topl.daml.api.model.topl.levels.LvlTransferRequest
import com.daml.ledger.javaapi.data.Command

trait TransferRequestProcessorAlgebra[F[_]] {

  def prepareTransactionM(
    transferRequest:         LvlTransferRequest,
    transferRequestContract: LvlTransferRequest.ContractId
  ): F[stream.Stream[Command]]

}

object TransferRequestProcessorAlgebra {

  def makeTransferRequestProcessorAlgebra[F[_]: Sync](
    transactionBuilderApi: TransactionBuilderApi[F],
    utxoAlgebra:           GenusQueryAlgebra[F]
  ) = new TransferRequestProcessorAlgebra[F] with RpcChannelResource {

    override def prepareTransactionM(
      transferRequest:         LvlTransferRequest,
      transferRequestContract: LvlTransferRequest.ContractId
    ): F[stream.Stream[Command]] = {
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
      } yield transferRequestContract
        .exerciseLvlTransferRequest_Accept(
          Encoding.encodeToBase58(ioTransaction.toByteString.toByteArray())
        )
        .commands()
        .stream()
    }
  }
}
