package co.topl.daml.assets.processors

import cats.data.EitherT
import cats.data.NonEmptyChain
import co.topl.akkahttprpc.implicits.client.rpcToClient
import co.topl.attestation.Address
import co.topl.attestation.AddressCodec.implicits._
import co.topl.attestation.Proposition
import co.topl.attestation.PublicKeyPropositionCurve25519
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import co.topl.daml.processEventAux
import co.topl.daml.utf8StringToLatin1ByteArray
import co.topl.modifier.box.AssetCode
import co.topl.modifier.box.AssetValue
import co.topl.modifier.box.SecurityRoot
import co.topl.modifier.transaction.AssetTransfer
import co.topl.modifier.transaction.builder.BoxSelectionAlgorithms
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
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
import co.topl.attestation._

import java.util.stream
import scala.collection.JavaConverters._
import scala.concurrent.Await

import ToplRpc.Transaction.RawAssetTransfer
import co.topl.daml.assets.BoxPreservingAssetTransaction
import scala.collection.immutable.ListMap

class AssetMintingRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext
) extends AbstractProcessor(damlAppContext, toplContext) {

  val logger = LoggerFactory.getLogger(classOf[AssetMintingRequestProcessor])

  import toplContext.provider._

  def createParams(assetMintingRequest: AssetMintingRequest): RawAssetTransfer.Params =
    RawAssetTransfer.Params(
      propositionType =
        PublicKeyPropositionCurve25519.typeString, // required fixed string for now, exciting possibilities in the future!
      sender = NonEmptyChain
        .fromSeq(
          assetMintingRequest.from.asScala.toSeq
            .map(Base58Data.unsafe)
            .map(_.decodeAddress.getOrThrow())
        )
        .get, // Set of addresses whose state you want to use for the transaction
      recipients = NonEmptyChain
        .fromSeq(
          assetMintingRequest.to.asScala.toSeq.map(x =>
            (
              Base58Data.unsafe(x._1).decodeAddress.getOrThrow(),
              AssetValue(
                Int128(x._2.intValue()),
                AssetCode(
                  assetMintingRequest.assetCode.version.toByte,
                  Base58Data
                    .unsafe(assetMintingRequest.from.get(0))
                    .decodeAddress
                    .getOrThrow(),
                  Latin1Data.fromData(
                    utf8StringToLatin1ByteArray(assetMintingRequest.assetCode.shortName)
                  )
                ),
                assetMintingRequest.someCommitRoot
                  .map(x => SecurityRoot.fromBase58(Base58Data.unsafe(x)))
                  .orElse(SecurityRoot.empty),
                assetMintingRequest.someMetadata
                  .map(x =>
                    Option(
                      Latin1Data.fromData(
                        utf8StringToLatin1ByteArray(x)
                      )
                    )
                  )
                  .orElse(None)
              )
            )
          )
        )
        .get, // Chain of (Recipients, Value) tuples that represent the output boxes
      fee = Int128(
        assetMintingRequest.fee
      ), // fee to be paid to the network for the transaction (unit is nanoPoly)
      changeAddress = Base58Data
        .unsafe(assetMintingRequest.changeAddress)
        .decodeAddress
        .getOrThrow(), // who will get ALL the change from the transaction?
      consolidationAddress = Base58Data
        .unsafe(assetMintingRequest.changeAddress)
        .decodeAddress
        .getOrThrow(), // who will get ALL the change from the transaction?
      minting = true,
      data = None, // upto 128 Latin-1 encoded characters of optional data,
      boxSelectionAlgorithm = BoxSelectionAlgorithms.All
    )

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command] = processEventAux(AssetMintingRequest.TEMPLATE_ID, event) {
    val mintingRequestContract =
      AssetMintingRequest.Contract.fromCreatedEvent(event).id
    val assetMintingRequest =
      AssetMintingRequest.fromValue(
        event.getArguments()
      )
    val params = createParams(assetMintingRequest)
    val rawTransaction =
      RawAssetTransfer.rpc(params).map { x =>
        x.rawTx
      }
    import scala.concurrent.duration._
    import scala.language.postfixOps
    Await.result(
      rawTransaction.fold(
        failure => {
          logger.info("Failed to obtain raw transaction from server.")
          logger.debug("Error: {}", failure)
          stream.Stream.of(
            mintingRequestContract
              .exerciseTransferRequest_Reject()
          )
        },
        rawMintingRequest => {
          val mintingRequest: AssetTransfer[PublicKeyPropositionCurve25519] =
            new BoxPreservingAssetTransaction[PublicKeyPropositionCurve25519](
              rawMintingRequest.from,
              rawMintingRequest.to,
              ListMap(),
              rawMintingRequest.fee,
              rawMintingRequest.timestamp,
              rawMintingRequest.data,
              rawMintingRequest.minting,
              AssetValue(
                Int128(assetMintingRequest.to.asScala.head._2.intValue()),
                AssetCode(
                  assetMintingRequest.assetCode.version.toByte,
                  Base58Data
                    .unsafe(assetMintingRequest.from.get(0))
                    .decodeAddress
                    .getOrThrow(),
                  Latin1Data.fromData(
                    utf8StringToLatin1ByteArray(assetMintingRequest.assetCode.shortName)
                  )
                ),
                assetMintingRequest.someCommitRoot
                  .map(x => SecurityRoot.fromBase58(Base58Data.unsafe(x)))
                  .orElse(SecurityRoot.empty),
                assetMintingRequest.someMetadata
                  .map(x =>
                    Option(
                      Latin1Data.fromData(
                        utf8StringToLatin1ByteArray(x)
                      )
                    )
                  )
                  .orElse(None)
              ),
              IndexedSeq()
            )
          val encodedTx =
            ByteVector(
              AssetTransferSerializer.toBytes(mintingRequest: AssetTransfer[PublicKeyPropositionCurve25519])
            ).toBase58
          logger.info("Successfully generated raw transaction for contract {}.", mintingRequestContract.contractId)
          import io.circe.syntax._
          logger.info("The returned json: {}", mintingRequest.asJson)
          logger.debug(
            "Encoded transaction: {}",
            encodedTx
          )

          stream.Stream.of(
            mintingRequestContract
              .exerciseMintingRequest_Accept(
                encodedTx
              )
          )
        }
      ),
      3 second
    )
  }

}
