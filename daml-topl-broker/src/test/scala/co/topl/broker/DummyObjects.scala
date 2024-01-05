package co.topl.broker


import cats.Monad
import co.topl.brambl.codecs.AddressCodecs
import co.topl.brambl.dataApi.GenusQueryAlgebra
import co.topl.brambl.models.Datum
import co.topl.brambl.models.GroupId
import co.topl.brambl.models.LockAddress
import co.topl.brambl.models.SeriesId
import co.topl.brambl.models.TransactionId
import co.topl.brambl.models.TransactionOutputAddress
import co.topl.brambl.models.box.Attestation
import co.topl.brambl.models.box.Challenge
import co.topl.brambl.models.box.FungibilityType
import co.topl.brambl.models.box.Lock
import co.topl.brambl.models.box.QuantityDescriptorType
import co.topl.brambl.models.box.Value
import co.topl.brambl.models.transaction.SpentTransactionOutput
import co.topl.brambl.models.transaction.UnspentTransactionOutput
import co.topl.brambl.utils.Encoding
import co.topl.consensus.models.BlockId
import co.topl.genus.services.Txo
import co.topl.genus.services.TxoState
import co.topl.node.models.BlockBody
import com.google.protobuf.ByteString
import quivr.models.Int128
import quivr.models.Proposition
import co.topl.consensus.models.BlockHeader
import co.topl.consensus.models.EligibilityCertificate
import co.topl.consensus.models.OperationalCertificate
import co.topl.consensus.models.StakingAddress
import co.topl.consensus.models.ProtocolVersion
import co.topl.consensus.models.VerificationKeyKesProduct
import co.topl.consensus.models.SignatureKesProduct
import co.topl.consensus.models.SignatureKesSum

trait DummyObjects {

  import co.topl.brambl.syntax._

  lazy val transactionId01 = TransactionId(
    ByteString.copyFrom(
      Encoding
        .decodeFromBase58("DAas2fmY1dfpVkTYSJXp3U1CD7yTMEonum2xG9BJmNtQ")
        .toOption
        .get
    )
  )
  // corresponds to the address of the lockAddress01
  val lock01 = Lock.Predicate.of(
    Seq(
      Challenge.defaultInstance.withProposition(
        Challenge.Proposition.Revealed(
          Proposition.of(
            Proposition.Value.Locked(Proposition.Locked())
          )
        )
      )
    ),
    1
  )

  lazy val lockAddress01 = AddressCodecs
    .decodeAddress(
      "ptetP7jshHVqDp4Dcc4gWsBzG7o7yLh2kt5aKdY7e12xrqnUfXZm5jtvHXeR"
    )
    .toOption
    .get

  lazy val lvlValue01 = Value(
    Value.Value.Lvl(
      Value.LVL(
        Int128(ByteString.copyFrom(BigInt(100L).toByteArray))
      )
    )
  )
  lazy val groupValue01 = Value(
    Value.Value.Group(
      Value.Group(
        GroupId(
          ByteString.copyFrom(
            Encoding
              .decodeFromHex(
                "fdae7b6ea08b7d5489c3573abba8b1765d39365b4e803c4c1af6b97cf02c54bf"
              )
              .toOption
              .get
          )
        ),
        1L,
        None
      )
    )
  )

  lazy val seriesValue01 = Value(
    Value.Value.Series(
      Value.Series(
        SeriesId(
          ByteString.copyFrom(
            Encoding
              .decodeFromHex(
                "1ed1caaefda61528936051929c525a17a0d43ea6ae09592da06c9735d9416c03"
              )
              .toOption
              .get
          )
        ),
        1L,
        None,
        QuantityDescriptorType.LIQUID,
        FungibilityType.GROUP_AND_SERIES
      )
    )
  )
  lazy val assetValue01 = Value(
    Value.Asset(
      Some(
        GroupId(
          ByteString.copyFrom(
            Encoding
              .decodeFromHex(
                "fdae7b6ea08b7d5489c3573abba8b1765d39365b4e803c4c1af6b97cf02c54bf"
              )
              .toOption
              .get
          )
        )
      ),
      Some(
        SeriesId(
          ByteString.copyFrom(
            Encoding
              .decodeFromHex(
                "1ed1caaefda61528936051929c525a17a0d43ea6ae09592da06c9735d9416c03"
              )
              .toOption
              .get
          )
        )
      ),
      1L
    )
  )

  lazy val transactionOutputAddress01 = TransactionOutputAddress(
    lockAddress01.network,
    lockAddress01.ledger,
    1,
    transactionId01
  )

