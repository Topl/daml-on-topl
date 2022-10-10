package co.topl.daml

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

trait CommonOperations {

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

  def parseTxM(msg2Sign: Array[Byte]) = IO.fromTry(AssetTransferSerializer.parseBytes(msg2Sign))

  def signTxM(rawTx: PolyTransfer[_ <: Proposition]) = IO {
    val signFunc = (addr: Address) => keyRing.generateAttestation(addr)(rawTx.messageToSign)
    val signatures = keyRing.addresses.map(signFunc).reduce(_ ++ _)
    rawTx.copy(attestation = signatures)
  }

  def signTxM(rawTx: AssetTransfer[_ <: Proposition]) = IO {
    val signFunc = (addr: Address) => keyRing.generateAttestation(addr)(rawTx.messageToSign)
    val signatures = keyRing.addresses.map(signFunc).reduce(_ ++ _)
    rawTx.copy(attestation = signatures)
  }

  def readFileM(fileName: String) =
    IO.blocking(Source.fromFile(new File(fileName)).getLines().mkString("").mkString)

  def importKeyM(jsonKey: Json, password: String, keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519]) =
    IO.fromEither(
      Brambl.importCurve25519JsonToKeyRing(jsonKey, password, keyRing).left.map(RpcClientFailureException(_))
    )

  def broadcastTransactionM(
    signedTx: AssetTransfer[_ <: Proposition]
  ) =
    IO.fromFuture(
      IO(
        ToplRpc.Transaction.BroadcastTx.rpc(ToplRpc.Transaction.BroadcastTx.Params(signedTx)).value
      )
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

  def deserializeTransactionM(transactionAsBytes: Array[Byte]) = IO.fromTry(
    AssetTransferSerializer
      .parseBytes(transactionAsBytes)
  )

  def encodeTransferM(assetTransfer: AssetTransfer[PublicKeyPropositionCurve25519]) = for {
    transferRequest <- IO(
      AssetTransferSerializer.toBytes(
        assetTransfer
      )
    )
    encodedTx <- IO(
      ByteVector(
        transferRequest
      ).toBase58
    )
  } yield encodedTx

  def encodeTransferM(assetTransfer: PolyTransfer[PublicKeyPropositionCurve25519]) = for {
    transferRequest <- IO(
      PolyTransferSerializer.toBytes(
        assetTransfer
      )
    )
    encodedTx <- IO(
      ByteVector(
        transferRequest
      ).toBase58
    )
  } yield encodedTx

  def createAssetTransferM(
    fee:               Long,
    someBoxNonce:      Option[Long],
    address:           Seq[Address],
    balance:           ToplRpc.NodeView.Balances.Response,
    listOfToAddresses: List[(Address, TokenValueHolder)]
  ) = IO(
    AssetTransfer(
      someBoxNonce
        .map(boxNonce =>
          balance.values.toList
            .flatMap(_.Boxes.PolyBox)
            .map(x => (address.head, x.nonce))
            .toIndexedSeq
            .++(
              balance.values.toList
                .flatMap(_.Boxes.AssetBox)
                .filter(_.nonce == boxNonce)
                .map(x => (address.head, x.nonce))
                .toIndexedSeq
            )
        )
        .getOrElse(balance.values.toList.flatMap(_.Boxes.PolyBox).map(x => (address.head, x.nonce)).toIndexedSeq),
      listOfToAddresses.toIndexedSeq,
      ListMap(),
      Int128(
        fee
      ),
      System.currentTimeMillis(),
      None,
      someBoxNonce.isEmpty // no box nonce means minting
    )
  )

}
