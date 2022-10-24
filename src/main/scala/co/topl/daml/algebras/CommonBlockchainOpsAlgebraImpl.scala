package co.topl.daml.algebras

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
import co.topl.utils.IdiomaticScalaTransition.implicits.toValidatedOps
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

trait CommonBlockchainOpsAlgebraImpl extends CommonBlockchainOpsAlgebra[IO] {

  val keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519] =
    KeyRing.empty[PrivateKeyCurve25519, KeyfileCurve25519]()(
      toplContext.provider.networkPrefix,
      PrivateKeyCurve25519.secretGenerator,
      KeyfileCurve25519Companion
    )

  val toplContext: ToplContext

  implicit val executionContext: ExecutionContext

  implicit val system: ActorSystem

  import toplContext.provider._

  def readFileM(fileName: String) =
    IO.blocking(Source.fromFile(new File(fileName)).getLines().mkString("").mkString)

  def importKeyM(jsonKey: Json, password: String, keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519]) =
    IO.fromEither(
      Brambl.importCurve25519JsonToKeyRing(jsonKey, password, keyRing).left.map(RpcClientFailureException(_))
    )

  def getBalanceM(param: ToplRpc.NodeView.Balances.Params) = for {
    eitherBalances <- IO.fromFuture(
      IO(ToplRpc.NodeView.Balances.rpc(param).leftMap(f => RpcClientFailureException(f)).value)
    )
    balance <- IO.fromEither(eitherBalances)
  } yield balance

  def computeValueM(
    fee:     Long,
    balance: ToplRpc.NodeView.Balances.Response
  ): IO[TokenValueHolder] = IO(
    SimpleValue(
      balance.values.map(_.Boxes.PolyBox.head.value.quantity).head - Int128(fee)
    )
  )

  def getParamsM(address: Seq[Address]) = IO(
    ToplRpc.NodeView.Balances
      .Params(
        address.toList
      )
  )

  def decodeAddressesM(addresses: List[String]) = IO(
    addresses.toSeq
      .map(Base58Data.unsafe)
      .map(_.decodeAddress.getOrThrow())
  )

  def decodeAddressM(address: String) = IO(
    Base58Data.unsafe(address).decodeAddress.getOrThrow()
  )

  def decodeTransactionM(tx: String) = IO(
    ByteVector
      .fromBase58(tx)
      .map(_.toArray)
      .getOrElse(throw new IllegalArgumentException())
  )

  def createLatinDataM(data: String) = IO(
    StringDataTypes.Latin1Data.fromData(
      utf8StringToLatin1ByteArray(data)
    )
  )

  def createCommitRootM(someCommitRoot: Option[String]) = IO(
    someCommitRoot
      .map(x => SecurityRoot.fromBase58(Base58Data.unsafe(x)))
      .getOrElse(SecurityRoot.empty)
  )

  def createMetadataM(someMetadata: Option[String]) =
    someMetadata
      .map(x => createLatinDataM(x))
      .orElse(None)
      .sequence

}