  lazy val transactionOutputAddress02 = TransactionOutputAddress(
    lockAddress01.network,
    lockAddress01.ledger,
    2,
    transactionId01
  )

  lazy val transactionOutputAddress03 = TransactionOutputAddress(
    lockAddress01.network,
    lockAddress01.ledger,
    3,
    transactionId01
  )

  lazy val txo01 = Txo(
    UnspentTransactionOutput(
      lockAddress01,
      lvlValue01
    ),
    co.topl.genus.services.TxoState.UNSPENT,
    transactionOutputAddress01
  )

  lazy val txo02 = Txo(
    UnspentTransactionOutput(
      lockAddress01,
      groupValue01
    ),
    co.topl.genus.services.TxoState.UNSPENT,
    transactionOutputAddress02
  )

  lazy val txo03 = Txo(
    UnspentTransactionOutput(
      lockAddress01,
      seriesValue01
    ),
    co.topl.genus.services.TxoState.UNSPENT,
    transactionOutputAddress03
  )

  lazy val txo04 = Txo(
    UnspentTransactionOutput(
      lockAddress01,
      assetValue01
    ),
    co.topl.genus.services.TxoState.UNSPENT,
    transactionOutputAddress03
  )

  lazy val blockId01 = BlockId(
    ByteString.copyFrom(
      Array.fill[Byte](32)(0)
    )
  )

  lazy val blockHeader01 = BlockHeader(
    headerId = Some(blockId01),
    parentHeaderId = blockId01,
    txRoot = ByteString.copyFrom(Array.fill[Byte](32)(0)),
    bloomFilter = ByteString.copyFrom(Array.fill[Byte](256)(0)),
    eligibilityCertificate = EligibilityCertificate(
      vrfSig = ByteString.copyFrom(Array.fill[Byte](80)(0)),
      vrfVK = ByteString.copyFrom(Array.fill[Byte](32)(0)),
      eta = ByteString.copyFrom(Array.fill[Byte](32)(0)),
      thresholdEvidence = ByteString.copyFrom(Array.fill[Byte](32)(0))
    ),
    operationalCertificate = OperationalCertificate(
      VerificationKeyKesProduct(value =
        ByteString.copyFrom(Array.fill[Byte](32)(0))
      ),
      SignatureKesProduct(
        SignatureKesSum(
          verificationKey = ByteString.copyFrom(Array.fill[Byte](32)(0)),
          signature = ByteString.copyFrom(Array.fill[Byte](64)(0))
        ),
        SignatureKesSum(
          verificationKey = ByteString.copyFrom(Array.fill[Byte](32)(0)),
          signature = ByteString.copyFrom(Array.fill[Byte](64)(0))
        ),
        subRoot = ByteString.copyFrom(Array.fill[Byte](32)(0))
      ),
      ByteString.copyFrom(Array.fill[Byte](32)(0)),
      ByteString.copyFrom(Array.fill[Byte](64)(0)),
    ),
    address = StakingAddress(
      value = ByteString.copyFrom(Array.fill[Byte](32)(0))
    ),
    version = ProtocolVersion()
  )

  lazy val blockBody01 = BlockBody(
    Seq(transactionId01)
  )

  lazy val stxo01 = SpentTransactionOutput(
    transactionOutputAddress01,
    Attestation(Attestation.Value.Empty),
    lvlValue01
  )

  lazy val utxo01 = UnspentTransactionOutput(
    lockAddress01,
    lvlValue01
  )

  lazy val iotransaction01 = co.topl.brambl.models.transaction
    .IoTransaction(
      Some(transactionId01),
      Seq(stxo01),
      Seq(utxo01),
      Datum.IoTransaction.defaultInstance
    )

  def makeGenusQueryAlgebraMockWithAddress[F[_]: Monad] =
    new GenusQueryAlgebra[F] {

      override def queryUtxo(
          fromAddress: LockAddress,
          txoState: TxoState
      ): F[Seq[Txo]] = {
        Monad[F].pure(
          Seq(txo01, txo02, txo03, txo04)
        )
      }
    }
  def makeGenusQueryAlgebraMockWithOneAddress[F[_]: Monad] =
    new GenusQueryAlgebra[F] {

      override def queryUtxo(
          fromAddress: LockAddress,
          txoState: TxoState
      ): F[Seq[Txo]] = {
        Monad[F].pure(
          Seq(txo01)
        )
      }
    }

}
