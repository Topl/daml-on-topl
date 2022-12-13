import co.topl.rpc.ToplRpc
import cats.effect.IO
import co.topl.modifier.box.TokenValueHolder
import co.topl.modifier.box.SimpleValue
import co.topl.utils.Int128
import co.topl.rpc.implicits.client._
import scala.concurrent.ExecutionContext
import co.topl.attestation.AddressCodec.implicits._
import co.topl.attestation.PublicKeyPropositionCurve25519._
import co.topl.attestation._
import co.topl.akkahttprpc.implicits.client._
import akka.actor.ActorSystem
import co.topl.utils.StringDataTypes.Base58Data
import co.topl.utils.StringDataTypes
import co.topl.modifier.box.SecurityRoot
import cats.syntax.traverse._
import co.topl.modifier.transaction.AssetTransfer
import scala.collection.immutable.ListMap
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import scodec.bits.ByteVector
import co.topl.attestation.keyManagement.KeyRing
import co.topl.attestation.keyManagement.KeyfileCurve25519
import co.topl.attestation.keyManagement.KeyfileCurve25519.jsonEncoder
import co.topl.attestation.keyManagement.KeyfileCurve25519Companion
import co.topl.attestation.keyManagement.PrivateKeyCurve25519
import scala.io.Source
import java.io.File
import co.topl.client.Brambl
import io.circe.Json
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import co.topl.modifier.transaction.PolyTransfer
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import co.topl.modifier.box.AssetValue
import co.topl.modifier.box.AssetCode
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import co.topl.daml.ToplContext
import co.topl.daml.RpcClientFailureException
import co.topl.daml.utf8StringToLatin1ByteArray
import co.topl.modifier.ModifierId
import akka.http.scaladsl.model.Uri;
import co.topl.client.Provider;

val uri = Uri("http://localhost:9085/");

val provider = new Provider.PrivateTestNet(uri, "")

implicit val networkPrefix = provider.networkPrefix

val keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519] =
  KeyRing.empty[PrivateKeyCurve25519, KeyfileCurve25519]()(
    provider.networkPrefix,
    PrivateKeyCurve25519.secretGenerator,
    KeyfileCurve25519Companion
  )

val keyPair = keyRing.generateNewKeyPairs(1, None)

val res: KeyfileCurve25519 = Brambl.generateNewCurve25519Keyfile("test2", keyRing) match {
  case Right(res) => res
  case Left(_)    => throw new IllegalStateException
}

import io.circe.syntax._
res.asJson.toString()
