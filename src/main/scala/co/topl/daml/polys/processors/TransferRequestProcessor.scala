package co.topl.daml.polys.processors

import com.daml.ledger.rxjava.DamlLedgerClient
import io.reactivex.Single
import com.daml.ledger.javaapi.data.Command
import co.topl.akkahttprpc.implicits.client.rpcToClient
import cats.data.{EitherT, NonEmptyChain}
import com.daml.ledger.javaapi.data.CreatedEvent
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.client.Provider
import co.topl.attestation.{Address, PublicKeyPropositionCurve25519}
import co.topl.utils.StringDataTypes.{Base58Data, Latin1Data}
import co.topl.modifier.transaction.builder.BoxSelectionAlgorithms
import co.topl.attestation.AddressCodec.implicits._
import co.topl.rpc.implicits.client._
import scala.concurrent.{ExecutionContext, Future}
import co.topl.utils.IdiomaticScalaTransition.implicits.toValidatedOps
import scala.collection.JavaConverters._
import co.topl.daml.api.model.da.types.{Tuple2 => DamlTuple2}
import co.topl.daml.api.model.topl.transfer.TransferRequest

import co.topl.rpc.ToplRpc.Transaction.{BroadcastTx, RawArbitTransfer, RawAssetTransfer, RawPolyTransfer}
import co.topl.attestation.keyManagement.KeyRing
import co.topl.attestation.keyManagement.PrivateKeyCurve25519
import co.topl.attestation.keyManagement.KeyfileCurve25519
import co.topl.attestation.keyManagement.KeyfileCurve25519Companion
import co.topl.rpc.ToplRpc
import akka.actor.ActorSystem
import com.daml.ledger.javaapi.data.Identifier
import java.util.stream
import co.topl.utils.Int128
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scodec.bits._
import com.daml.ledger.javaapi.data.Transaction
import com.daml.ledger.rxjava.LedgerClient
import java.util.UUID
import co.topl.daml.OperatorMain
import io.reactivex.subjects.SingleSubject
import com.google.protobuf.Empty
import org.slf4j.LoggerFactory
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.AbstractProcessor
import co.topl.daml.processEventAux

class TransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[TransferRequest, TransferRequest.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  val logger = LoggerFactory.getLogger(classOf[TransferRequestProcessor])

  import toplContext.provider._

  def createParams(transferRequest: TransferRequest): RawPolyTransfer.Params =
    RawPolyTransfer.Params(
      propositionType =
        PublicKeyPropositionCurve25519.typeString, // required fixed string for now, exciting possibilities in the future!
      sender = NonEmptyChain
        .fromSeq(
          transferRequest.from.asScala.toSeq
            .map(Base58Data.unsafe)
            .map(_.decodeAddress.getOrThrow())
        )
        .get, // Set of addresses whose state you want to use for the transaction
      recipients = NonEmptyChain
        .fromSeq(
          transferRequest.to.asScala.toSeq.map(x =>
            (
              Base58Data.unsafe(x._1).decodeAddress.getOrThrow(),
              Int128(x._2.intValue())
            )
          )
        )
        .get, // Chain of (Recipients, Value) tuples that represent the output boxes
      fee = Int128(
        transferRequest.fee
      ), // fee to be paid to the network for the transaction (unit is nanoPoly)
      changeAddress = Base58Data
        .unsafe(transferRequest.changeAddress)
        .decodeAddress
        .getOrThrow(), // who will get ALL the change from the transaction?
      data = None, // upto 128 Latin-1 encoded characters of optional data,
      boxSelectionAlgorithm = BoxSelectionAlgorithms.All
    )

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) =
    processEventAux(
      TransferRequest.TEMPLATE_ID,
      e => TransferRequest.fromValue(e.getArguments()),
      e => TransferRequest.Contract.fromCreatedEvent(e).id,
      callback.apply,
      event
    ) { (transferRequest, transferRequestContract) =>
      val params = createParams(transferRequest)

      val rawTransaction =
        ToplRpc.Transaction.RawPolyTransfer.rpc(params).map(_.rawTx)
      import scala.concurrent.duration._
      import scala.language.postfixOps
      Await.result(
        rawTransaction.fold(
          failure => {
            logger.info("Failed to obtain raw transaction from server.")
            logger.debug("Error: {}", failure)
            stream.Stream.of(
              transferRequestContract
                .exerciseTransferRequest_Reject()
            )
          },
          polyTransfer => {
            val encodedTx = ByteVector(PolyTransferSerializer.toBytes(polyTransfer)).toBase58
            logger.info("Successfully generated raw transaction for contract {}.", transferRequestContract.contractId)
            logger.debug(
              "Encoded transaction: {}",
              encodedTx
            )

            stream.Stream.of(
              transferRequestContract
                .exerciseTransferRequest_Accept(
                  encodedTx
                )
            )
          }
        ),
        timeoutMillis millis
      )

    }

}
