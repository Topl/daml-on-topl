package co.topl.daml.polys.processors

import cats.data.EitherT
import cats.implicits._
import co.topl.akkahttprpc.InvalidParametersError
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.akkahttprpc.RpcErrorFailure
import co.topl.akkahttprpc.implicits.client._
import co.topl.attestation.Address
import co.topl.attestation.AddressCodec.implicits._
import co.topl.attestation.keyManagement.KeyRing
import co.topl.attestation.keyManagement.KeyfileCurve25519
import co.topl.attestation.keyManagement.KeyfileCurve25519Companion
import co.topl.attestation.keyManagement.PrivateKeyCurve25519
import co.topl.client.Brambl
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.topl.transfer.SignedTransfer
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.FailedToSend
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import co.topl.daml.processEventAux
import co.topl.modifier.transaction.PolyTransfer
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import co.topl.rpc.ToplRpc
import co.topl.rpc.implicits.client._
import co.topl.utils.StringDataTypes
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import io.circe.DecodingFailure
import io.circe.parser.parse
import io.circe.syntax._
import org.slf4j.LoggerFactory
import scodec.bits._

import java.io.File
import java.time.Instant
import java.util.Optional
import java.util.stream
import scala.concurrent.Await
import scala.concurrent.Future
import scala.io.Source
import cats.effect.IO
import co.topl.daml.RpcClientFailureException
import co.topl.attestation.Proposition
import cats.arrow.FunctionK
import io.circe.Decoder
import co.topl.utils.NetworkType
import co.topl.modifier.transaction.Transaction
import co.topl.daml.algebras.PolySpecificOperationsAlgebra

class SignedTransferProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[SignedTransfer, SignedTransfer.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with PolySpecificOperationsAlgebra {

  val logger = LoggerFactory.getLogger(classOf[SignedTransferProcessor])
  import toplContext.provider._

  private def handlePendingM(
    signedTransfer:         SignedTransfer,
    signedTransferContract: SignedTransfer.ContractId
  ): IO[stream.Stream[Command]] = (for {
    transactionAsBytes <- decodeTransactionM(signedTransfer.txToSign)
    signedTx           <- parseTxM(transactionAsBytes)
    success            <- broadcastTransactionM(signedTx)
  } yield {
    logger.info("Successfully broadcasted transaction to network.")
    logger.debug(
      "Server answer: {}",
      success.asJson
    )
    stream.Stream.of(
      signedTransferContract
        .exerciseSignedTransfer_Sent(new Sent(Instant.now(), damlAppContext.appId, Optional.empty()))
    ): stream.Stream[Command]
  }).handleError { failure =>
    logger.info("Failed to broadcast transaction to server.")
    logger.debug("Error: {}", failure)
    stream.Stream.of(
      signedTransferContract
        .exerciseSignedTransfer_Fail("Failed broadcast to server")
    )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    SignedTransfer.TEMPLATE_ID,
    e => SignedTransfer.fromValue(e.getArguments()),
    e => SignedTransfer.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (signedTransfer, signedTransferId) =>
    if (signedTransfer.sendStatus.isInstanceOf[Pending]) {
      handlePendingM(signedTransfer, signedTransferId)
    } else if (signedTransfer.sendStatus.isInstanceOf[FailedToSend]) {
      logger.error("Failed to send contract.")
      IO.apply(
        stream.Stream.of(
          signedTransferId
            .exerciseSignedTransfer_Archive()
        )
      )

    } else if (signedTransfer.sendStatus.isInstanceOf[Sent]) {
      logger.info("Successfully sent.")
      IO(
        stream.Stream.of(
          signedTransferId
            .exerciseSignedTransfer_Archive()
        )
      )
    } else {
      IO(
        stream.Stream.of(
          signedTransferId
            .exerciseSignedTransfer_Archive()
        )
      )
    }
  }

}
