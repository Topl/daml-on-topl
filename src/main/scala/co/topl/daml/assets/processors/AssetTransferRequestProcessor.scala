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
import co.topl.daml.processEventAux
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

class AssetTransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[AssetTransferRequest, AssetTransferRequest.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  val logger = LoggerFactory.getLogger(classOf[AssetTransferRequestProcessor])

  import toplContext.provider._

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) = processEventAux(
    AssetTransferRequest.TEMPLATE_ID,
    e => AssetTransferRequest.fromValue(e.getArguments()),
    e => AssetTransferRequest.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (assetTransferRequest, transferRequestContract) =>
    val address = assetTransferRequest.from.asScala.toSeq
      .map(Base58Data.unsafe)
      .map(_.decodeAddress.getOrThrow())
    val params =
      ToplRpc.NodeView.Balances
        .Params(
          address.toList
        )
    val balances = ToplRpc.NodeView.Balances.rpc(params)

    import scala.concurrent.duration._
    import scala.language.postfixOps
    Await.result(
      balances.fold(
        failure => {
          logger.info("Failed to obtain raw transaction from server.")
          logger.debug("Error: {}", failure)

          stream.Stream.of(
            transferRequestContract
              .exerciseAssetTransferRequest_Reject()
          )
        },
        balance => {
          val to =
            (
              Base58Data.unsafe(assetTransferRequest.to.get(0)._1).decodeAddress.getOrThrow(),
              SimpleValue(
                balance.values.map(_.Boxes.PolyBox.head.value.quantity).head - Int128(assetTransferRequest.fee)
              )
            ) :: assetTransferRequest.to.asScala.toSeq
              .map(x =>
                (
                  Base58Data.unsafe(x._1).decodeAddress.getOrThrow(),
                  AssetValue(
                    Int128(x._2.intValue()),
                    AssetCode(
                      assetTransferRequest.assetCode.version.toByte,
                      Base58Data
                        .unsafe(assetTransferRequest.from.get(0))
                        .decodeAddress
                        .getOrThrow(),
                      Latin1Data.fromData(
                        utf8StringToLatin1ByteArray(assetTransferRequest.assetCode.shortName)
                      )
                    ),
                    assetTransferRequest.someCommitRoot
                      .map(x => SecurityRoot.fromBase58(Base58Data.unsafe(x)))
                      .orElse(SecurityRoot.empty),
                    assetTransferRequest.someMetadata
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
              .toList

          val assetTransfer = AssetTransfer(
            balance.values.toList
              .flatMap(_.Boxes.PolyBox)
              .map(x => (address.head, x.nonce))
              .toIndexedSeq
              .++(
                balance.values.toList
                  .flatMap(_.Boxes.AssetBox)
                  .filter(_.nonce == assetTransferRequest.boxNonce)
                  .map(x => (address.head, x.nonce))
                  .toIndexedSeq
              ),
            to.toIndexedSeq,
            ListMap(),
            Int128(
              assetTransferRequest.fee
            ),
            System.currentTimeMillis(),
            None,
            false
          )
          val transferRequest = AssetTransferSerializer.toBytes(
            assetTransfer
          )
          val encodedTx =
            ByteVector(
              transferRequest
            ).toBase58
          logger.info("Successfully generated raw transaction for contract {}.", transferRequestContract.contractId)
          import io.circe.syntax._
          logger.info("The returned json: {}", transferRequest.asJson)
          logger.debug(
            "Encoded transaction: {}",
            encodedTx
          )

          stream.Stream.of(
            transferRequestContract
              .exerciseAssetTransferRequest_Accept(
                encodedTx,
                assetTransfer.newBoxes.toList.reverse.head.nonce
              )
          )
        }
      ),
      timeoutMillis millis
    )
  }

}
