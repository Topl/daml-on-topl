package co.topl.daml.base

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite
import co.topl.daml.api.model.topl.asset.AssetMintingRequest
import java.util.Optional
import java.{util => ju}
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.utils.AssetCode
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.assets.processors.AssetMintingRequestProcessor
import com.daml.ledger.javaapi.data.Command
import akka.http.scaladsl.model.Uri
import co.topl.client.Provider
import akka.actor.ActorSystem
import co.topl.rpc.ToplRpc
import co.topl.attestation.Address
import co.topl.utils.Int128
import co.topl.attestation.Evidence
import co.topl.utils.NetworkType
import co.topl.modifier.box.PolyBox
import co.topl.modifier.box.SimpleValue
import com.daml.ledger.api.v1.EventOuterClass.CreatedEvent
import com.daml.ledger.javaapi.data
import com.daml.ledger.api.v1.TransactionOuterClass.Transaction
import com.daml.ledger.api.v1.EventOuterClass.Event

trait AssetMintingRequestProcessorBaseTest extends BaseTest {

  import toplContext.provider._

  val assetMintingRequestContract = new AssetMintingRequest.ContractId("")

  val assetMintingRequest = new AssetMintingRequest(
    "operator",
    "alice",
    Optional.of("1"),
    ju.List.of("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64"),
    ju.List.of(new types.Tuple2("AUANVY6RqbJtTnQS1AFTQBjXMFYDknhV8NEixHFLmeZynMxVbp64", java.lang.Long.valueOf(1L))),
    "address",
    new AssetCode(1L, "Wheat"),
    5,
    Optional.empty(),
    Optional.empty(),
    100L
  )

  def dummyStandardProcessor =
    new AssetMintingRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => true, t => true) {

      override def getBalanceM(param: ToplRpc.NodeView.Balances.Params) = IO(
        Map(
          (
            Address(
              dummyEvidence
            ),
            ToplRpc.NodeView.Balances.Entry(
              ToplRpc.NodeView.Balances
                .EntryBalances(Int128(1L), Int128(1L)),
              ToplRpc.NodeView.Balances
                .EntryBoxes(List(PolyBox(dummyEvidence, 1L, SimpleValue(Int128(100)))), List(), List())
            )
          )
        )
      )
    }

  def dummyStandardProcessorWithErrorReturningTrue =
    new AssetMintingRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => true, t => false) {

      override def getBalanceM(param: ToplRpc.NodeView.Balances.Params) = IO(
        Map(
          (
            Address(
              dummyEvidence
            ),
            ToplRpc.NodeView.Balances.Entry(
              ToplRpc.NodeView.Balances
                .EntryBalances(Int128(1L), Int128(1L)),
              ToplRpc.NodeView.Balances
                .EntryBoxes(List(PolyBox(dummyEvidence, 1L, SimpleValue(Int128(100)))), List(), List())
            )
          )
        )
      )
    }

  val dummyFailCondition =
    new AssetMintingRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => false, t => true) {

      override def getBalanceM(param: ToplRpc.NodeView.Balances.Params) = IO(
        Map(
          (
            Address(
              dummyEvidence
            ),
            ToplRpc.NodeView.Balances.Entry(
              ToplRpc.NodeView.Balances
                .EntryBalances(Int128(1L), Int128(1L)),
              ToplRpc.NodeView.Balances
                .EntryBoxes(List(PolyBox(dummyEvidence, 1L, SimpleValue(Int128(100)))), List(), List())
            )
          )
        )
      )
    }

  def dummyFailingWithException =
    new AssetMintingRequestProcessor(damlAppContext, toplContext, 1000, (x, y) => true, t => true) {
      override def getBalanceM(param: ToplRpc.NodeView.Balances.Params) = IO(throw new IllegalStateException())
    }

}