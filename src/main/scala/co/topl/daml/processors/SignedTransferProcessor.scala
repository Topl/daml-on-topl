package co.topl.daml.processors

import java.util.stream
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.attestation.keyManagement.{KeyRing, KeyfileCurve25519, KeyfileCurve25519Companion, PrivateKeyCurve25519}
import co.topl.client.Brambl
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.attestation.Address
import io.circe.parser.parse
import scala.io.Source
import java.io.File
import cats.data.EitherT
import co.topl.utils.StringDataTypes
import co.topl.attestation.AddressCodec.implicits._
import scodec.bits._
import co.topl.akkahttprpc.RpcErrorFailure
import co.topl.akkahttprpc.InvalidParametersError
import io.circe.DecodingFailure
import scala.concurrent.Future
import co.topl.modifier.transaction.PolyTransfer
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import org.slf4j.LoggerFactory
import co.topl.daml.api.model.topl.transfer.SignedTransfer
import co.topl.rpc.ToplRpc

import co.topl.akkahttprpc.implicits.client._
import co.topl.rpc.implicits.client._
import io.circe.syntax._
import cats.implicits._
import scala.concurrent.Await
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.FailedToSend
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import java.time.Instant
import java.util.Optional

class SignedTransferProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext
) extends AbstractProcessor(damlAppContext, toplContext) {

  implicit val networkPrefix = toplContext.provider.networkPrefix
  implicit val jsonDecoder = co.topl.modifier.transaction.Transaction.jsonDecoder

  val logger = LoggerFactory.getLogger(classOf[SignedTransferProcessor])
  import toplContext.provider._

  private def lift[E, A](a: Either[E, A]) = EitherT[Future, E, A](Future(a))

  private def handlePending(
    signedTransfer:         SignedTransfer,
    signedTransferContract: SignedTransfer.ContractId
  ): stream.Stream[Command] = {
    val result = for {
      transactionAsBytes <-
        lift(
          ByteVector
            .fromBase58(signedTransfer.signedTx)
            .map(_.toArray)
            .toRight(RpcErrorFailure(InvalidParametersError(DecodingFailure("Invalid signed tx from base 58", Nil))))
        )
      _ = logger.debug("transactionAsBytes = {}", transactionAsBytes)
      signedTx <- lift(
        PolyTransferSerializer
          .parseBytes(transactionAsBytes)
          .toEither
          .left
          .map(_ => RpcErrorFailure(InvalidParametersError(DecodingFailure("Invalid bytes for transaction", Nil))))
      )
      _ = logger.debug("from address = {}", signedTx.from.head._1.toString())
      broadcastResult <- ToplRpc.Transaction.BroadcastTx.rpc(ToplRpc.Transaction.BroadcastTx.Params(signedTx))
    } yield broadcastResult
    import scala.concurrent.duration._
    import scala.language.postfixOps
    Await
      .result(result.value, 3 second)
      .fold(
        failure => {
          logger.info("Failed to broadcast transaction to server.")
          logger.debug("Error: {}", failure)
          // FIXME error handling
          stream.Stream.of(
            signedTransferContract
              .exerciseSignedTransfer_Fail("Failed broadcast to server")
          )
        },
        success => {
          logger.info("Successfully broadcasted transaction to network.")
          logger.debug(
            "Server answer: {}",
            success.asJson
          )
          stream.Stream.of(
            signedTransferContract
              .exerciseSignedTransfer_Sent(new Sent(Instant.now(), damlAppContext.appId, Optional.empty()))
          )
        }
      )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command] = processEventAux(SignedTransfer.TEMPLATE_ID, event) {
    val signedTransferContract =
      SignedTransfer.Contract.fromCreatedEvent(event).id
    val signedTransfer =
      SignedTransfer.fromValue(
        event.getArguments()
      )
    if (signedTransfer.sendStatus.isInstanceOf[Pending]) {
      handlePending(signedTransfer, signedTransferContract)
    } else if (signedTransfer.sendStatus.isInstanceOf[FailedToSend]) {
      logger.error("Failed to send contract.")
      stream.Stream.of(
        signedTransferContract
          .exerciseSignedTransfer_Archive()
      )
    } else if (signedTransfer.sendStatus.isInstanceOf[Sent]) {
      logger.info("Successfully sent.")
      stream.Stream.of(
        signedTransferContract
          .exerciseSignedTransfer_Archive()
      )
    } else {
      stream.Stream.of(
        signedTransferContract
          .exerciseSignedTransfer_Archive()
      )
    }
  }

}
