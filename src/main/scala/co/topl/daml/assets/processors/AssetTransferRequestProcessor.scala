package co.topl.daml.assets.processors

import cats.data.EitherT
import cats.data.NonEmptyChain
import co.topl.akkahttprpc.implicits.client.rpcToClient
import co.topl.attestation.AddressCodec.implicits._
import co.topl.attestation.PublicKeyPropositionCurve25519._
import co.topl.attestation._
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.utf8StringToLatin1ByteArray
import co.topl.modifier.box.AssetCode
import co.topl.modifier.box.AssetValue
import co.topl.modifier.box.SecurityRoot
import co.topl.modifier.transaction.AssetTransfer
import co.topl.modifier.transaction.builder.BoxSelectionAlgorithms
import co.topl.rpc.ToplRpc
import co.topl.rpc.implicits.client._
import co.topl.utils.IdiomaticScalaTransition.implicits.toValidatedOps
import co.topl.utils.Int128
import co.topl.utils.StringDataTypes
import co.topl.utils.StringDataTypes.Base58Data
import co.topl.utils.StringDataTypes.Latin1Data
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import org.slf4j.LoggerFactory
import scodec.bits.ByteVector

import java.util.stream
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.concurrent.Await

import ToplRpc.Transaction.RawAssetTransfer
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import co.topl.modifier.box.SimpleValue
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import cats.effect.IO
import cats.syntax.traverse._

import co.topl.daml.RpcClientFailureException
import co.topl.modifier.box.TokenValueHolder
import co.topl.daml.algebras.AssetOperationsAlgebra

class AssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[AssetTransferRequest, AssetTransferRequest.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with AssetOperationsAlgebra {

  val logger = LoggerFactory.getLogger(classOf[AssetTransferRequestProcessor])

  implicit val ev = assetTransferRequestEv

  import toplContext.provider._

  def processTransferRequestM(
    assetTransferRequest:         AssetTransferRequest,
    assetTransferRequestContract: AssetTransferRequest.ContractId
  ): IO[stream.Stream[Command]] = (for {
    address   <- decodeAddressesM(assetTransferRequest.from.asScala.toList)
    params    <- getParamsM(address)
    balance   <- getBalanceM(params)
    toAddress <- decodeAddressM(assetTransferRequest.to.get(0)._1)
    value     <- computeValueM(assetTransferRequest.fee, balance)
    tailList = assetTransferRequest.to.asScala.toList.map(t => (createToParamM(assetTransferRequest) _)(t._1, t._2))
    listOfToAddresses <- (IO((toAddress, value)) :: tailList).sequence
    assetTransfer <- createAssetTransferM(
      assetTransferRequest.fee,
      Some(assetTransferRequest.boxNonce),
      address,
      balance,
      listOfToAddresses
    )
    encodedTx <- encodeTransferM(assetTransfer)
  } yield {
    logger.info("Successfully generated raw transaction for contract {}.", assetTransferRequestContract.contractId)
    import io.circe.syntax._
    logger.debug("The returned json: {}", assetTransfer.asJson)
    logger.debug(
      "Encoded transaction: {}",
      encodedTx
    )

    stream.Stream.of(
      assetTransferRequestContract.exerciseAssetTransferRequest_Accept(
        encodedTx,
        assetTransfer.newBoxes.toList.reverse.head.nonce
      )
    ): stream.Stream[Command]
  }).handleError { failure =>
    logger.info("Failed to obtain raw transaction from server.")
    logger.debug("Error: {}", failure)

    stream.Stream.of(
      assetTransferRequestContract
        .exerciseAssetTransferRequest_Reject()
    )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    AssetTransferRequest.TEMPLATE_ID,
    e => AssetTransferRequest.fromValue(e.getArguments()),
    e => AssetTransferRequest.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  )(processTransferRequestM)

}
