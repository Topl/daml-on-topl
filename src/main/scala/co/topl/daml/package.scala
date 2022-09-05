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

package object daml {

  type RpcErrorOr[T] = EitherT[Future, RpcClientFailure, T]

  def processEventAux(templateIdentifier: Identifier, event: CreatedEvent)(
    processor:                            => stream.Stream[Command]
  ): stream.Stream[Command] =
    if (event.getTemplateId() == templateIdentifier)
      processor
    else stream.Stream.empty()

  def processTransactionAux(
    tx: Transaction
  )(
    processEvent:            (String, CreatedEvent) => stream.Stream[Command]
  )(implicit damlAppContext: DamlAppContext): Single[Empty] = {
    val exerciseCommands = tx
      .getEvents()
      .stream()
      .filter(e => e.isInstanceOf[CreatedEvent])
      .map(e => e.asInstanceOf[CreatedEvent])
      .flatMap(e => processEvent(tx.getWorkflowId(), e))
      .collect(stream.Collectors.toList())
    if (!exerciseCommands.isEmpty()) {
      return damlAppContext.client
        .getCommandClient()
        .submitAndWait(
          tx.getWorkflowId(),
          damlAppContext.appId,
          UUID.randomUUID().toString(),
          damlAppContext.operatorParty,
          exerciseCommands
        )
    } else return SingleSubject.create()
  }

  def utf8StringToLatin1ByteArray(str: String) = str.zipWithIndex
    .map(e => str.codePointAt(e._2).toByte)
    .toArray

}
