import scala.concurrent.Await
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import co.topl.client.Provider
import co.topl.utils.StringDataTypes
import co.topl.attestation.Address
import co.topl.rpc.ToplRpc
import co.topl.attestation.AddressCodec.implicits._
import cats.implicits._
import co.topl.akkahttprpc.implicits.client._
import co.topl.rpc.implicits.client._

import io.circe.syntax._

val uri = Uri("http://localhost:9085");

val provider = new Provider.PrivateTestNet(uri, "")

import provider._

implicit val actorSystem = ActorSystem()
implicit val ec = actorSystem.dispatcher

val params = ToplRpc.NodeView.Balances.Params(
  List(
    StringDataTypes.Base58Data
      .unsafe("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64")
      .decodeAddress
      .getOrElse(throw new IllegalArgumentException())
  )
)

val rpcCall = ToplRpc.NodeView.Balances.rpc(params)

import scala.concurrent.duration._
val res = Await.result(rpcCall.value, 3.second)

res.fold(
  fa => println("Failure"),
  fb =>
    // println("Success!")
    fb.values.map(_.Boxes.AssetBox.map(_.nonce))
)
