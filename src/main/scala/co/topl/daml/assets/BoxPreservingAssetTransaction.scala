package co.topl.daml.assets

import co.topl.attestation.Address
import co.topl.attestation.EvidenceProducer
import co.topl.attestation.Proof
import co.topl.attestation.Proposition
import co.topl.crypto.hash.blake2b256
import co.topl.modifier.box.Box
import co.topl.modifier.box.SimpleValue
import co.topl.modifier.box.TokenBox
import co.topl.modifier.box.TokenValueHolder
import co.topl.modifier.transaction.Transaction
import co.topl.modifier.transaction.TransferTransaction
import co.topl.utils.Identifiable
import co.topl.utils.Int128
import co.topl.utils.StringDataTypes.Latin1Data
import com.google.common.primitives.Ints
import com.google.common.primitives.Longs

import scala.collection.immutable.ListMap
import co.topl.modifier.box.AssetValue
import co.topl.modifier.transaction.AssetTransfer
import co.topl.modifier.box.AssetBox
import co.topl.modifier.box.PolyBox

class BoxPreservingAssetTransaction[
  P <: Proposition: EvidenceProducer: Identifiable
](
  override val from:        IndexedSeq[(Address, Box.Nonce)],
  override val to:          IndexedSeq[(Address, TokenValueHolder)],
  override val attestation: ListMap[P, Proof[P]],
  override val fee:         Int128,
  override val timestamp:   Long,
  override val data:        Option[Latin1Data] = None,
  override val minting:     Boolean = false,
  val mintedAsset:          AssetValue,
  val previousAssets:       IndexedSeq[(Address, TokenValueHolder)]
) extends AssetTransfer(from, to, attestation, fee, timestamp, data, minting) {

  val (asseetFeeOutputParams, assetCoinOutputParams) =
    calculateBoxNonce[TokenValueHolder](mintedAsset, previousAssets)

  val assetCoinOutput: Iterable[AssetBox] =
    assetCoinOutputParams.map {
      case TransferTransaction.BoxParams(evi, nonce, value: AssetValue) =>
        AssetBox(evi, nonce, value)
      case TransferTransaction.BoxParams(_, _, value) =>
        throw new IllegalArgumentException(s"AssetTransfer Coin output params contained invalid value=$value")
    }

  val assetFeeChangeOutput: PolyBox =
    PolyBox(asseetFeeOutputParams.evidence, asseetFeeOutputParams.nonce, asseetFeeOutputParams.value)

  override val newBoxes: Iterable[TokenBox[TokenValueHolder]] = {
    // this only creates an output if the value of the output boxes is non-zero
    val recipientCoinOutput: Iterable[AssetBox] = assetCoinOutput.filter(_.value.quantity > 0)
    val hasRecipientOutput: Boolean = recipientCoinOutput.nonEmpty
    val hasFeeChangeOutput: Boolean = assetFeeChangeOutput.value.quantity > 0

    (hasRecipientOutput, hasFeeChangeOutput) match {
      case (false, _)    => Iterable()
      case (true, false) => recipientCoinOutput
      case (true, true)  => Iterable(assetFeeChangeOutput) ++ recipientCoinOutput
    }
  }

  /**
   * Computes a unique nonce value based on the transaction type and
   * inputs and returns the details needed to create the output boxes for the transaction
   */
  def calculateBoxNonce[T <: TokenValueHolder](
    pMintedAsset: T,
    to:           IndexedSeq[(Address, T)]
  ): (TransferTransaction.BoxParams[SimpleValue], Iterable[TransferTransaction.BoxParams[T]]) = {

    // known input data (similar to messageToSign but without newBoxes since they aren't known yet)
    val txIdPrefix = Transaction.identifier(this).typePrefix
    val boxIdsToOpenAccumulator = this.boxIdsToOpen.foldLeft(Array[Byte]())((acc, x) => acc ++ x.hash.value)
    val timestampBytes = Longs.toByteArray(this.timestamp)
    val feeBytes = this.fee.toByteArray

    val inputBytes =
      Array(txIdPrefix) ++ boxIdsToOpenAccumulator ++ timestampBytes ++ feeBytes

    val calcNonce: Int => Box.Nonce = (index: Int) => {
      val digest = blake2b256.hash(inputBytes ++ Ints.toByteArray(index))
      Transaction.nonceFromDigest(digest)
    }

    val feeChangeParams =
      TransferTransaction.BoxParams(this.to.head._1.evidence, calcNonce(0), SimpleValue(this.to.head._2.quantity))

    val coinOutputParams: IndexedSeq[TransferTransaction.BoxParams[T]] =
      (to
        .appended((this.to(0)._1, (pMintedAsset))))
        .zipWithIndex
        .map { case ((addr, value), idx) =>
          TransferTransaction.BoxParams(addr.evidence, calcNonce(idx + 1), value)
        }
    (feeChangeParams, coinOutputParams)
  }

}
