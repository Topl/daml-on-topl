package co.topl.daml.assets.processors

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
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.asset.SignedAssetMinting
import co.topl.daml.api.model.topl.asset.SignedAssetTransfer
import co.topl.daml.api.model.topl.asset.SignedAssetTransfer_Confirm
import co.topl.daml.api.model.topl.organization.Organization
import co.topl.daml.api.model.topl.transfer.SignedTransfer
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.Confirmed
import co.topl.daml.api.model.topl.utils.sendstatus.FailedToSend
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
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
import co.topl.modifier.transaction.AssetTransfer
import co.topl.attestation.Proposition
import co.topl.daml.algebras.AssetOperationsAlgebra

/**
 * This processor processes the broadcasting of signed transfer requests.
 *
 * @param damlAppContext the context of the DAML application
 * @param toplContext the context for Topl blockain, in particular the provider
 * @param timeoutMillis the timeout before processing fails
 * @param idGenerator generates the id for the created transaction.
 * @param callback a function that performs operations before the processing is done. Its result is returned by the processor when there are no errors.
 * @param onError a function executed when there is an error sending the commands to the DAML server. Its result is returned by the processor when there are errors in the DAML.
 */
class SignedAssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  idGenerator:    java.util.function.Supplier[String],
  callback:       java.util.function.BiFunction[SignedAssetTransfer, SignedAssetTransfer.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with AssetOperationsAlgebra {

  def this(
    damlAppContext: DamlAppContext,
    toplContext:    ToplContext,
    idGenerator:    java.util.function.Supplier[String]
  ) =
    this(damlAppContext, toplContext, 3000, idGenerator, (x, y) => true, x => true)

  import toplContext.provider._

  def handlePendingM(
    signedTransferRequest:         SignedAssetTransfer,
    signedTransferRequestContract: SignedAssetTransfer.ContractId
  ): IO[stream.Stream[Command]] =
    (for {
      transactionAsBytes <- decodeTransactionM(signedTransferRequest.signedTx)
      signedTx           <- deserializeTransactionM(transactionAsBytes)
      broadcast          <- broadcastTransactionM(signedTx)
    } yield {
      logger.info("Successfully broadcasted transaction to network.")
      logger.debug(
        "Server answer: {}",
        broadcast.asJson
      )
      stream.Stream.of(
        signedTransferRequestContract
          .exerciseSignedAssetTransfer_Sent(new Sent(Instant.now(), damlAppContext.appId, Optional.empty()))
      ): stream.Stream[Command]
    }).handleError { failure =>
      logger.info("Failed to broadcast transaction to server.")
      logger.debug("Error: {}", failure)
      stream.Stream.of(
        signedTransferRequestContract
          .exerciseSignedAssetTransfer_Fail("Failed broadcast to server")
      )
    }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    SignedAssetTransfer.TEMPLATE_ID,
    e => SignedAssetTransfer.fromValue(e.getArguments()),
    e => SignedAssetTransfer.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (signedTransferRequest, signedTransferRequestContract) =>
    if (signedTransferRequest.sendStatus.isInstanceOf[Pending]) {
      handlePendingM(signedTransferRequest, signedTransferRequestContract)
    } else if (signedTransferRequest.sendStatus.isInstanceOf[FailedToSend]) {
      logger.error("Failed to send contract.")

      IO(
        stream.Stream.of(
          signedTransferRequestContract
            .exerciseSignedAssetTransfer_Archive()
        )
      )
    } else if (signedTransferRequest.sendStatus.isInstanceOf[Sent]) {
      logger.info("Successfully sent.")

      IO(
        stream.Stream.of(
          signedTransferRequestContract
            .exerciseSignedAssetTransfer_Confirm(
              new SignedAssetTransfer_Confirm(
                (signedTransferRequest.sendStatus.asInstanceOf[Sent]).txHash.orElseGet(() => ""),
                1
              )
            )
        )
      )
    } else if (signedTransferRequest.sendStatus.isInstanceOf[Confirmed]) {
      logger.info("Successfully confirmed.")

      IO(
        stream.Stream.of(
          Organization
            .byKey(new types.Tuple2(signedTransferRequest.operator, signedTransferRequest.someOrgId.get()))
            .exerciseOrganization_AddSignedAssetTransfer(idGenerator.get(), signedTransferRequestContract)
        )
      )
    } else {

      IO(
        stream.Stream.of(
          signedTransferRequestContract
            .exerciseSignedAssetTransfer_Archive()
        )
      )
    }

  }

}
