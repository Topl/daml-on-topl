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
import cats.effect.IO
import cats.arrow.FunctionK
import co.topl.daml.RpcClientFailureException
import co.topl.daml.algebras.PolySpecificOperationsAlgebra

class TransferRequestProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  timeoutMillis:  Int,
  callback:       java.util.function.BiFunction[TransferRequest, TransferRequest.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError)
    with PolySpecificOperationsAlgebra {

  val logger = LoggerFactory.getLogger(classOf[TransferRequestProcessor])

  import toplContext.provider._

  def prepareTransactionM(
    transferRequest:         TransferRequest,
    transferRequestContract: TransferRequest.ContractId
  ): IO[stream.Stream[Command]] = (for {
    params         <- createParamsM(transferRequest)
    rawTransaction <- createRawTxM(params)
    encodedTx      <- IO(ByteVector(PolyTransferSerializer.toBytes(rawTransaction)).toBase58)
  } yield {
    logger.info("Successfully generated raw transaction for contract {}.", transferRequestContract.contractId)
    import io.circe.syntax._
    logger.info(
      "Raw transaction: {}",
      rawTransaction.asJson
    )
    logger.debug(
      "Encoded transaction: {}",
      encodedTx
    )

    stream.Stream.of(
      transferRequestContract
        .exerciseTransferRequest_Accept(
          encodedTx
        )
    ): stream.Stream[Command]

  }).handleError { failure =>
    logger.info("Failed to obtain raw transaction from server.")
    logger.info("Error: {}", failure)
    stream.Stream.of(
      transferRequestContract
        .exerciseTransferRequest_Reject()
    )
  }

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] =
    processEventAux(
      TransferRequest.TEMPLATE_ID,
      e => TransferRequest.fromValue(e.getArguments()),
      e => TransferRequest.Contract.fromCreatedEvent(e).id,
      callback.apply,
      event
    ) {
      prepareTransactionM
    }

}
