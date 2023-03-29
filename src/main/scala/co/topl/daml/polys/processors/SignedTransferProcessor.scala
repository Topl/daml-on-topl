package co.topl.daml.polys.processors

import java.io.File
import java.time.Instant
import java.util.Optional
import java.util.stream

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

import cats.arrow.FunctionK
import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import co.topl.akkahttprpc.InvalidParametersError
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.akkahttprpc.RpcErrorFailure
import co.topl.akkahttprpc.implicits.client._
import co.topl.attestation.Address
import co.topl.attestation.AddressCodec.implicits._
import co.topl.attestation.Proposition
import co.topl.attestation.keyManagement.KeyRing
import co.topl.attestation.keyManagement.KeyfileCurve25519
import co.topl.attestation.keyManagement.KeyfileCurve25519Companion
import co.topl.attestation.keyManagement.PrivateKeyCurve25519
import co.topl.client.Brambl
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.RpcClientFailureException
import co.topl.daml.ToplContext
import co.topl.daml.algebras.PolySpecificOperationsAlgebra
import co.topl.daml.api.model.topl.transfer.SignedTransfer
import co.topl.daml.api.model.topl.transfer.SignedTransfer_Confirm
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.FailedToSend
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import co.topl.modifier.ModifierId
import co.topl.modifier.transaction.PolyTransfer
import co.topl.modifier.transaction.Transaction
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import co.topl.rpc.ToplRpc
import co.topl.rpc.implicits.client._
import co.topl.utils.NetworkType
import co.topl.utils.StringDataTypes
import com.daml.ledger.javaapi.data.CreatedEvent
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.parser.parse
import io.circe.syntax._
import org.slf4j.LoggerFactory
import scodec.bits._
import com.daml.ledger.javaapi.data.codegen.HasCommands

/**
 * This processor processes the broadcasting of signed transfer requests.
 *
 * @param damlAppContext the context of the DAML application
 * @param toplContext the context for Topl blockain, in particular the provider
 * @param timeoutMillis the timeout before processing fails
 * @param confirmationDepth the depth at which a transaction is confirmed
 * @param callback a function that performs operations before the processing is done. Its result is returned by the processor when there are no errors.
 * @param onError a function executed when there is an error sending the commands to the DAML server. Its result is returned by the processor when there are errors in the DAML.
 */
class SignedTransferProcessor(
  damlAppContext:    DamlAppContext,
  toplContext:       ToplContext,
  timeoutMillis:     Int,
  confirmationDepth: Int,
  callback:          java.util.function.BiFunction[SignedTransfer, SignedTransfer.ContractId, Boolean],
  onError:           java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with PolySpecificOperationsAlgebra {

  def this(
    damlAppContext: DamlAppContext,
    toplContext:    ToplContext
  ) =
    this(damlAppContext, toplContext, 3000, 1, (x, y) => true, x => true)

  import toplContext.provider._

  def handlePendingM(
    signedTransfer:         SignedTransfer,
    signedTransferContract: SignedTransfer.ContractId
  ): IO[stream.Stream[HasCommands]] = (for {
    transactionAsBytes <- decodeTransactionM(signedTransfer.signedTx)
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
        .exerciseSignedTransfer_Sent(new Sent(Instant.now(), damlAppContext.appId, success.id.toString()))
    ): stream.Stream[HasCommands]
  }).timeout(timeoutMillis.millis).handleError { failure =>
    logger.info("Failed to broadcast transaction to server.")
    logger.debug("Error: {}", failure)
    stream.Stream.of(
      signedTransferContract
        .exerciseSignedTransfer_Fail("Failed broadcast to server")
    )
  }

  private def handleSentM(
    signedTransfer:         SignedTransfer,
    signedTransferContract: SignedTransfer.ContractId,
    sentStatus:             Sent
  ): IO[stream.Stream[HasCommands]] = (for {
    confirmationStatusMap <- getTransactionConfirmationStatusM(sentStatus.txId)
  } yield {
    val confirmationStatus = confirmationStatusMap(ModifierId(sentStatus.txId))
    if (confirmationStatus.depthFromHead >= confirmationDepth) {
      IO(
        stream.Stream.of(
          signedTransferContract
            .exerciseSignedTransfer_Confirm(
              new SignedTransfer_Confirm(
                (signedTransfer.sendStatus.asInstanceOf[Sent]).txId,
                confirmationStatus.depthFromHead
              )
            )
        ): stream.Stream[HasCommands]
      )
    } else {
      import scala.concurrent.duration._
      IO.sleep(10.second)
        .flatMap { _ =>
          logger.info("Transaction id: {}.", sentStatus.txId)
          logger.info("Transaction depth: {}. Retrying.", confirmationStatus.depthFromHead)
          handleSentM(signedTransfer, signedTransferContract, sentStatus)
        }
    }
  }).flatten

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[HasCommands])] = processEventAux(
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
      handleSentM(signedTransfer, signedTransferId, signedTransfer.sendStatus.asInstanceOf[Sent])
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
