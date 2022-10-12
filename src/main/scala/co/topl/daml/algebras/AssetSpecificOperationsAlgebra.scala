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

trait AssetSpecificOperationsAlgebra[Transfer[_ <: Proposition], F[_]] {

  def parseTxM(msg2Sign: Array[Byte]): F[Transfer[_ <: Proposition]]

  def signTxM(rawTx: Transfer[_ <: Proposition]): F[Transfer[PublicKeyPropositionCurve25519]]

  def broadcastTransactionM(
    signedTx: Transfer[_ <: Proposition]
  ): F[ToplRpc.Transaction.BroadcastTx.Response]

  def deserializeTransactionM(transactionAsBytes: Array[Byte]): F[Transfer[_ <: Proposition]]

  def encodeTransferM(assetTransfer: Transfer[PublicKeyPropositionCurve25519]): F[String]

}
