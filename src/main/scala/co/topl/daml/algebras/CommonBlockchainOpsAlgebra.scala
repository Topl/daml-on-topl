package co.topl.daml.algebras

import co.topl.attestation.PublicKeyPropositionCurve25519._
import co.topl.attestation._
import co.topl.attestation.keyManagement.KeyRing
import co.topl.attestation.keyManagement.KeyfileCurve25519
import co.topl.attestation.keyManagement.KeyfileCurve25519Companion
import co.topl.attestation.keyManagement.PrivateKeyCurve25519
import co.topl.daml.api.model.topl.asset.AssetTransferRequest
import co.topl.modifier.box.AssetCode
import co.topl.modifier.box.AssetValue
import co.topl.modifier.box.SecurityRoot
import co.topl.modifier.box.SimpleValue
import co.topl.modifier.box.TokenValueHolder
import co.topl.modifier.transaction.serialization.AssetTransferSerializer
import co.topl.rpc.ToplRpc
import co.topl.rpc.implicits.client._
import co.topl.utils.IdiomaticScalaTransition.implicits.toValidatedOps
import co.topl.utils.Int128
import co.topl.utils.StringDataTypes
import co.topl.utils.StringDataTypes.Base58Data
import io.circe.Json
import scodec.bits.ByteVector

import java.io.File
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.io.Source

trait CommonBlockchainOpsAlgebra[F[_]] {

  def readFileM(fileName: String): F[String]

  def importKeyM(jsonKey: Json, password: String, keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519]): F[Address]

  def getBalanceM(param: ToplRpc.NodeView.Balances.Params): F[ToplRpc.NodeView.Balances.Response]

  def computeValueM(
    fee:     Long,
    balance: ToplRpc.NodeView.Balances.Response
  ): F[TokenValueHolder]

  def getParamsM(address: Seq[Address]): F[ToplRpc.NodeView.Balances.Params]

  def decodeAddressesM(addresses: List[String]): F[List[Address]]

  def decodeAddressM(address: String): F[Address]

  def decodeTransactionM(tx: String): F[Array[Byte]]

  def createLatinDataM(data: String): F[StringDataTypes.Latin1Data]

  def createCommitRootM(someCommitRoot: Option[String]): F[SecurityRoot]

  def createMetadataM(someMetadata: Option[String]): F[Option[StringDataTypes.Latin1Data]]

}
