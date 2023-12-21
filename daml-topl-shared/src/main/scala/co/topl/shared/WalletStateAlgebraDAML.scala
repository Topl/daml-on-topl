package co.topl.shared

import scala.jdk.CollectionConverters._

import cats.data.ValidatedNel
import cats.effect.kernel.Async
import cats.implicits._
import co.topl.brambl.builders.locks.LockTemplate
import co.topl.brambl.dataApi.WalletStateAlgebra
import co.topl.brambl.models.Indices
import co.topl.brambl.models.box.Lock
import co.topl.brambl.utils.Encoding
import co.topl.daml.api.model.topl.wallet.CurrentInteractionSignatureIndexes
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.interop.reactivestreams._
import quivr.models.Preimage
import quivr.models.Proposition
import quivr.models.VerificationKey
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

object WalletStateAlgebraDAML {

  def make[F[_]: Async: Logger](dappParty: String, client: DamlLedgerClient) = new WalletStateAlgebra[F] {

    override def initWalletState(networkId: Int, ledgerId: Int, vk: VerificationKey): F[Unit] = ???

    override def getIndicesBySignature(signatureProposition: Proposition.DigitalSignature): F[Option[Indices]] =
      for {
        activeContractClient <- Async[F].delay(client.getActiveContractSetClient())
        currentInteractions <- Async[F].delay(
          activeContractClient.getActiveContracts(
            CurrentInteractionSignatureIndexes.contractFilter(),
            Set(dappParty).asJava,
            false
          )
        )
        currentInteractionList <- fromPublisher(currentInteractions, 1)(Async[F]).compile.toList.map(_.get(0).get)
        _ <- info"Vk encoded: ${Encoding.encodeToBase58(signatureProposition.verificationKey.toByteArray)}"
        someCurrentInteraction <- Async[F].delay(
          currentInteractionList.activeContracts
            .stream()
            .filter(_.data.vk == Encoding.encodeToBase58(signatureProposition.verificationKey.toByteArray))
            .findFirst()
        )
      } yield
        if (someCurrentInteraction.isPresent)
          Some(
            Indices(
              someCurrentInteraction.get().data.fellowshipIdx.toInt,
              someCurrentInteraction.get().data.lockTemplateIdx.toInt,
              someCurrentInteraction.get().data.interactionIdx.toInt
            )
          )
        else
          None

    override def getPreimage(digestProposition: Proposition.Digest): F[Option[Preimage]] = ???

    override def addPreimage(preimage: Preimage, digest: Proposition.Digest): F[Unit] = ???

    override def getCurrentAddress: F[String] = ???

    override def updateWalletState(
      lockPredicate: String,
      lockAddress:   String,
      routine:       Option[String],
      vk:            Option[String],
      indices:       Indices
    ): F[Unit] = ???

    override def getCurrentIndicesForFunds(
      fellowship:      String,
      template:        String,
      someInteraction: Option[Int]
    ): F[Option[Indices]] = ???

    override def getInteractionList(fellowship: String, template: String): F[Option[List[(Indices, String)]]] = ???

    override def setCurrentIndices(fellowship: String, template: String, interaction: Int): F[Option[Indices]] = ???

    override def validateCurrentIndicesForFunds(
      fellowship:      String,
      template:        String,
      someInteraction: Option[Int]
    ): F[ValidatedNel[String, Indices]] = ???

    override def getNextIndicesForFunds(fellowship: String, template: String): F[Option[Indices]] = ???

    override def getLockByIndex(indices: Indices): F[Option[Lock.Predicate]] = ???

    override def getLockByAddress(lockAddress: String): F[Option[Lock.Predicate]] = ???

    override def getAddress(fellowship: String, template: String, someInteraction: Option[Int]): F[Option[String]] = ???

    override def addEntityVks(fellowship: String, template: String, fellows: List[String]): F[Unit] = ???

    override def getEntityVks(fellowship: String, template: String): F[Option[List[String]]] = ???

    override def addNewLockTemplate(template: String, lockTemplate: LockTemplate[F]): F[Unit] = ???

    override def getLockTemplate(template: String): F[Option[LockTemplate[F]]] = ???

    override def getLock(fellowship: String, template: String, nextInteraction: Int): F[Option[Lock]] = ???

  }
}
