package co.topl.daml.base

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import cats.effect.IO
import cats.effect.SyncIO
import co.topl.attestation.Address
import co.topl.attestation.Evidence
import co.topl.client.Provider
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import co.topl.daml.api.model.topl.asset.SignedAssetTransfer
import co.topl.daml.api.model.topl.asset.UnsignedAssetMinting
import co.topl.daml.api.model.topl.asset.UnsignedAssetTransferRequest
import co.topl.daml.api.model.topl.utils.AssetCode
import co.topl.daml.assets.processors.AssetMintingRequestProcessor
import co.topl.daml.assets.processors.AssetTransferRequestProcessor
import co.topl.daml.assets.processors.UnsignedAssetTransferRequestProcessor
import co.topl.daml.assets.processors.UnsignedMintingRequestProcessor
import co.topl.modifier.box.PolyBox
import co.topl.modifier.box.SimpleValue
import co.topl.rpc.ToplRpc
import co.topl.utils.Int128
import co.topl.utils.NetworkType
import com.daml.ledger.api.v1.EventOuterClass.CreatedEvent
import com.daml.ledger.api.v1.EventOuterClass.Event
import com.daml.ledger.api.v1.TransactionOuterClass.Transaction
import com.daml.ledger.javaapi.data
import com.daml.ledger.javaapi.data.Command
import munit.CatsEffectSuite

import java.util.Optional
import java.{util => ju}

trait UnsignedAssetMintingRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetTransferRequestContract = new UnsignedAssetMinting.ContractId("")

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

  val assetTransferRequest = new UnsignedAssetMinting(
    "operator",
    "alice",
    "1",
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
    new AssetCode(1L, "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", "Wheat"),
    5,
    Optional.empty(),
    Optional.empty(),
    1L,
    100L,
    """{"txType":"AssetTransfer","timestamp":1670347196841,"signatures":{},"newBoxes":[{"nonce":"1868907209648347650","id":"F7RCsePqSshr21kD6yWoaU7bJ9YEecvsHhs4LF1aNHXM","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"AssetBox","value":{"quantity":"1","assetCode":"6LmEme2MYkNpheCC3hWcNujUygr1aUfmzPH6F4naJDbvG6pfEkmDSarCvT","metadata":null,"type":"Asset","securityRoot":"11111111111111111111111111111111"}}],"data":null,"from":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64","1"]],"minting":true,"txId":"iDCcr6KgtvVwYS93cirsK923v5PGhzrsgpaGiz6QsWCt","boxesToRemove":["8UoxAoDY48dbYNBunLSgufEJVvh3rKsN7GZ5PaDx2i19"],"fee":"100","to":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"0"}],["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"quantity":"1","assetCode":"6LmEme2MYkNpheCC3hWcNujUygr1aUfmzPH6F4naJDbvG6pfEkmDSarCvT","metadata":null,"type":"Asset","securityRoot":"11111111111111111111111111111111"}]],"propositionType":"PublicKeyCurve25519"}"""
  )

  def dummyStandardProcessor =
    new UnsignedMintingRequestProcessor(
      damlAppContext,
      toplContext,
      "file.json",
      "test",
      (x, y) => true,
      t => true
    ) {

      override def readFileM(fileName: String): IO[String] = IO(keyfile)

    }

  def dummyStandardProcessorWithErrorReturningTrue =
    new UnsignedMintingRequestProcessor(
      damlAppContext,
      toplContext,
      "file.json",
      "password",
      (x, y) => true,
      t => false
    ) {

      override def readFileM(fileName: String): IO[String] = IO(keyfile)
    }

  val dummyFailCondition =
    new UnsignedMintingRequestProcessor(
      damlAppContext,
      toplContext,
      "file.json",
      "password",
      (x, y) => false,
      t => true
    ) {

      override def readFileM(fileName: String): IO[String] = IO(keyfile)
    }

  def dummyFailingWithException =
    new UnsignedMintingRequestProcessor(
      damlAppContext,
      toplContext,
      "file.json",
      "password",
      (x, y) => true,
      t => true
    ) {
      override def readFileM(fileName: String): IO[String] = IO(throw new IllegalStateException())
    }

}
