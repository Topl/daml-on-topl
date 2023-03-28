package co.topl.daml.base

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import cats.effect.IO
import cats.effect.SyncIO
import co.topl.attestation.Address
import co.topl.attestation.Evidence
import co.topl.attestation.Proposition
import co.topl.client.Provider
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import co.topl.daml.api.model.topl.asset.SignedAssetTransfer
import co.topl.daml.api.model.topl.asset.UnsignedAssetTransferRequest
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.api.model.topl.utils.AssetCode
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.assets.processors.AssetMintingRequestProcessor
import co.topl.daml.assets.processors.AssetTransferRequestProcessor
import co.topl.daml.assets.processors.SignedAssetTransferRequestProcessor
import co.topl.daml.assets.processors.UnsignedAssetTransferRequestProcessor
import co.topl.daml.polys.processors.UnsignedTransferProcessor
import co.topl.modifier.box.PolyBox
import co.topl.modifier.box.SimpleValue
import co.topl.modifier.transaction
import co.topl.modifier.transaction.AssetTransfer
import co.topl.rpc.ToplRpc
import co.topl.utils.Int128
import co.topl.utils.NetworkType
import com.daml.ledger.api.v1.EventOuterClass.CreatedEvent
import com.daml.ledger.api.v1.EventOuterClass.Event
import com.daml.ledger.api.v1.TransactionOuterClass.Transaction
import com.daml.ledger.api.v1.ValueOuterClass.Identifier
import com.daml.ledger.api.v1.ValueOuterClass.Variant
import com.daml.ledger.javaapi.data
import com.daml.ledger.javaapi.data.Command
import io.circe.parser.parse
import munit.CatsEffectSuite

import java.util.Optional
import java.{util => ju}

trait UnsignedPolyTransferRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetTransferRequestContract = new UnsignedTransfer.ContractId("")

  val keyfile = """{
    "crypto": {
        "mac": "HdWGSCawZZ4ijtKZcVtmfq5ckDBET3B6DXKcRTMyr8Vh",
        "kdf": "scrypt",
        "cipherText": "5fqw5atEstpEr1jan5Lnot1kig592xcBdxFhBRM9BmHBH6PvL31x2rUH72Yq1i41Y5hcEvA2Pme7TPRuDAARpDnF",
        "kdfSalt": "HxQdxsw6GkY7WWji8F3cT8VE166WE3K5KiPxHGFTfktk",
        "cipher": "aes-256-ctr",
        "cipherParams": {
            "iv": "Y1zqPXxhpUkDjrJZHQxt1v"
        }
    },
    "address": "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"
    }
      """

  val assetTransferRequest = new UnsignedTransfer(
    "operator",
    "alice",
    "1",
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    """{"txType":"PolyTransfer","timestamp":1665697142778,"signatures":{},"newBoxes":[{"nonce":"8671064749621587799","id":"BYdcFpz9M8ZDHGaWzTToCJWDhjYWCaSqptZJzhL9jz74","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"PolyBox","value":{"type":"Simple","quantity":"999400"}},{"nonce":"-4209546341112737928","id":"4UxNACCKpt8hDdGa7iN4rwEELiaC168cizmXtq4Yx8sU","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"PolyBox","value":{"type":"Simple","quantity":"500"}}],"data":null,"from":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64","4767595726154610060"]],"minting":false,"txId":"q8LnJwPMbxVuuEHEVHHLC83KmhJBAiG5PmrihQyGpvnE","boxesToRemove":["FTCgbUwbedqyaT75djdZ7r58xr1z2GNDWrzcWVuXjy4K"],"fee":"100","to":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"999400"}],["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"500"}]],"propositionType":"PublicKeyCurve25519"}"""
  )

  def dummyStandardProcessor =
    new UnsignedTransferProcessor(
      damlAppContext,
      toplContext,
      "keyfile.json",
      "test",
      (x, y) => true,
      t => true
    ) {

      override def readFileM(fileName: String): IO[String] = IO(keyfile)

    }

  def dummyStandardProcessorWithErrorReturningTrue =
    new UnsignedTransferProcessor(
      damlAppContext,
      toplContext,
      "keyfile.json",
      "test",
      (x, y) => true,
      t => false
    ) {
      override def readFileM(fileName: String): IO[String] = IO(keyfile)
    }

  val dummyFailCondition =
    new UnsignedTransferProcessor(
      damlAppContext,
      toplContext,
      "keyfile.json",
      "test",
      (x, y) => false,
      t => true
    ) {
      override def readFileM(fileName: String): IO[String] = IO(throw new IllegalStateException())
    }

  def dummyFailingWithException =
    new UnsignedTransferProcessor(
      damlAppContext,
      toplContext,
      "keyfile.json",
      "test",
      (x, y) => true,
      t => true
    ) {
      override def readFileM(fileName: String): IO[String] = IO(throw new IllegalStateException())
    }

}
