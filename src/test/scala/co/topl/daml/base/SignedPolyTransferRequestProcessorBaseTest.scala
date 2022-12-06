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
import co.topl.daml.api.model.topl.asset.SignedAssetTransfer
import co.topl.daml.api.model.topl.asset.UnsignedAssetTransferRequest
import co.topl.daml.assets.processors.UnsignedAssetTransferRequestProcessor
import co.topl.daml.api.model.topl.utils.SendStatus
import com.daml.ledger.api.v1.ValueOuterClass.Variant
import co.topl.daml.assets.processors.SignedAssetTransferRequestProcessor
import com.daml.ledger.api.v1.ValueOuterClass.Identifier
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.modifier.transaction
import co.topl.modifier.transaction.AssetTransfer
import co.topl.attestation.Proposition
import io.circe.parser.parse
import co.topl.daml.api.model.topl.transfer.SignedTransfer
import co.topl.daml.polys.processors.SignedTransferProcessor

trait SignedPolyTransferRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetTransferRequestContract = new SignedTransfer.ContractId("")

  val assetTransferRequest = new SignedTransfer(
    "operator",
    "alice",
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "3skAnq3Un9CN9217fRmrgMyVWrBSy6oyZQwUaVW3u2cADA7tURUT2LHvX9WEiXRWGA8eiSGoSeRE6QsMjtCL1u2WdgVx92WPRB2tY1g6gSxPE1FKK2fvChwaA4n4PTxGdmYvyZhoiGLAKDjmrCPM6wWDEvqjetVZATtFxvSYUxKFj48PYmFAexDE52be6tT7178V5cdZsh7B7hvXhh8FuUViVXtiTCkoEGygTH5Qf2SCP",
    """{"txType":"PolyTransfer","timestamp":1665697142778,"signatures":{"STcQmmQvXTxU9MHDrvjXV8p2JDiCqHfLQSwzUfR1pMgX":"9HMjJAHv3CN13fpZNUnS4j6jnGzzU8xMUiyHkhT3mG2dB91JZPGgR1anpDYM4KYDPAF7Bzv3fRFHRmkdNvPJtW2y"},"newBoxes":[{"nonce":"8671064749621587799","id":"BYdcFpz9M8ZDHGaWzTToCJWDhjYWCaSqptZJzhL9jz74","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"PolyBox","value":{"type":"Simple","quantity":"999400"}},{"nonce":"-4209546341112737928","id":"4UxNACCKpt8hDdGa7iN4rwEELiaC168cizmXtq4Yx8sU","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"PolyBox","value":{"type":"Simple","quantity":"500"}}],"data":null,"from":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64","4767595726154610060"]],"minting":false,"txId":"q8LnJwPMbxVuuEHEVHHLC83KmhJBAiG5PmrihQyGpvnE","boxesToRemove":["FTCgbUwbedqyaT75djdZ7r58xr1z2GNDWrzcWVuXjy4K"],"fee":"100","to":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"999400"}],["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"500"}]],"propositionType":"PublicKeyCurve25519"}""",
    Pending.fromValue(new data.Variant("Pending", data.Unit.getInstance()))
  )

  val serverResponse = """{
  "txType" : "PolyTransfer",
  "timestamp" : 1665776529455,
  "signatures" : {
    "STcQmmQvXTxU9MHDrvjXV8p2JDiCqHfLQSwzUfR1pMgX" : "AUsiVtLAWoh8qu3ZTB8TQXxDLeJ6RjHY4hTCkJbPpvKnUqFEhzVtUvT5SVcCgVaSeAKWBoXVFuJvHasGXRdyJ9dQ"
  },
  "newBoxes" : [
    {
      "nonce" : "-9219861743849205608",
      "id" : "BAR45edVVziGSFPqjMyUsGqeY2zvrqqqGDEbRFgWtyLs",
      "evidence" : "TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq",
      "type" : "PolyBox",
      "value" : {
        "type" : "Simple",
        "quantity" : "999200"
      }
    },
    {
      "nonce" : "7201239525968841825",
      "id" : "BbpeyGeT918A3afB9FDD82DpMVmVNcv5TRqcUQj8h9sh",
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
      "5497647477238761934"
    ],
    [
      "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
      "8100657596031342414"
    ]
  ],
  "minting" : false,
  "txId" : "hLWfxRtaeh9JNdBFAayAaHUCJBZGE9EdBMnJihtA2mfu",
  "boxesToRemove" : [
    "GZSF6UWACJsLby3w9UieGbkyEb9ssoS2NhvYTa9LWJ6D",
    "HUpVUFDmvPY4sp5hreiMdCiEmEj3FL5NyHeoEcjgH2j8"
  ],
  "fee" : "100",
  "to" : [
    [
      "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
      {
        "type" : "Simple",
        "quantity" : "999200"
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
}"""

  def dummyStandardProcessor =
    new SignedTransferProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      (x, y) => true,
      t => true
    ) {

      override def broadcastTransactionM(
        signedTx: transaction.PolyTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = for {
        json <- IO.fromEither(parse(serverResponse))
        res <- IO(
          transaction.Transaction.jsonDecoder.decodeJson(json)
        )
      } yield res.toOption.get

    }

  def dummyStandardProcessorWithErrorReturningTrue =
    new SignedTransferProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      (x, y) => true,
      t => false
    ) {

      override def broadcastTransactionM(
        signedTx: transaction.PolyTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = for {
        json <- IO.fromEither(parse(serverResponse))
        res <- IO(
          transaction.Transaction.jsonDecoder.decodeJson(json)
        )
      } yield res.toOption.get

    }

  val dummyFailCondition =
    new SignedTransferProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      (x, y) => false,
      t => true
    ) {

      override def broadcastTransactionM(
        signedTx: transaction.PolyTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = for {
        json <- IO.fromEither(parse(serverResponse))
        res <- IO(
          transaction.Transaction.jsonDecoder.decodeJson(json)
        )
      } yield res.toOption.get

    }

  def dummyFailingWithException =
    new SignedTransferProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      (x, y) => true,
      t => true
    ) {

      override def broadcastTransactionM(
        signedTx: transaction.PolyTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = IO(throw new IllegalStateException())
    }

}
