package co.topl.daml.assets.processors

import java.util.stream

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.concurrent.Await
import scala.concurrent.duration._

import cats.data.EitherT
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.syntax.traverse._
import co.topl.akkahttprpc.implicits.client.rpcToClient
import co.topl.attestation.AddressCodec.implicits._
import co.topl.attestation.PublicKeyPropositionEd25519._
import co.topl.attestation._
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.RpcClientFailureException
import co.topl.daml.ToplContext
import co.topl.daml.algebras.AssetOperationsAlgebra
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import co.topl.daml.utf8StringToLatin1ByteArray
import co.topl.modifier.box.AssetCode
import co.topl.modifier.box.AssetValue
import co.topl.modifier.box.SecurityRoot
import co.topl.modifier.box.SimpleValue
import co.topl.modifier.box.TokenValueHolder
import co.topl.modifier.transaction.AssetTransfer
import co.topl.modifier.transaction.builder.BoxSelectionAlgorithms
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import co.topl.rpc.ToplRpc
import co.topl.rpc.implicits.client._
import co.topl.utils.IdiomaticScalaTransition.implicits.toValidatedOps
import co.topl.utils.Int128
import co.topl.utils.StringDataTypes
import co.topl.utils.StringDataTypes.Base58Data
import co.topl.utils.StringDataTypes.Latin1Data
import com.daml.ledger.javaapi.data.CreatedEvent
import org.slf4j.LoggerFactory
import scodec.bits.ByteVector

import ToplRpc.Transaction.RawAssetTransfer
import com.daml.ledger.javaapi.data.codegen.HasCommands

/**
 * This processor processes the transfer requests.
 *
 * @param damlAppContext the context of the DAML application
 * @param toplContext the context for Topl blockain, in particular the provider
 * @param timeoutMillis the timeout before processing fails
 * @param callback a function that performs operations before the processing is done. Its result is returned by the processor when there are no errors.
 * @param onError a function executed when there is an error sending the commands to the DAML server. Its result is returned by the processor when there are errors in the DAML.
 */
class AssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[AssetTransferRequest, AssetTransferRequest.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with AssetOperationsAlgebra {

  def this(
    damlAppContext: DamlAppContext,
    toplContext:    ToplContext
  ) =
    this(damlAppContext, toplContext, 3000, (x, y) => true, x => true)

  implicit val ev = assetTransferRequestEv

  import toplContext.provider._

  def processTransferRequestM(
    assetTransferRequest:         AssetTransferRequest,
    assetTransferRequestContract: AssetTransferRequest.ContractId
  ): IO[stream.Stream[HasCommands]] = (for {
    address       <- decodeAddressesM(assetTransferRequest.from.asScala.toList)
    changeAddress <- decodeAddressM(assetTransferRequest.changeAddress)
    params        <- getParamsM(address)
    balance       <- getBalanceM(params)
    value         <- computeValueM(assetTransferRequest.fee, balance)
    tailList = assetTransferRequest.to.asScala.toList.map(t => (createToParamM(assetTransferRequest) _)(t._1, t._2))
    listOfToAddresses <- (IO((changeAddress, value)) :: tailList).sequence
    assetTransfer <- createAssetTransferM(
      assetTransferRequest.fee,
      Some(assetTransferRequest.boxNonce),
      address,
      balance,
      listOfToAddresses
      )
      encodedTx <- encodeTransferM(assetTransfer)
      messageToSign <- IO(
        ByteVector(
          assetTransfer.messageToSign
          ).toBase58
          )
        } yield {
          logger.info("Successfully generated raw transaction for contract {}.", assetTransferRequestContract.contractId)
          import io.circe.syntax._
          logger.debug("The returned json: {}", assetTransfer.asJson)
    logger.info(
      "Encoded transaction: {}",
      encodedTx
    )
    logger.info(
      "Message to sign: {}",
      messageToSign
    )

    stream.Stream.of(
      assetTransferRequestContract.exerciseAssetTransferRequest_Accept(
        encodedTx,
        messageToSign,
        assetTransfer.newBoxes.toList.reverse.head.nonce
      )
    ): stream.Stream[HasCommands]
  }).handleError { failure =>
    logger.info("Failed to obtain raw transaction from server.")
    logger.info("Error: {}", failure)

    stream.Stream.of(
      assetTransferRequestContract
        .exerciseAssetTransferRequest_Reject()
    )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[HasCommands])] =
    processEventAux(
      AssetTransferRequest.TEMPLATE_ID,
      e => AssetTransferRequest.fromValue(e.getArguments()),
      e => AssetTransferRequest.Contract.fromCreatedEvent(e).id,
      callback.apply,
      event
    )(processTransferRequestM).timeout(timeoutMillis.millis)

}
