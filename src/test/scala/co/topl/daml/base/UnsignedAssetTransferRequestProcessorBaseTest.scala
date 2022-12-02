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

trait UnsignedAssetTransferRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetTransferRequestContract = new UnsignedAssetTransferRequest.ContractId("")

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

  val assetTransferRequest = new UnsignedAssetTransferRequest(
    "operator",
    "alice",
    Optional.of("1"),
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "address",
    new AssetCode(1L, "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", "Wheat"),
    5,
    Optional.empty(),
    Optional.empty(),
    1L,
    100L,
    "NpGKt2zHYNBqeidrSaTvGhtrwUTjqoTXKSpbtD7exMK3EZgZW2dG61sBixZED5fjh5CgAUHpLbpm4hsh4L7Eehf4SY59yU2hXTnXNPWistwCApDppTPqDiNr8RVQK4xy372NnPW8Yy9kJ4ne8GBi5eAJM5TpPn5oxFm7TTH1Wgc3DFvmsSZvjABG1fFTM3yWNSMYytTjhkbKeEb7qKYW6hGcHocDRbAFxtGSF2mHcYQHuP17f9z21b7vxXwQnckEajGF2jkswvrvtYdcEbJXiTAxdYeRFyERwVeKzKbgnM4gsdEecYfAQA4Xqu1jNcdYquuweFBW",
    ""
  )

  def dummyStandardProcessor =
    new UnsignedAssetTransferRequestProcessor(
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
    new UnsignedAssetTransferRequestProcessor(
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
    new UnsignedAssetTransferRequestProcessor(
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
    new UnsignedAssetTransferRequestProcessor(
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
