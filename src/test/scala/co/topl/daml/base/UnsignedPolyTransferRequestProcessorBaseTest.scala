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
import co.topl.daml.api.model.topl.transfer.UnsignedTransfer
import co.topl.daml.polys.processors.UnsignedTransferProcessor

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
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "3skAnq3Un9CN9217fRmrgMyVWrBSy6oyZQwUaVW3u2cADA7tURUT2LHvX9WEiXRWGA8eiSGoSeRE6QsMjtCL1u2WdgVx92WPRB2tY1g6gSxPE1FKK2fvChwaA4n4PTxGdmYvyZhoiGLAKDjmrCPM6wWDEvqjetVZATtFxvSYUxKFj48PYmFAexDE52be6tT7178V5cdZsh7B7hvXhh8FuUViVXtiTCkoEGygTH5Qf2SCP"
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
