package co.topl.daml

import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.Transaction
import com.google.protobuf.Empty
import io.reactivex.Single

import java.util.stream
import scala.concurrent.ExecutionContext

abstract class AbstractProcessor(damlAppContext: DamlAppContext, toplContext: ToplContext) {

  implicit val implicitDamlAppContext = damlAppContext

  implicit val executionContext: ExecutionContext = toplContext.actorSystem.dispatcher

  implicit val system = toplContext.actorSystem

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command]

  def processTransaction(tx: Transaction): Single[Empty] =
    processTransactionAux(tx)(processEvent)
}
