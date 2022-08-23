package co.topl.daml.processors

import java.util.stream
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.attestation.keyManagement.{KeyRing, KeyfileCurve25519, KeyfileCurve25519Companion, PrivateKeyCurve25519}
import co.topl.client.Brambl
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.attestation.Address
import io.circe.parser.parse
import scala.io.Source
import java.io.File
import cats.data.EitherT
import co.topl.utils.StringDataTypes
import co.topl.attestation.AddressCodec.implicits._
import scodec.bits._
import co.topl.akkahttprpc.RpcErrorFailure
import co.topl.akkahttprpc.InvalidParametersError
import io.circe.DecodingFailure
import scala.concurrent.Future
import co.topl.modifier.transaction.PolyTransfer
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import org.slf4j.LoggerFactory

class UnsignedTransferProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  fileName:       String,
  password:       String
) extends AbstractProcessor(damlAppContext, toplContext) {

  implicit val networkPrefix = toplContext.provider.networkPrefix

  val logger = LoggerFactory.getLogger(classOf[UnsignedTransferProcessor])

  val keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519] =
    KeyRing.empty[PrivateKeyCurve25519, KeyfileCurve25519]()(
      toplContext.provider.networkPrefix,
      PrivateKeyCurve25519.secretGenerator,
      KeyfileCurve25519Companion
    )

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command] = processEventAux(UnsignedTransfer.TEMPLATE_ID, event) {
    val unsidgnedTransferRequestContract =
      UnsignedTransfer.Contract.fromCreatedEvent(event).id
    val unsidgnedTransferRequest =
      UnsignedTransfer.fromValue(
        event.getArguments()
      )
    val keyfile = Source.fromFile(new File(fileName)).getLines().mkString("").mkString
    (for {
      jsonKey <- parse(keyfile)
      address <- Brambl.importCurve25519JsonToKeyRing(jsonKey, password, keyRing)
      msg2Sign <- ByteVector
        .fromBase58(unsidgnedTransferRequest.txToSign)
        .map(_.toArray)
        .toRight(RpcErrorFailure(InvalidParametersError(DecodingFailure("Invalid contract", Nil))))
      rawTx <- PolyTransferSerializer.parseBytes(msg2Sign).toEither
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
            .exerciseUnsignedTransfer_Archive()
        )
      },
      signedTx => {
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
        )
      }
    )
  }

}
