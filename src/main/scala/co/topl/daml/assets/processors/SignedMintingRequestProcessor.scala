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
import co.topl.daml.api.model.topl.asset.SignedAssetMinting_Confirm
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
import com.daml.ledger.api.v1.CommandsOuterClass
import com.daml.ledger.api.v1.TransactionFilterOuterClass
import com.daml.ledger.api.v1.ValueOuterClass.Identifier
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import com.daml.ledger.javaapi.data.TransactionFilter
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
import co.topl.daml.CommonOperations
import co.topl.daml.RpcClientFailureException
import cats.effect.IO

class SignedMintingRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  idGenerator:    java.util.function.Supplier[String],
  callback:       java.util.function.BiFunction[SignedAssetMinting, SignedAssetMinting.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with CommonOperations {

  implicit val networkPrefix = toplContext.provider.networkPrefix
  implicit val jsonDecoder = co.topl.modifier.transaction.Transaction.jsonDecoder

  val logger = LoggerFactory.getLogger(classOf[SignedMintingRequestProcessor])

  import toplContext.provider._

  private def handlePendingM(
    signedMintingRequest:         SignedAssetMinting,
    signedMintingRequestContract: SignedAssetMinting.ContractId
  ): IO[stream.Stream[Command]] =
    (for {
      transactionAsBytes <- decodeTransactionM(signedMintingRequest.signedMintTx)
      signedTx           <- deserializeTransactionM(transactionAsBytes)
      eitherBroadcast    <- broadcastTransactionM(signedTx)
      broadcast          <- IO.fromEither(eitherBroadcast.left.map(x => new RpcClientFailureException(x)))
    } yield {
      logger.info("Successfully broadcasted transaction to network.")
      logger.debug(
        "Server answer: {}",
        broadcast.asJson
      )
      stream.Stream.of(
        signedMintingRequestContract
          .exerciseSignedAssetMinting_Sent(
            new Sent(Instant.now(), damlAppContext.appId, Optional.of(broadcast.id.toString()))
          )
      ): stream.Stream[Command]
    }).handleError { failure =>
      logger.info("Failed to broadcast transaction to server.")
      logger.debug("Error: {}", failure)
      stream.Stream.of(
        signedMintingRequestContract
          .exerciseSignedAssetMinting_Fail("Failed broadcast to server")
      )
    }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
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

      IO(
        stream.Stream.of(
          signedMintingRequestContract
            .exerciseSignedAssetMinting_Confirm(
              new SignedAssetMinting_Confirm(
                (signedMintingRequest.sendStatus.asInstanceOf[Sent]).txHash.orElseGet(() => ""),
                1
              )
            )
        )
      )
    } else if (signedMintingRequest.sendStatus.isInstanceOf[Confirmed]) {
      logger.info("Successfully confirmed.")
      IO(
        stream.Stream.of(
          Organization
            .byKey(new types.Tuple2(signedMintingRequest.operator, signedMintingRequest.someOrgId.get()))
            .exerciseOrganization_AddSignedAssetMinting(idGenerator.get(), signedMintingRequestContract)
        )
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
