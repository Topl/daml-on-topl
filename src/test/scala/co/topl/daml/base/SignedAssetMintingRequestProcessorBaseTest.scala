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
import co.topl.daml.api.model.topl.asset.SignedAssetMinting
import io.circe.Json
import io.circe.parser.parse

trait SignedAssetMintingRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetTransferRequestContract = new SignedAssetTransfer.ContractId("")

  val assetTransferRequest = new SignedAssetTransfer(
    "operator",
    "alice",
    Optional.of("1"),
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",
    new AssetCode(1L, "AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", "Wheat"),
    5,
    Optional.empty(),
    Optional.empty(),
    1L,
    100L,
    "NpGKt2zHYNBqeidrSaTvGhtrwUTjqoTXKSpbtD7exMK3EZgZW2dG61sBixZED5fjh5CgAUHpLbpm4hsh4L7Eehf4SY59yU2hXTnXNPWistwCApDppTPqDiNr8RVQK4xy372NnPW8Yy9kJ4ne8GBi5eAJM5TpPn5oxFm7TTH1Wgc3DFvmsSZvjABG1fFTM3yWNSMYytTjhkbKeEb7qKYW6hGcHocDRbAFxtGSF2mHcYQHuP17f9z21b7vxXwQnckEajGF2jkswvrvtYdcEbJXiTAxdYeRFyERwVeKzKbgnM4gsdEecYfAQA4Xqu1jNcdYquuweFBW",
    """{"txType":"AssetTransfer","timestamp":1670347196841,"signatures":{"STcQmmQvXTxU9MHDrvjXV8p2JDiCqHfLQSwzUfR1pMgX":"ATAZmA7kWPNkzXv4xtJzXmELab781DzNczJojFJ7XmtKJGctGb4iZWEhdUGbuHfzGQXWneUSQWNTMS3PUNUyKUKw"},"newBoxes":[{"nonce":"1868907209648347650","id":"F7RCsePqSshr21kD6yWoaU7bJ9YEecvsHhs4LF1aNHXM","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"AssetBox","value":{"quantity":"1","assetCode":"6LmEme2MYkNpheCC3hWcNujUygr1aUfmzPH6F4naJDbvG6pfEkmDSarCvT","metadata":null,"type":"Asset","securityRoot":"11111111111111111111111111111111"}}],"data":null,"from":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64","1"]],"minting":true,"txId":"iDCcr6KgtvVwYS93cirsK923v5PGhzrsgpaGiz6QsWCt","boxesToRemove":["8UoxAoDY48dbYNBunLSgufEJVvh3rKsN7GZ5PaDx2i19"],"fee":"100","to":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"0"}],["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"quantity":"1","assetCode":"6LmEme2MYkNpheCC3hWcNujUygr1aUfmzPH6F4naJDbvG6pfEkmDSarCvT","metadata":null,"type":"Asset","securityRoot":"11111111111111111111111111111111"}]],"propositionType":"PublicKeyCurve25519"}""",
    Pending.fromValue(new data.Variant("Pending", data.Unit.getInstance()))
  )

  val serverResponse = """{
  "txType" : "AssetTransfer",
  "timestamp" : 1665688547933,
  "signatures" : {
    "Jeje2tumVTJW52yHyK8bXhvB3ErXPnPmuiRe3rZHfSiF" : "7nvQ3YhXRxbqQL38VFsYCBgAryaxKh1HhwhyvxELtDtEpvAo3auXkPVHiQfrTRFNwDAPm2JAkA8xRvm94Vavk2FH"
  },
  "newBoxes" : [
    {
      "nonce" : "-5646404903357100774",
      "id" : "2LdNZGZU4U9mdZjbXR7KL5Svx8oiMfBozzd7s4pMQNKX",
      "evidence" : "LmJU2jCd6pUWNKQgifyxXWw9XnFr2cxxSY57MLqjqM6e",
      "type" : "PolyBox",
      "value" : {
        "type" : "Simple",
        "quantity" : "999900"
      }
    },
    {
      "nonce" : "-859243269870710928",
      "id" : "2iRje8a1NTafsAjRzavfjMwvvSAo5E9ev3Fw3GXPcfmR",
      "evidence" : "LmJU2jCd6pUWNKQgifyxXWw9XnFr2cxxSY57MLqjqM6e",
      "type" : "AssetBox",
      "value" : {
        "quantity" : "5",
        "assetCode" : "6Lm9dRk8kZqP1ZgKJutmNAmkwZsqKu14JLrtWWcZapuFpZ8y5ffUFaMQqu",
        "metadata" : "Test0",
        "type" : "Asset",
        "securityRoot" : "11111111111111111111111111111111"
      }
    }
  ],
  "data" : null,
  "from" : [
    [
      "AU9avKWiVVPKyU9LoMqDpduS4knoLDMdPEK54qKDNBpdnAMwQZcS",
      "7750101586306631723"
    ]
  ],
  "minting" : true,
  "txId" : "dq2jCaWh9F5gHB7TeDPKxxANZhCqKtcWuv2cQomzgnSM",
  "boxesToRemove" : [
    "4zMJRRUFMBQHEjgXu3tZ6c7y9dSN9mS1agdxJTSkWdoK"
  ],
  "fee" : "100",
  "to" : [
    [
      "AU9avKWiVVPKyU9LoMqDpduS4knoLDMdPEK54qKDNBpdnAMwQZcS",
      {
        "type" : "Simple",
        "quantity" : "999900"
      }
    ],
    [
      "AU9avKWiVVPKyU9LoMqDpduS4knoLDMdPEK54qKDNBpdnAMwQZcS",
      {
        "quantity" : "5",
        "assetCode" : "6Lm9dRk8kZqP1ZgKJutmNAmkwZsqKu14JLrtWWcZapuFpZ8y5ffUFaMQqu",
        "metadata" : "Test0",
        "type" : "Asset",
        "securityRoot" : "11111111111111111111111111111111"
      }
    ]
  ],
  "propositionType" : "PublicKeyCurve25519"
}"""

  def dummyStandardProcessor =
    new SignedAssetTransferRequestProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      () => "1",
      (x, y) => true,
      t => true
    ) {

      override def broadcastTransactionM(
        signedTx: AssetTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = for {
        json <- IO.fromEither(parse(serverResponse))
        res <- IO(
          transaction.Transaction.jsonDecoder.decodeJson(json)
        )
      } yield res.toOption.get

    }

  def dummyStandardProcessorWithErrorReturningTrue =
    new SignedAssetTransferRequestProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      () => "1",
      (x, y) => true,
      t => false
    ) {

      override def broadcastTransactionM(
        signedTx: AssetTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = for {
        json <- IO.fromEither(parse(serverResponse))
        res <- IO(
          transaction.Transaction.jsonDecoder.decodeJson(json)
        )
      } yield res.toOption.get
    }

  val dummyFailCondition =
    new SignedAssetTransferRequestProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      () => "1",
      (x, y) => false,
      t => true
    ) {

      override def broadcastTransactionM(
        signedTx: AssetTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = for {
        json <- IO.fromEither(parse(serverResponse))
        res <- IO(
          transaction.Transaction.jsonDecoder.decodeJson(json)
        )
      } yield res.toOption.get
    }

  def dummyFailingWithException =
    new SignedAssetTransferRequestProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      () => "1",
      (x, y) => true,
      t => true
    ) {

      override def broadcastTransactionM(
        signedTx: AssetTransfer[_ <: Proposition]
      ): IO[transaction.Transaction[Any, _ <: Proposition]] = IO(throw new IllegalStateException())
    }

}
