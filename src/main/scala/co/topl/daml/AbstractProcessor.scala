package co.topl.daml

import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.Transaction
import com.google.protobuf.Empty
import io.reactivex.Single

import java.util.stream
import scala.concurrent.ExecutionContext
import cats.effect.IO
import cats.effect.unsafe.implicits.global

abstract class AbstractProcessor[T, U, V](
  val damlAppContext: DamlAppContext,
  val toplContext:    ToplContext,
  consumer:           java.util.function.BiFunction[T, U, V],
  onError:            java.util.function.Function[Throwable, Boolean]
) {

  implicit val implicitDamlAppContext = damlAppContext

  implicit val executionContext: ExecutionContext = toplContext.actorSystem.dispatcher

  implicit val system = toplContext.actorSystem

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])]

  def processTransaction(tx: Transaction): Boolean =
    processTransactionAux(tx)(processEvent).handleError(t => onError(t)).unsafeRunSync()

}
