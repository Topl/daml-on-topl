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
import co.topl.daml.api.model.topl.utils.AssetCode
import co.topl.daml.api.model.topl.utils.SendStatus
import co.topl.daml.api.model.topl.utils.sendstatus.Pending
import co.topl.daml.assets.processors.AssetMintingRequestProcessor
import co.topl.daml.assets.processors.AssetTransferRequestProcessor
import co.topl.daml.assets.processors.SignedAssetTransferRequestProcessor
import co.topl.daml.assets.processors.UnsignedAssetTransferRequestProcessor
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

trait SignedAssetTransferRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val serverResponse = """{
  "txType" : "AssetTransfer",
  "timestamp" : 1665688625665,
  "signatures" : {
    "Jeje2tumVTJW52yHyK8bXhvB3ErXPnPmuiRe3rZHfSiF" : "9mNyuG4eCGbW1Q9C21JSUSYadd7K4eySdMbVXfuZBNmYDH2Kfb8jqxLKHeQyAQ3xpxNsDA1kUwQ8Bgfvvsa5wkdC"
  },
  "newBoxes" : [
    {
      "nonce" : "2658643020273269730",
      "id" : "A8Hham14ZuQLe52FngqGyFNNb7zQgiyjzqzcccPDBNHJ",
      "evidence" : "LmJU2jCd6pUWNKQgifyxXWw9XnFr2cxxSY57MLqjqM6e",
      "type" : "PolyBox",
      "value" : {
        "type" : "Simple",
        "quantity" : "999800"
      }
    },
    {
      "nonce" : "-4800355605196900950",
      "id" : "4v8syuXy9uhgirbRda7Ku7gcT3vj8EJqbRVPZdtPpzvu",
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
      "-5646404903357100774"
    ],
    [
      "AU9avKWiVVPKyU9LoMqDpduS4knoLDMdPEK54qKDNBpdnAMwQZcS",
      "-859243269870710928"
    ]
  ],
  "minting" : false,
  "txId" : "jabkk9n8Qrqc1jQvozEaY9fKbwiz5ZTdFn62ym9vE7tL",
  "boxesToRemove" : [
    "2LdNZGZU4U9mdZjbXR7KL5Svx8oiMfBozzd7s4pMQNKX",
    "2iRje8a1NTafsAjRzavfjMwvvSAo5E9ev3Fw3GXPcfmR"
  ],
  "fee" : "100",
  "to" : [
    [
      "AU9avKWiVVPKyU9LoMqDpduS4knoLDMdPEK54qKDNBpdnAMwQZcS",
      {
        "type" : "Simple",
        "quantity" : "999800"
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
    "C9ArQ3UPR2NdbEHatFGHDr1zKHD6MvwDBNHYpsZmhNCZmSDdSBWKW41s2qKMVji5JHZ5PLKJKr7CbUyFj4hYUW9oeQHMcSFe43wzyD8wG5Ch6PaJUXBaZc81FQ5om5LxeS2ozGZVgNbctsmjHyHf2Weo74yYd2n9NNDeyq3Y6kyHtKUTASBQqyn8oQjVaP821znyXiRih2tZGf5sjHiAun3TzGAGuRTvmYTbayZawmjZEozEEW5tv6hXVTxEeonNqGnNcm34HYcnirnNP7m7kJZbtaqc8n7Mr399P6oQdiNkqimBXQ2yxqGZ16xb1SqcyYSb8ZNmoQ7SfvrVZniz2cPrePHoHCdXprPrqe6wQcMnUin1R5atCFJxmeihBv5iGDb7K3GsZn8JGWk1uBR7wvoJLZiaezUBPBCEB1hEB66Exxic2zoqdsWn9ttz1G4TMyF2dGgwEunZvQ",
    """{"txType":"AssetTransfer","timestamp":1670346927771,"signatures":{"STcQmmQvXTxU9MHDrvjXV8p2JDiCqHfLQSwzUfR1pMgX":"7jb5q5qDLZjjMjp7ayzLEgGqTPw3kKX6u14vkGiKsyKUvwc66BJHvLdDp7TLsDo13Zjce9WNnPpL87ZuyZM6yv5b"},"newBoxes":[{"nonce":"-7210283914750651566","id":"8zjzVCNGbHhSC3U2Ptn4ZehF8V9DG8oY5KjZ1LrST1WA","evidence":"TjEmqLT7VnfsbSMTUptWXVTEms2sHfP5bXSZa9iue7Tq","type":"AssetBox","value":{"quantity":"1","assetCode":"6LmEme2MYkNpheCC3hWcNujUygr1aUfmzPH6F4naJDbvG6pfEkmDSarCvT","metadata":null,"type":"Asset","securityRoot":"11111111111111111111111111111111"}}],"data":null,"from":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64","1"]],"minting":false,"txId":"hoFF6oZRx2tgxXu4U5B2y5uLKvPCcKBu8kdm3ZYbzpG4","boxesToRemove":["8UoxAoDY48dbYNBunLSgufEJVvh3rKsN7GZ5PaDx2i19"],"fee":"100","to":[["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"type":"Simple","quantity":"0"}],["AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64",{"quantity":"1","assetCode":"6LmEme2MYkNpheCC3hWcNujUygr1aUfmzPH6F4naJDbvG6pfEkmDSarCvT","metadata":null,"type":"Asset","securityRoot":"11111111111111111111111111111111"}]],"propositionType":"PublicKeyCurve25519"}""",
    Pending.fromValue(new data.Variant("Pending", data.Unit.getInstance()))
  )

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
    ) {}

  val dummyFailCondition =
    new SignedAssetTransferRequestProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      () => "1",
      (x, y) => false,
      t => true
    ) {}

  def dummyFailingWithException =
    new SignedAssetTransferRequestProcessor(
      damlAppContext,
      toplContext,
      1000,
      1,
      () => "1",
      (x, y) => true,
      t => true
    ) {}

}
