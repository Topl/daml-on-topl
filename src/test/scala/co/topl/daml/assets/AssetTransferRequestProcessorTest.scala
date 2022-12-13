package co.topl.daml.assets

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
import co.topl.daml.api.model.topl.utils.AssetCode
import co.topl.daml.assets.processors.AssetMintingRequestProcessor
import co.topl.daml.base.AssetTransferRequestProcessorBaseTest
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

class AssetTransferRequestProcessorTest extends CatsEffectSuite with AssetTransferRequestProcessorBaseTest {

  test("AssetTransferRequestProcessor should exercise AssetTransferRequest_Accept") {

    dummyStandardProcessor
      .processTransferRequestM(assetTransferRequest, assetTransferRequestContract)
      .map { x =>
        val command = x.collect(ju.stream.Collectors.toList()).get(0).asExerciseCommand().get()
        assertEquals(command.getChoice(), "AssetTransferRequest_Accept")
      }
  }

  test("AssetTransferRequestProcessor should exercise AssetTransferRequest_Reject") {

    dummyFailingWithException
      .processTransferRequestM(assetTransferRequest, assetTransferRequestContract)
      .map { x =>
        val command = x.collect(ju.stream.Collectors.toList()).get(0).asExerciseCommand().get()
        assertEquals(command.getChoice(), "AssetTransferRequest_Reject")
      }
  }

  test("AssetTransferRequestProcessor should return false if the error function returns false") {

    val event: data.Event =
      data.CreatedEvent.fromProto(
        CreatedEvent
          .newBuilder()
          .setTemplateId(AssetTransferRequest.TEMPLATE_ID.toProto())
          .setCreateArguments(assetTransferRequest.toValue().toProto().getRecord())
          .build()
      )
    val listOfEvents = new ju.ArrayList[Event]()
    listOfEvents.add(event.toProtoEvent())
    val tx = data.Transaction.fromProto(Transaction.newBuilder().addAllEvents(listOfEvents).build())

    dummyStandardProcessorWithErrorReturningTrue
      .processTransactionIO(tx)
      .map { x =>
        assertEquals(x, false)
      }
  }

  test("AssetTransferRequestProcessor should return false if the condition function return false") {

    val event =
      data.CreatedEvent.fromProto(
        CreatedEvent
          .newBuilder()
          .setTemplateId(AssetTransferRequest.TEMPLATE_ID.toProto())
          .setCreateArguments(assetTransferRequest.toValue().toProto().getRecord())
          .build()
      )
    dummyFailCondition
      .processEvent("xxx", event)
      .map { x =>
        assertEquals(x._1, false)
      }
  }

}
