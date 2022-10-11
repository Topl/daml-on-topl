package co.topl.daml.polys.processors

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
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.processEventAux
import co.topl.modifier.transaction.PolyTransfer
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
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
import cats.effect.IO
import scala.util.Try
import io.circe.Json
import co.topl.attestation.Proposition
import co.topl.daml.RpcClientFailureException
import co.topl.daml.CommonOperations

class UnsignedTransferProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  fileName:       String,
  password:       String,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[UnsignedTransfer, UnsignedTransfer.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with CommonOperations {

  implicit val networkPrefix = toplContext.provider.networkPrefix

  val logger = LoggerFactory.getLogger(classOf[UnsignedTransferProcessor])

  def parsePolyTxM(msg2Sign: Array[Byte]) = IO.fromTry(PolyTransferSerializer.parseBytes(msg2Sign))

  def signOperationM(
    unsidgnedTransferRequest:         UnsignedTransfer,
    unsidgnedTransferRequestContract: UnsignedTransfer.ContractId
  ): IO[stream.Stream[Command]] = (for {
    keyfile        <- readFileM(fileName)
    jsonKey        <- IO.fromEither(parse(keyfile))
    address        <- importKeyM(jsonKey, password, keyRing)
    msg2Sign       <- decodeTransactionM(unsidgnedTransferRequest.txToSign)
    rawTx          <- parsePolyTxM(msg2Sign)
    signedTx       <- signTxM(rawTx)
    signedTxString <- encodeTransferM(signedTx)
  } yield {
    val signedTxString = ByteVector(PolyTransferSerializer.toBytes(signedTx)).toBase58
    logger.info("Successfully signed transaction for contract {}.", unsidgnedTransferRequestContract.contractId)
    logger.debug("signedTx = {}", signedTx)
    logger.debug(
      "Encoded transaction: {}",
      signedTxString
    )

    stream.Stream.of(
      unsidgnedTransferRequestContract
        .exerciseUnsignedTransfer_Sign(signedTxString)
    ): stream.Stream[Command]
  }).handleError { failure =>
    logger.info("Failed to sign transaction.")
    logger.debug("Error: {}", failure)

    stream.Stream.of(
      unsidgnedTransferRequestContract
        .exerciseUnsignedTransfer_Archive()
    )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    UnsignedTransfer.TEMPLATE_ID,
    e => UnsignedTransfer.fromValue(e.getArguments()),
    e => UnsignedTransfer.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  )(signOperationM)

}
