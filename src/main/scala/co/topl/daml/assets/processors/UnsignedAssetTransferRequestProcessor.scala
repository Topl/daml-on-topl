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

import co.topl.daml.processEventAux
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

class UnsignedAssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  fileName:       String,
  password:       String,
  callback: java.util.function.BiFunction[
    UnsignedAssetTransferRequest,
    UnsignedAssetTransferRequest.ContractId,
    Boolean
  ]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  implicit val networkPrefix = toplContext.provider.networkPrefix

  val logger = LoggerFactory.getLogger(classOf[UnsignedAssetTransferRequestProcessor])

  val keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519] =
    KeyRing.empty[PrivateKeyCurve25519, KeyfileCurve25519]()(
      toplContext.provider.networkPrefix,
      PrivateKeyCurve25519.secretGenerator,
      KeyfileCurve25519Companion
    )

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) = processEventAux(
    UnsignedAssetTransferRequest.TEMPLATE_ID,
    e => UnsignedAssetTransferRequest.fromValue(e.getArguments()),
    e => UnsignedAssetTransferRequest.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (unsidgnedTransferRequest, unsidgnedTransferRequestContract) =>
    val keyfile = Source.fromFile(new File(fileName)).getLines().mkString("").mkString
    (for {
      jsonKey <- parse(keyfile)
      address <- Brambl.importCurve25519JsonToKeyRing(jsonKey, password, keyRing)
      msg2Sign <- ByteVector
        .fromBase58(unsidgnedTransferRequest.txToSign)
        .map(_.toArray)
        .toRight(RpcErrorFailure(InvalidParametersError(DecodingFailure("Invalid contract", Nil))))
      rawTx <- AssetTransferSerializer.parseBytes(msg2Sign).toEither
      signedTx <- Right {
        val signFunc = (addr: Address) => keyRing.generateAttestation(addr)(rawTx.messageToSign)
        logger.debug("listOfAddresses = {}", keyRing.addresses)
        val signatures = keyRing.addresses.map(signFunc).reduce(_ ++ _)
        rawTx.copy(attestation = signatures)
      }
    } yield signedTx).fold(
      failure => {
        logger.info("Failed to sign transaction.")
        logger.debug("Error: {}", failure)

        stream.Stream.of(
          unsidgnedTransferRequestContract
            .exerciseUnsignedAssetTransfer_Archive()
        )
      },
      signedTx => {
        val signedTxString = ByteVector(AssetTransferSerializer.toBytes(signedTx)).toBase58
        logger.info("Successfully signed transaction for contract {}.", unsidgnedTransferRequestContract.contractId)
        logger.debug("signedTx = {}", signedTx)
        logger.debug(
          "Encoded transaction: {}",
          signedTxString
        )

        stream.Stream.of(
          unsidgnedTransferRequestContract
            .exerciseUnsignedAssetTransfer_Sign(signedTxString)
        )
      }
    )

  }

}
