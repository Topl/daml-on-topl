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

  def utf8StringToLatin1ByteArray(str: String) = str.zipWithIndex
    .map(e => str.codePointAt(e._2).toByte)
    .toArray

}
