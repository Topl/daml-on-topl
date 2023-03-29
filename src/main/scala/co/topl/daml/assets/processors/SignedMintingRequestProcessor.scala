package co.topl.daml.assets.processors

import java.io.File
import java.time.Instant
import java.util.Optional
import java.util.stream

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source

import cats.data.EitherT
import cats.effect.IO
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
import co.topl.daml.RpcClientFailureException
import co.topl.daml.ToplContext
import co.topl.daml.algebras.AssetOperationsAlgebra
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.asset.SignedAssetMinting
import co.topl.daml.api.model.topl.asset.SignedAssetMinting_Confirm
import co.topl.daml.api.model.topl.organization.Organization
import co.topl.daml.api.model.topl.transfer.SignedTransfer
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.Confirmed
import co.topl.daml.api.model.topl.utils.sendstatus.FailedToSend
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.api.model.topl.utils.sendstatus.Sent
import co.topl.modifier.ModifierId
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import co.topl.rpc.ToplRpc
import co.topl.rpc.implicits.client._
import co.topl.utils.StringDataTypes
import com.daml.ledger.api.v1.CommandsOuterClass
import com.daml.ledger.api.v1.TransactionFilterOuterClass
import com.daml.ledger.api.v1.ValueOuterClass.Identifier
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.TransactionFilter
import io.circe.DecodingFailure
import io.circe.parser.parse
import io.circe.syntax._
import org.slf4j.LoggerFactory
import scodec.bits._
import com.daml.ledger.javaapi.data.codegen.HasCommands

/**
 * This processor processes the broadcasting of signed minting requests.
 *
 * @param damlAppContext the context of the DAML application
 * @param toplContext the context for Topl blockain, in particular the provider
 * @param timeoutMillis the timeout before processing fails
 * @param confirmationDepth the depth at which a transaction is confirmed
 * @param idGenerator generates the id for the created transaction.
 * @param callback a function that performs operations before the processing is done. Its result is returned by the processor when there are no errors.
 * @param onError a function executed when there is an error sending the commands to the DAML server. Its result is returned by the processor when there are errors in the DAML.
 */
class SignedMintingRequestProcessor(
  damlAppContext:    DamlAppContext,
  toplContext:       ToplContext,
  timeoutMillis:     Int,
  confirmationDepth: Int,
  idGenerator:       java.util.function.Supplier[String],
  callback:          java.util.function.BiFunction[SignedAssetMinting, SignedAssetMinting.ContractId, Boolean],
  onError:           java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with AssetOperationsAlgebra {

  def this(
    damlAppContext: DamlAppContext,
    toplContext:    ToplContext,
    idGenerator:    java.util.function.Supplier[String]
  ) =
    this(damlAppContext, toplContext, 3000, 1, idGenerator, (x, y) => true, x => true)

  import toplContext.provider._

  private def handlePendingM(
    signedMintingRequest:         SignedAssetMinting,
    signedMintingRequestContract: SignedAssetMinting.ContractId
  ): IO[stream.Stream[HasCommands]] =
    (for {
      transactionAsBytes <- decodeTransactionM(signedMintingRequest.signedMintTx)
      signedTx           <- deserializeTransactionM(transactionAsBytes)
      broadcast          <- broadcastTransactionM(signedTx)
    } yield {
      logger.info("Successfully broadcasted transaction to network.")
      logger.debug(
        "Server answer: {}",
        broadcast.asJson
      )
      stream.Stream.of(
        signedMintingRequestContract
          .exerciseSignedAssetMinting_Sent(
            new Sent(Instant.now(), damlAppContext.appId, broadcast.id.toString())
          )
      ): stream.Stream[HasCommands]
    }).timeout(timeoutMillis.millis).handleError { failure =>
      logger.info("Failed to broadcast transaction to server.")
      logger.debug("Error: {}", failure)
      stream.Stream.of(
        signedMintingRequestContract
          .exerciseSignedAssetMinting_Fail("Failed broadcast to server")
      )
    }

  private def handleSentM(
    signedMintingRequest:         SignedAssetMinting,
    signedMintingRequestContract: SignedAssetMinting.ContractId,
    sentStatus:                   Sent
  ): IO[stream.Stream[HasCommands]] = (for {
    confirmationStatusMap <- getTransactionConfirmationStatusM(sentStatus.txId)
  } yield {
    val confirmationStatus = confirmationStatusMap(ModifierId(sentStatus.txId))
    if (confirmationStatus.depthFromHead >= confirmationDepth) {
      IO(
        stream.Stream.of(
          signedMintingRequestContract
            .exerciseSignedAssetMinting_Confirm(
              new SignedAssetMinting_Confirm(
                (signedMintingRequest.sendStatus.asInstanceOf[Sent]).txId,
                confirmationDepth
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
          handleSentM(signedMintingRequest, signedMintingRequestContract, sentStatus)
        }
    }
  }).flatten

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[HasCommands])] = processEventAux(
    SignedAssetMinting.TEMPLATE_ID,
    e => SignedAssetMinting.fromValue(e.getArguments()),
    e => SignedAssetMinting.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (signedMintingRequest, signedMintingRequestContract) =>
    if (signedMintingRequest.sendStatus.isInstanceOf[Pending]) {
      handlePendingM(signedMintingRequest, signedMintingRequestContract)
    } else if (signedMintingRequest.sendStatus.isInstanceOf[FailedToSend]) {
      logger.error("Failed to send contract.")

      IO(
        stream.Stream.of(
          signedMintingRequestContract
            .exerciseSignedAssetMinting_Archive()
        )
      )
    } else if (signedMintingRequest.sendStatus.isInstanceOf[Sent]) {
      logger.info("Successfully sent.")

      handleSentM(
        signedMintingRequest,
        signedMintingRequestContract,
        signedMintingRequest.sendStatus.asInstanceOf[Sent]
      )
    } else if (signedMintingRequest.sendStatus.isInstanceOf[Confirmed]) {
      logger.info("Successfully confirmed.")
      IO(
        stream.Stream.empty()
      )
    } else {
      IO(
        stream.Stream.of(
          signedMintingRequestContract
            .exerciseSignedAssetMinting_Archive()
        )
      )
    }
  }

}
