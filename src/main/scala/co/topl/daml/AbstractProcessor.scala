package co.topl.daml

import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.Transaction
import com.google.protobuf.Empty
import io.reactivex.Single

import java.util.stream
import scala.concurrent.ExecutionContext

abstract class AbstractProcessor[T, U, V](
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  consumer:       java.util.function.BiFunction[T, U, V]
) {

  implicit val implicitDamlAppContext = damlAppContext

  implicit val executionContext: ExecutionContext = toplContext.actorSystem.dispatcher

  implicit val system = toplContext.actorSystem

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command])

  def processTransaction(tx: Transaction): Boolean =
    processTransactionAux(tx)(processEvent)

}
