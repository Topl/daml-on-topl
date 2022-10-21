package co.topl.daml.base

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import java.util.Optional
import java.{util => ju}
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.utils.AssetCode
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.assets.processors.AssetMintingRequestProcessor
import com.daml.ledger.javaapi.data.Command
import akka.http.scaladsl.model.Uri
import co.topl.client.Provider
import akka.actor.ActorSystem
import co.topl.rpc.ToplRpc
import co.topl.attestation.Address
import co.topl.utils.Int128
import co.topl.attestation.Evidence
import co.topl.utils.NetworkType
import co.topl.modifier.box.PolyBox
import co.topl.modifier.box.SimpleValue
import com.daml.ledger.api.v1.EventOuterClass.CreatedEvent
import com.daml.ledger.javaapi.data
import com.daml.ledger.api.v1.TransactionOuterClass.Transaction
import com.daml.ledger.api.v1.EventOuterClass.Event
import co.topl.daml.assets.processors.AssetTransferRequestProcessor
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import co.topl.daml.api.model.topl.transfer.TransferRequest
import co.topl.daml.polys.processors.TransferRequestProcessor
import co.topl.akkahttprpc.RpcClientFailure
import co.topl.attestation.Proposition
import co.topl.modifier.transaction.PolyTransfer
import io.circe.parser.parse
import co.topl.modifier.transaction
import co.topl.akkahttprpc.implicits.client._
import co.topl.rpc.implicits.client._

trait PolyTransferRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetTransferRequestContract = new TransferRequest.ContractId("")

  val serverResponse = """{
    "messageToSign": "",
    "rawTx": {
  "txType" : "PolyTransfer",
  "timestamp" : 1665697142778,
  "signatures" : {
    
  },
  "newBoxes" : [
    {
      "nonce" : "8671064749621587799",
      "id" : "BYdcFpz9M8ZDHGaWzTToCJWDhjYWCaSqptZJzhL9jz74",
      "evidence" : "TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq",
      "type" : "PolyBox",
      "value" : {
        "type" : "Simple",
        "quantity" : "999400"
      }
    },
    {
      "nonce" : "-4209546341112737928",
      "id" : "4UxNACCKpt8hDdGa7iN4rwEELiaC168cizmXtq4Yx8sU",
      "evidence" : "TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq",
      "type" : "PolyBox",
      "value" : {
        "type" : "Simple",
        "quantity" : "500"
      }
    }
  ],
  "data" : null,
  "from" : [
    [
      "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
      "4767595726154610060"
    ]
  ],
  "minting" : false,
  "txId" : "q8LnJwPMbxVuuEHEVHHLC83KmhJBAiG5PmrihQyGpvnE",
  "boxesToRemove" : [
    "FTCgbUwbedqyaT75djdZ7r58xr1z2GNDWrzcWVuXjy4K"
  ],
  "fee" : "100",
  "to" : [
    [
      "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
      {
        "type" : "Simple",
        "quantity" : "999400"
      }
    ],
    [
      "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
      {
        "type" : "Simple",
        "quantity" : "500"
      }
    ]
  ],
  "propositionType" : "PublicKeyCurve25519"
}
}"""

  val assetTransferRequest = new TransferRequest(
    "operator",
    "alice",
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
    100L
  )

  def dummyStandardProcessor =
    new TransferRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => true, t => true) {

      override def createRawTxM(
        params: ToplRpc.Transaction.RawPolyTransfer.Params
      ): IO[PolyTransfer[Proposition]] =
        for {
          json <- IO.fromEither(parse(serverResponse))
          res <- IO.fromEither(
            transactionRawPolyTransferResponseDecoder.decodeJson(json)
          )
        } yield res.rawTx
    }

  def dummyStandardProcessorWithErrorReturningTrue =
    new TransferRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => true, t => false) {

      override def createRawTxM(
        params: ToplRpc.Transaction.RawPolyTransfer.Params
      ): IO[PolyTransfer[Proposition]] = IO(throw new IllegalStateException)
    }

  val dummyFailCondition =
    new TransferRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => false, t => true) {

      override def createRawTxM(
        params: ToplRpc.Transaction.RawPolyTransfer.Params
      ): IO[PolyTransfer[Proposition]] =
        for {
          json <- IO.fromEither(parse(serverResponse))
          res <- IO.fromEither(
            transactionRawPolyTransferResponseDecoder.decodeJson(json)
          )
        } yield res.rawTx
    }

  def dummyFailingWithException =
    new TransferRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => true, t => true) {
      override def getBalanceM(param: ToplRpc.NodeView.Balances.Params) = IO(throw new IllegalStateException())
    }

}
