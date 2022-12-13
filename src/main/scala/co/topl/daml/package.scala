package co.topl

import java.util.UUID
import java.util.concurrent.TimeoutException
import java.util.function.BiFunction
import java.util.stream

import scala.collection.JavaConverters._
import scala.concurrent.Future

import cats.data.EitherT
import cats.effect.IO
import co.topl.akkahttprpc.RpcClientFailure
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.Identifier
import com.daml.ledger.javaapi.data.Transaction
import com.daml.ledger.rxjava.LedgerClient
import com.google.protobuf.Empty
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject

package object daml {

  type RpcErrorOr[T] = EitherT[Future, RpcClientFailure, T]

  case class RpcClientFailureException(failure: RpcClientFailure) extends Throwable

  def utf8StringToLatin1ByteArray(str: String) = str.zipWithIndex
    .map(e => str.codePointAt(e._2).toByte)
    .toArray

}
