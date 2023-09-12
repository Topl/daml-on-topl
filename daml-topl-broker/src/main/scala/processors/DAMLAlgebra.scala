package processors

import com.daml.ledger.javaapi.data.Transaction
import com.daml.ledger.javaapi.data.Command
import io.reactivex.Single
import com.google.protobuf.Empty
import com.daml.ledger.javaapi.data.CreatedEvent
import scala.collection.JavaConverters._
import cats.effect.kernel.Sync
import java.util.stream
import cats.Monad
import java.util.UUID
import io.reactivex.subjects.SingleSubject
import com.daml.ledger.rxjava.DamlLedgerClient
import cats.effect.kernel.Resource
import cats.data.Kleisli
import com.daml.ledger.javaapi.data.Identifier

case class DAMLContext(client: DamlLedgerClient, appId: String, operatorParty: String)

trait DAMLAlgebra[F[_]] {

  def submitToDaml(
    tx:               Transaction,
    exerciseCommands: java.util.List[Command]
  ): DAMLKleisli[F, Single[Empty]]

  def processEventsM(
    tx:           Transaction,
    processEvent: (String, CreatedEvent) => DAMLKleisli[F, (Boolean, stream.Stream[Command])]
  ): DAMLKleisli[F, (Boolean, stream.Stream[Command])]

  def processTransactionAux(
    tx: Transaction
  )(
    processEvent: (String, CreatedEvent) => DAMLKleisli[F, (Boolean, stream.Stream[Command])]
  ): DAMLKleisli[F, Boolean]

  def processEventAux[T, C](
    templateIdentifier: Identifier,
    extractContract:    CreatedEvent => T,
    extractContractId:  CreatedEvent => C,
    callback:           (T, C) => Boolean,
    event:              CreatedEvent
  )(
    processor: (T, C) => F[stream.Stream[Command]]
  ): F[(Boolean, stream.Stream[Command])]

  def processTransaction(
    tx:           Transaction,
    processEvent: (String, CreatedEvent) => DAMLKleisli[F, (Boolean, stream.Stream[Command])]
  ): DAMLKleisli[F, Boolean]
}

object DAMLAlgebra {

  def makeDAMLAlgebra[F[_]: Sync] = new DAMLAlgebra[F] {

    override def submitToDaml(
      tx:               Transaction,
      exerciseCommands: java.util.List[Command]
    ): DAMLKleisli[F, Single[Empty]] = if (!exerciseCommands.isEmpty()) {
      Kleisli[F, DAMLContext, Single[Empty]](c =>
        Sync[F].blocking(
          c.client
            .getCommandClient()
            .submitAndWait(
              tx.getWorkflowId(),
              c.appId,
              UUID.randomUUID().toString(),
              c.operatorParty,
              exerciseCommands
            )
        )
      )
    } else {
      Kleisli[F, DAMLContext, Single[Empty]](_ => Sync[F].delay(SingleSubject.create()))
    }

    override def processEventsM(
      tx:           Transaction,
      processEvent: (String, CreatedEvent) => DAMLKleisli[F, (Boolean, stream.Stream[Command])]
    ): DAMLKleisli[F, (Boolean, stream.Stream[Command])] = {
      import cats.implicits._
      tx.getEvents()
        .asScala
        .filter(_.isInstanceOf[CreatedEvent])
        .map(_.asInstanceOf[CreatedEvent])
        .foldLeft(
          Kleisli[F, DAMLContext, (Boolean, stream.Stream[Command])](_ =>
            Sync[F].delay((true, stream.Stream.empty[Command]))
          )
        ) { (firstIO, b) =>
          for {
            pair1 <- firstIO
            (bool0, str0) = pair1
            pair2 <- processEvent(tx.getWorkflowId(), b)
            (bool1, str1) = pair2
          } yield ((bool0 && bool1), stream.Stream.concat(str0, str1))
        }
    }

    def processEventAux[T, C](
      templateIdentifier: Identifier,
      extractContract:    CreatedEvent => T,
      extractContractId:  CreatedEvent => C,
      callback:           (T, C) => Boolean,
      event:              CreatedEvent
    )(
      processor: (T, C) => F[stream.Stream[Command]]
    ): F[(Boolean, stream.Stream[Command])] =
      if (event.getTemplateId() == templateIdentifier) {
        import cats.implicits._
        for {
          contractId   <- Sync[F].delay(extractContractId(event))
          contract     <- Sync[F].delay(extractContract(event))
          mustContinue <- Sync[F].delay(callback.apply(contract, contractId))
          resultStream <- processor(contract, contractId)
        } yield (mustContinue, resultStream)
      } else Sync[F].delay((true, stream.Stream.empty()))

    override def processTransactionAux(
      tx: Transaction
    )(
      processEvent: (String, CreatedEvent) => DAMLKleisli[F, (Boolean, stream.Stream[Command])]
    ): DAMLKleisli[F, Boolean] = {
      import cats.implicits._
      (for {
        pair <- processEventsM(tx, processEvent)
        (mustContinue, exerciseCommandsStream) = pair
        exerciseCommands = exerciseCommandsStream.collect(stream.Collectors.toList())
        _ <- submitToDaml(tx, exerciseCommands)
      } yield
        if (!exerciseCommands.isEmpty()) {
          mustContinue;
        } else true)
    }

    def processTransaction(
      tx:           Transaction,
      processEvent: (String, CreatedEvent) => DAMLKleisli[F, (Boolean, stream.Stream[Command])]
    ): DAMLKleisli[F, Boolean] =
      processTransactionAux(tx)(processEvent)

  }
}
