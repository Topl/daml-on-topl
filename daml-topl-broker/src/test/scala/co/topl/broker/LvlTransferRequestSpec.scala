package co.topl.broker

import scala.jdk.CollectionConverters._

import cats.effect.IO
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.constants.NetworkConstants
import co.topl.brambl.models.transaction.IoTransaction
import co.topl.brambl.utils.Encoding
import co.topl.daml.api.model.topl.levels.LvlTransferRequest
import co.topl.daml.api.model.topl.levels.LvlTransferUnproved
import co.topl.daml.api.model.topl.levels.Recipient
import co.topl.daml.api.model.topl.levels.SendAddress
import com.daml.ledger.api.v1.EventOuterClass.{CreatedEvent => CreatedEventProto}
import com.daml.ledger.javaapi.data.CreatedEvent
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jFactory

class LvlTransferRequestSpec extends CatsEffectSuite with DummyObjects {

  test("processLvlTransferRequest should only work for LvlTransferRequest contracts") {
    implicit val genus = makeGenusQueryAlgebraMockWithAddress[IO]
    implicit val logging = Slf4jFactory.create[IO]
    implicit val asyncForIO = IO.asyncForIO
    implicit val logger: Logger[IO] = logging.getLogger

    implicit val transactionBuilderApi = TransactionBuilderApi.make[IO](
      NetworkConstants.MAIN_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID
    )
    assertIO(
      LvlTransferRequestModule.processLvlTransferRequest[IO](
        BrokerCLIParamConfig(),
        CreatedEvent.fromProto(
          CreatedEventProto
            .newBuilder()
            .setTemplateId(LvlTransferUnproved.TEMPLATE_ID.toProto())
            .setCreateArguments(
              (new LvlTransferRequest(
                "1",
                "requestor",
                "operator",
                new SendAddress("from", "to"),
                new Recipient("address", 100),
                10,
                "lockProposition",
                "changeAddress",
                List().asJava
              )).toValue().toProtoRecord()
            )
            .build()
        )
      ),
      None
    )
  }

  test("processLvlTransferRequest should should generate an exercise LvlTransferRequest_Accept") {
    implicit val genus = makeGenusQueryAlgebraMockWithOneAddress[IO]
    implicit val logging = Slf4jFactory.create[IO]
    implicit val asyncForIO = IO.asyncForIO
    implicit val logger: Logger[IO] = logging.getLogger

    implicit val transactionBuilderApi = TransactionBuilderApi.make[IO](
      NetworkConstants.PRIVATE_NETWORK_ID,
      NetworkConstants.MAIN_LEDGER_ID
    )
    val evt = CreatedEvent.fromProto(
      CreatedEventProto
        .newBuilder()
        .setTemplateId(LvlTransferRequest.TEMPLATE_ID.toProto())
        .setCreateArguments(
          (new LvlTransferRequest(
            "1",
            "requestor",
            "operator",
            new SendAddress("ptetP7jshHVqDp4Dcc4gWsBzG7o7yLh2kt5aKdY7e12xrqnUfXZm5jtvHXeR", "to"),
            new Recipient("ptetP7jshHUqDhjMhP88yhtQhhvrnBUVJkSvEo5xZvHE4UDL9FShTf1YBqSU", 10),
            10,
            "bfE1m27tJ1xxC6LV2Rah5ccDipmLrrNpDqhv3egccq5rte9w9G5eURQViUj7LEv4HNjtmHRMX71CVtPosHuRoAkPrMLvu1E2tCwiyD4zXy5Hu29Hm3fYsETJHu3VJiTG2mBhbJicWUBD",
            "ptetP7jshHUqDhjMhP88yhtQhhvrnBUVJkSvEo5xZvHE4UDL9FShTf1YBqSU",
            List().asJava
          )).toValue().toProtoRecord()
        )
        .build()
    )
    val result = LvlTransferRequestModule
      .processLvlTransferRequest[IO](
        BrokerCLIParamConfig(),
        evt
      )
      .map(
        _.flatMap(
          _.getCommands().asScala.toList.headOption
            .flatMap(_.commands().asScala.toList.headOption.map(_.asExerciseCommand().get()))
        )
      )
    assertIO(
      result.map(_.map(_.getChoice())),
      Some(
        "LvlTransferRequest_Accept"
      )
    )
    assertIO(
      result
        .map(
          _.map(
            _.getChoiceArgument()
              .asRecord()
              .get()
              .getFieldsMap()
              .get("unprovedTx")
              .asText()
              .get()
              .getValue()
          )
        )
        .map(x =>
          IoTransaction
            .parseFrom(x.map(Encoding.decodeFromBase58(_).toOption.get).get)
            .inputs
            .head
            .address
            .id
            .value
            .toByteArray()
        )
        .map(x => Some(Encoding.encodeToBase58(x))),
      Some(
        "DAas2fmY1dfpVkTYSJXp3U1CD7yTMEonum2xG9BJmNtQ"
        // Encoding.encodeToBase58(transactionId01.value.toByteArray())
      )
    )
  }
}
