package co.topl.daml

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.Identifier
import com.daml.ledger.javaapi.data.Transaction
import com.daml.ledger.rxjava.LedgerClient
import com.google.protobuf.Empty
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import org.slf4j.LoggerFactory

import java.util.UUID
import java.util.stream
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

abstract class AbstractProcessor[T, U, V](
  val damlAppContext: DamlAppContext,
  val toplContext:    ToplContext,
  consumer:           java.util.function.BiFunction[T, U, V],
  onError:            java.util.function.Function[Throwable, Boolean]
) {

  implicit val implicitDamlAppContext = damlAppContext

  val logger = LoggerFactory.getLogger(this.getClass())

  implicit val executionContext: ExecutionContext = toplContext.actorSystem.dispatcher

  implicit val system = toplContext.actorSystem

  def processEventAux[T, C](
    templateIdentifier: Identifier,
    extractContract:    CreatedEvent => T,
    extractContractId:  CreatedEvent => C,
    callback:           (T, C) => Boolean,
    event:              CreatedEvent
  )(
    processor: (T, C) => IO[stream.Stream[Command]]
  ): IO[(Boolean, stream.Stream[Command])] =
    if (event.getTemplateId() == templateIdentifier) {
      for {
        contractId   <- IO.apply(extractContractId(event))
        contract     <- IO.apply(extractContract(event))
        mustContinue <- IO.apply(callback.apply(contract, contractId))
        resultStream <- processor(contract, contractId)
      } yield (mustContinue, resultStream)
    } else IO.apply((true, stream.Stream.empty()))

  def processEventsM(tx: Transaction, processEvent: (String, CreatedEvent) => IO[(Boolean, stream.Stream[Command])]) =
    tx.getEvents()
      .asScala
      .filter(_.isInstanceOf[CreatedEvent])
      .map(_.asInstanceOf[CreatedEvent])
      .foldLeft(IO.apply((true, stream.Stream.empty[Command]()))) { (firstIO, b) =>
        for {
          pair1 <- firstIO
          (bool0, str0) = pair1
          pair2 <- processEvent(tx.getWorkflowId(), b)
          (bool1, str1) = pair2
        } yield ((bool0 && bool1), stream.Stream.concat(str0, str1))
      }

  def submitToDaml(tx: Transaction, exerciseCommands: java.util.List[Command])(implicit
    damlAppContext:    DamlAppContext
  ) =
    if (!exerciseCommands.isEmpty()) {
      IO.blocking(
        damlAppContext.client
          .getCommandClient()
          .submitAndWait(
            tx.getWorkflowId(),
            damlAppContext.appId,
            UUID.randomUUID().toString(),
            damlAppContext.operatorParty,
            exerciseCommands
          )
      )
    } else {
      IO.apply(SingleSubject.create())
    }

  def processTransactionAux(
    tx: Transaction
  )(
    processEvent: (String, CreatedEvent) => IO[(Boolean, stream.Stream[Command])]
  ): IO[Boolean] =
    (for {
      pair <- processEventsM(tx, processEvent)
      (mustContinue, exerciseCommandsStream) = pair
      exerciseCommands = exerciseCommandsStream.collect(stream.Collectors.toList())
      _ <- submitToDaml(tx, exerciseCommands)
    } yield
      if (!exerciseCommands.isEmpty()) {
        mustContinue;
      } else true)

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])]

  def processTransactionIO(tx: Transaction): IO[Boolean] =
    processTransactionAux(tx)(processEvent).handleError(t => onError(t))

  def processTransaction(tx: Transaction): Boolean =
    processTransactionIO(tx).unsafeRunSync()

}
