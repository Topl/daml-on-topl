package co.topl.daml.algebras

import java.io.File

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.io.Source

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

/**
 * Algebra for asset specific operations.
 */
trait AssetSpecificOperationsAlgebra[Transfer[_ <: Proposition], F[_]] {

  /**
   * Parse a transaction from an array of bytes.
   *
   * @param tx The transaction encoded as an array of bytes
   * @return the actual transaction as an object.
   */
  def parseTxM(tx: Array[Byte]): F[Transfer[_ <: Proposition]]

  /**
   * Sign a transaction.
   *
   * @param rawTx the unsigned transaction
   * @return the signed transaction
   */
  def signTxM(rawTx: Transfer[_ <: Proposition]): F[Transfer[PublicKeyPropositionCurve25519]]

  def broadcastTransactionM(
    signedTx: Transfer[_ <: Proposition]
  ): F[ToplRpc.Transaction.BroadcastTx.Response]

  /**
   * Deserialize a transaction.
   *
   * @param transactionAsBytes The serialized transaction
   * @return the deserialized transaction
   */
  def deserializeTransactionM(transactionAsBytes: Array[Byte]): F[Transfer[_ <: Proposition]]

  /**
   * Encodes a transaction as a string.
   *
   * @param assetTransfer the transaction to encode
   * @return the string encoding the transaction.
   */
  def encodeTransferM(assetTransfer: Transfer[PublicKeyPropositionCurve25519]): F[String]

  def encodeTransferEd25519M(assetTransfer: Transfer[PublicKeyPropositionEd25519]): F[String]

}
