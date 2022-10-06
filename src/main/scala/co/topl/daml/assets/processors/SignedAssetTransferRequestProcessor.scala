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
import co.topl.daml.processEventAux
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

// Possible designs:
// - One-off processor
// - Reusable processor

class SignedAssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  idGenerator:    java.util.function.Supplier[String],
  callback:       java.util.function.BiFunction[SignedAssetTransfer, SignedAssetTransfer.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  implicit val networkPrefix = toplContext.provider.networkPrefix
  implicit val jsonDecoder = co.topl.modifier.transaction.Transaction.jsonDecoder

  val logger = LoggerFactory.getLogger(classOf[SignedAssetTransferRequestProcessor])
  import toplContext.provider._

  private def lift[E, A](a: Either[E, A]) = EitherT[Future, E, A](Future(a))

  private def handlePending(
    signedTransferRequest:         SignedAssetTransfer,
    signedTransferRequestContract: SignedAssetTransfer.ContractId
  ): stream.Stream[Command] = {
    val result = for {
      transactionAsBytes <-
        lift(
          ByteVector
            .fromBase58(signedTransferRequest.signedTx)
            .map(_.toArray)
            .toRight(RpcErrorFailure(InvalidParametersError(DecodingFailure("Invalid signed tx from base 58", Nil))))
        )
      _ = logger.debug("transactionAsBytes = {}", transactionAsBytes)
      signedTx <- lift(
        AssetTransferSerializer
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
      .result(result.value, timeoutMillis millis)
      .fold(
        failure => {
          logger.info("Failed to broadcast transaction to server.")
          logger.debug("Error: {}", failure)
          // FIXME: error handling
          stream.Stream.of(
            signedTransferRequestContract
              .exerciseSignedAssetTransfer_Fail("Failed broadcast to server")
          )
        },
        success => {
          logger.info("Successfully broadcasted transaction to network.")
          logger.debug(
            "Server answer: {}",
            success.asJson
          )
          stream.Stream.of(
            signedTransferRequestContract
              .exerciseSignedAssetTransfer_Sent(new Sent(Instant.now(), damlAppContext.appId, Optional.empty()))
          )
        }
      )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) = processEventAux(SignedAssetTransfer.TEMPLATE_ID, event) {
    val signedTransferRequestContract =
      SignedAssetTransfer.Contract.fromCreatedEvent(event).id
    val signedTransferRequest =
      SignedAssetTransfer.fromValue(
        event.getArguments()
      )
    val mustContinue = callback.apply(signedTransferRequest, signedTransferRequestContract)
    if (mustContinue) {
      if (signedTransferRequest.sendStatus.isInstanceOf[Pending]) {
        (mustContinue, handlePending(signedTransferRequest, signedTransferRequestContract))
      } else if (signedTransferRequest.sendStatus.isInstanceOf[FailedToSend]) {
        logger.error("Failed to send contract.")
        (
          mustContinue,
          stream.Stream.of(
            signedTransferRequestContract
              .exerciseSignedAssetTransfer_Archive()
          )
        )
      } else if (signedTransferRequest.sendStatus.isInstanceOf[Sent]) {
        logger.info("Successfully sent.")
        (
          mustContinue,
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
      } else {
        (
          mustContinue,
          stream.Stream.of(
            signedTransferRequestContract
              .exerciseSignedAssetTransfer_Archive()
          )
        )
      }
    } else {
      logger.info("Successfully confirmed.")
      (
        mustContinue,
        stream.Stream.of(
          Organization
            .byKey(new types.Tuple2(signedTransferRequest.operator, signedTransferRequest.someOrgId.get()))
            .exerciseOrganization_AddSignedAssetTransfer(idGenerator.get(), signedTransferRequestContract)
        )
      )
    }
  }

}
