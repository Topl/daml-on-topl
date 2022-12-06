package co.topl.daml.algebras

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
import co.topl.rpc.ToplRpc
import co.topl.rpc.ToplRpc.Transaction.RawPolyTransfer
import co.topl.rpc.implicits.client._
import cats.data.{EitherT, NonEmptyChain}

import co.topl.attestation.AddressCodec.implicits._
import scala.collection.JavaConverters._

import java.io.File
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext
import scala.io.Source
import cats.effect.IO
import co.topl.modifier.transaction.PolyTransfer
import co.topl.modifier.transaction.serialization.PolyTransferSerializer
import co.topl.akkahttprpc.implicits.client._
import co.topl.modifier.transaction.builder.BoxSelectionAlgorithms
import scala.concurrent.Future
import cats.arrow.FunctionK
import co.topl.modifier.transaction.AssetTransfer
import co.topl.daml.RpcClientFailureException
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import co.topl.modifier.ModifierId

trait AssetOperationsAlgebra
    extends AssetSpecificOperationsAlgebra[AssetTransfer, IO]
    with CommonBlockchainOpsAlgebraImpl {

  trait TransferEv[T] {

    def issuerAddress(t:    T): String
    def shortName(t:        T): String
    def someCommitRoot(t:   T): Option[String]
    def someMetadata(t:     T): Option[String]
    def assetCodeVersion(t: T): Byte

  }

  // evidence for AssetMintingRequest
  val assetMintingRequestEv = new TransferEv[AssetMintingRequest] {

    def issuerAddress(assetMintingRequest: AssetMintingRequest): String = assetMintingRequest.assetCode.issuerAddress
    def shortName(assetMintingRequest: AssetMintingRequest): String = assetMintingRequest.assetCode.shortName

    def someCommitRoot(assetMintingRequest: AssetMintingRequest): Option[String] =
      Option(assetMintingRequest.someCommitRoot).flatMap(x => Option(x.orElseGet(() => null)))

    def someMetadata(assetMintingRequest: AssetMintingRequest): Option[String] =
      Option(assetMintingRequest.someMetadata).flatMap(x => Option(x.orElseGet(() => null)))
    def assetCodeVersion(t: AssetMintingRequest): Byte = t.assetCode.version.toByte
  }

  // evidence for AssetTransferRequest
  val assetTransferRequestEv = new TransferEv[AssetTransferRequest] {

    def issuerAddress(assetMintingRequest: AssetTransferRequest): String = assetMintingRequest.assetCode.issuerAddress

    def shortName(assetMintingRequest: AssetTransferRequest): String = assetMintingRequest.assetCode.shortName

    def someCommitRoot(assetMintingRequest: AssetTransferRequest): Option[String] =
      Option(assetMintingRequest.someCommitRoot).flatMap(x => Option(x.orElseGet(() => null)))

    def someMetadata(assetMintingRequest: AssetTransferRequest): Option[String] =
      Option(assetMintingRequest.someMetadata).flatMap(x => Option(x.orElseGet(() => null)))
    def assetCodeVersion(t: AssetTransferRequest): Byte = t.assetCode.version.toByte
  }

  import toplContext.provider._

  def parseTxM(msg2Sign: Array[Byte]) = IO.fromEither {
    import io.circe.parser._
    import co.topl.modifier.transaction.AssetTransfer.jsonDecoder
    parse(new String(msg2Sign)).flatMap(jsonDecoder.decodeJson)
  }

  def signTxM(rawTx: AssetTransfer[_ <: Proposition]) = IO {
    val signFunc = (addr: Address) => keyRing.generateAttestation(addr)(rawTx.messageToSign)
    val signatures = keyRing.addresses.map(signFunc).reduce(_ ++ _)
    rawTx.copy(attestation = signatures)
  }

  def broadcastTransactionM(
    signedTx: AssetTransfer[_ <: Proposition]
  ) = for {
    eitherBroadcast <- IO.fromFuture(
      IO(
        ToplRpc.Transaction.BroadcastTx.rpc(ToplRpc.Transaction.BroadcastTx.Params(signedTx)).value
      )
    )
    broadcast <- IO.fromEither(eitherBroadcast.left.map(x => new RpcClientFailureException(x)))
  } yield broadcast

  def deserializeTransactionM(transactionAsBytes: Array[Byte]) = IO.fromEither {
    import io.circe.parser._
    import co.topl.modifier.transaction.AssetTransfer.jsonDecoder
    parse(new String(transactionAsBytes)).flatMap(jsonDecoder.decodeJson)
  }

  def encodeTransferM(assetTransfer: AssetTransfer[PublicKeyPropositionCurve25519]) = for {
    transferRequest <- IO {
      import io.circe.syntax._
      // import co.topl.modifier.transaction.AssetTransfer.jsonEncoder
      assetTransfer.asJson.noSpaces
    }
    // encodedTx <- IO(
    //   ByteVector(
    //     transferRequest
    //   ).toBase58
  } yield transferRequest

  def encodeTransferEd25519M(assetTransfer: AssetTransfer[PublicKeyPropositionEd25519]) = for {
    transferRequest <- IO {
      import io.circe.syntax._
      // import co.topl.modifier.transaction.AssetTransfer.jsonEncoder
      assetTransfer.asJson.noSpaces
    }
    // encodedTx <- IO(
    //   ByteVector(
    //     transferRequest
    //   ).toBase58
  } yield transferRequest

  def createAssetEd25519TransferM(
    fee:               Long,
    someBoxNonce:      Option[Long],
    address:           Seq[Address],
    balance:           ToplRpc.NodeView.Balances.Response,
    listOfToAddresses: List[(Address, TokenValueHolder)]
  ) = {
    import co.topl.attestation.PublicKeyPropositionEd25519._
    IO(
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

  def createAssetTransferM(
    fee:               Long,
    someBoxNonce:      Option[Long],
    address:           Seq[Address],
    balance:           ToplRpc.NodeView.Balances.Response,
    listOfToAddresses: List[(Address, TokenValueHolder)]
  ) = {
    import co.topl.attestation.PublicKeyPropositionCurve25519._
    IO(
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

  def createToParamM[T: TransferEv](
    assetTransferRequest: T
  )(address:              String, amount: Long): IO[(Address, TokenValueHolder)] = {
    val ev = implicitly[TransferEv[T]]
    for {
      address       <- decodeAddressM(address)
      issuerAddress <- decodeAddressM(ev.issuerAddress(assetTransferRequest))
      latinData     <- createLatinDataM(ev.shortName(assetTransferRequest))
      commitRoot    <- createCommitRootM(ev.someCommitRoot(assetTransferRequest))
      someMetadata  <- createMetadataM(ev.someMetadata(assetTransferRequest))
    } yield (
      address,
      AssetValue(
        amount,
        AssetCode(
          ev.assetCodeVersion(assetTransferRequest),
          issuerAddress,
          latinData
        ),
        commitRoot,
        someMetadata
      )
    )
  }

}
