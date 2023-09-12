package processors

import co.topl.daml.api.model.topl.levels.LvlTransferRequest
import java.util.stream
import com.daml.ledger.javaapi.data.Command

trait TransferRequestProcessorAlgebra[F[_]] {

  def prepareTransactionM(
    transferRequest:         LvlTransferRequest,
    transferRequestContract: LvlTransferRequest.ContractId
  ): F[stream.Stream[Command]]

}

object TransferRequestProcessorAlgebra {

  def makeTransferRequestProcessorAlgebra[F[_]] = new TransferRequestProcessorAlgebra[F] {

    override def prepareTransactionM(
      transferRequest:         LvlTransferRequest,
      transferRequestContract: LvlTransferRequest.ContractId
    ): F[stream.Stream[Command]] = ???

  }

}