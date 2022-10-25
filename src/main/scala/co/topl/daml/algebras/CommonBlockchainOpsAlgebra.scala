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

/**
 * Models common operations used in the blockchain.
 */
trait CommonBlockchainOpsAlgebra[F[_]] {

  /**
   * Read a file.
   *
   * @param fileName the name of the file.
   * @return the file contents as a string.
   */
  def readFileM(fileName: String): F[String]

  /**
   * Imports the key from the keyfile.
   *
   * @param jsonKey The file parsed as a Json datatype
   * @param password the password of the keyfile
   * @param keyRing the in memory keyring
   * @return the address of the keyfile
   */
  def importKeyM(jsonKey: Json, password: String, keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519]): F[Address]

  /**
   * Gets the balance for an address in the blockchain.
   *
   * @param param the list of addresses for which the balance will be computed
   * @return the balance of the address
   */
  def getBalanceM(param: ToplRpc.NodeView.Balances.Params): F[ToplRpc.NodeView.Balances.Response]

  /**
   * Computes the value that will be added to a given transaction.
   *
   * @param fee the fee to pay
   * @param balance the response from the balance service
   * @return the value that will be used in the transaction.
   */
  def computeValueM(
    fee:     Long,
    balance: ToplRpc.NodeView.Balances.Response
  ): F[TokenValueHolder]

  def getTransactionConfirmationStatusM(
    transactionId: String
  ): F[ToplRpc.NodeView.ConfirmationStatus.Response]

  /**
   * Computes the parameter por the balances service
   *
   * @param address the list of addresses
   * @return the parameters for the balances service
   */
  def getParamsM(address: Seq[Address]): F[ToplRpc.NodeView.Balances.Params]

  /**
   * Transforms a list of addresses encoded as strings to a list of addresses
   * encapsulted in an object
   *
   * @param addresses the list of addresses as string
   * @return the list of decoded addresses
   */
  def decodeAddressesM(addresses: List[String]): F[List[Address]]

  /**
   * Decodes a string to an address
   *
   * @param address the address encoded as string
   * @return the decoded address
   */
  def decodeAddressM(address: String): F[Address]

  /**
   * Decodes a transaction encoded as string to an array of bytes.
   *
   * @param tx the transaction to be decoded
   * @return an array of bytes encoded as a transaction
   */
  def decodeTransactionM(tx: String): F[Array[Byte]]

  /**
   * Encodes a string as Latin1Data.
   *
   * @param data the string
   * @return the encoded string
   */
  def createLatinDataM(data: String): F[StringDataTypes.Latin1Data]

  /**
   * Encodes a string as a security root. No validation is done,
   * might fail with an exception.
   *
   * @param someCommitRoot the string to be commited.
   * @return the SecurityRoot
   */
  def createCommitRootM(someCommitRoot: Option[String]): F[SecurityRoot]

  /**
   * Encodes some metadata as Latin1Data.
   *
   * @param someMetadata the metadata
   * @return the encoded metadata
   */
  def createMetadataM(someMetadata: Option[String]): F[Option[StringDataTypes.Latin1Data]]

}
