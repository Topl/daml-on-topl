package co.topl.daml.assets.processors

import cats.data.EitherT
import co.topl.akkahttprpc.InvalidParametersError
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.akkahttprpc.RpcErrorFailure
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
import co.topl.daml.api.model.topl.asset.UnsignedAssetMinting
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer

import co.topl.utils.StringDataTypes
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import io.circe.DecodingFailure
import io.circe.parser.parse
import org.slf4j.LoggerFactory
import scodec.bits._

import java.io.File
import java.util.stream
import scala.concurrent.Future
import scala.io.Source
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import co.topl.daml.api.model.topl.asset.UnsignedAssetTransferRequest
import cats.effect.IO
import co.topl.attestation.Proposition
import co.topl.daml.algebras.AssetOperationsAlgebra

/**
 * This processor processes the signing of transfer requests.
 *
 * @param damlAppContext the context of the DAML application
 * @param toplContext the context for Topl blockain, in particular the provider
 * @param filename the filename where the keys are stored
 * @param password the password of the keyfile
 * @param callback a function that performs operations before the processing is done. Its result is returned by the processor when there are no errors.
 * @param onError a function executed when there is an error sending the commands to the DAML server. Its result is returned by the processor when there are errors in the DAML.
 */
class UnsignedAssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  fileName:       String,
  password:       String,
  callback: java.util.function.BiFunction[
    UnsignedAssetTransferRequest,
    UnsignedAssetTransferRequest.ContractId,
    Boolean
  ],
  onError: java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with AssetOperationsAlgebra {

  def this(
    damlAppContext: DamlAppContext,
    toplContext:    ToplContext,
    fileName:       String,
    password:       String
  ) =
    this(damlAppContext, toplContext, fileName, password, (x, y) => true, x => true)

  implicit val networkPrefix = toplContext.provider.networkPrefix

  def signOperationM(
    unsidgnedTransferRequest:         UnsignedAssetTransferRequest,
    unsidgnedTransferRequestContract: UnsignedAssetTransferRequest.ContractId
  ): IO[stream.Stream[Command]] = (for {
    keyfile        <- readFileM(fileName)
    jsonKey        <- IO.fromEither(parse(keyfile))
    address        <- importKeyM(jsonKey, password, keyRing)
    msg2Sign       <- decodeTransactionM(unsidgnedTransferRequest.txToSign)
    rawTx          <- deserializeTransactionM(msg2Sign)
    signedTx       <- signTxM(rawTx)
    signedTxString <- encodeTransferM(signedTx)
  } yield {
    logger.info("Successfully signed transaction for contract {}.", unsidgnedTransferRequestContract.contractId)
    logger.debug("signedTx = {}", signedTx)
    logger.info(
      "Encoded transaction: {}",
      signedTxString
    )

    stream.Stream.of(
      unsidgnedTransferRequestContract
        .exerciseUnsignedAssetTransfer_Sign(signedTxString)
    ): stream.Stream[Command]
  }).handleError { f =>
    logger.info("Failed to sign transaction.")
    logger.debug("Error: {}", f)

    stream.Stream.of(
      unsidgnedTransferRequestContract
        .exerciseUnsignedAssetTransfer_Archive()
    )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    UnsignedAssetTransferRequest.TEMPLATE_ID,
    e => UnsignedAssetTransferRequest.fromValue(e.getArguments()),
    e => UnsignedAssetTransferRequest.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  )(signOperationM)

}
