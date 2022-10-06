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

package object daml {

  type RpcErrorOr[T] = EitherT[Future, RpcClientFailure, T]

  def processEventAux[T, C](
    templateIdentifier: Identifier,
    extractContract:    CreatedEvent => T,
    extractContractId:  CreatedEvent => C,
    callback:           (T, C) => Boolean,
    event:              CreatedEvent
  )(
    processor: (T, C) => stream.Stream[Command]
  ): (Boolean, stream.Stream[Command]) =
    if (event.getTemplateId() == templateIdentifier) {
      val contractId = extractContractId(event)
      val contract = extractContract(event)
      val mustContinue = callback.apply(contract, contractId)
      (mustContinue, processor(contract, contractId))
    } else (true, stream.Stream.empty())

  def processTransactionAux(
    tx: Transaction
  )(
    processEvent:            (String, CreatedEvent) => (Boolean, stream.Stream[Command])
  )(implicit damlAppContext: DamlAppContext): Boolean = {
    val mustContinueAndexerciseCommands = tx
      .getEvents()
      .stream()
      .filter(e => e.isInstanceOf[CreatedEvent])
      .map(e => e.asInstanceOf[CreatedEvent])
      .reduce(
        (true, stream.Stream.empty[Command]()),
        (a: (Boolean, stream.Stream[Command]), b: CreatedEvent) => {
          val (bool0, str0) = a
          val (bool1, str1) = processEvent(tx.getWorkflowId(), b)
          ((bool0 && bool1), stream.Stream.concat(str0, str1))
        },
        (a: (Boolean, stream.Stream[Command]), b: (Boolean, stream.Stream[Command])) => {
          val (bool0, str0) = a
          val (bool1, str1) = b
          ((bool0 && bool1), stream.Stream.concat(str0, str1))
        }
      )
    val (mustContinue, exerciseCommandsStream) = mustContinueAndexerciseCommands
    val exerciseCommands = exerciseCommandsStream.collect(stream.Collectors.toList())
    if (!exerciseCommands.isEmpty()) {
      damlAppContext.client
        .getCommandClient()
        .submitAndWait(
          tx.getWorkflowId(),
          damlAppContext.appId,
          UUID.randomUUID().toString(),
          damlAppContext.operatorParty,
          exerciseCommands
        )
      return mustContinue;
    } else return true;
  }

  def utf8StringToLatin1ByteArray(str: String) = str.zipWithIndex
    .map(e => str.codePointAt(e._2).toByte)
    .toArray

}
