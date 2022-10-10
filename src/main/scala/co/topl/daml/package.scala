package co.topl

import cats.data.EitherT
import scala.concurrent.Future
import co.topl.akkahttprpc.RpcClientFailure
import com.daml.ledger.javaapi.data.Identifier
import com.daml.ledger.javaapi.data.CreatedEvent
import java.util.stream
import com.daml.ledger.javaapi.data.Command
import io.reactivex.Single
import com.google.protobuf.Empty
import com.daml.ledger.javaapi.data.Transaction
import com.daml.ledger.rxjava.LedgerClient
import java.util.UUID
import io.reactivex.subjects.SingleSubject
import scala.collection.JavaConverters._
import java.util.function.BiFunction
import cats.effect.IO

package object daml {

  type RpcErrorOr[T] = EitherT[Future, RpcClientFailure, T]

  case class RpcClientFailureException(failure: RpcClientFailure) extends Throwable

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
    processEvent:            (String, CreatedEvent) => IO[(Boolean, stream.Stream[Command])]
  )(implicit damlAppContext: DamlAppContext): IO[Boolean] =
    (for {
      pair <- processEventsM(tx, processEvent)
      (mustContinue, exerciseCommandsStream) = pair
      exerciseCommands = exerciseCommandsStream.collect(stream.Collectors.toList())
      _ <- submitToDaml(tx, exerciseCommands)
    } yield
      if (!exerciseCommands.isEmpty()) {
        mustContinue;
      } else true)

  def utf8StringToLatin1ByteArray(str: String) = str.zipWithIndex
    .map(e => str.codePointAt(e._2).toByte)
    .toArray

}
