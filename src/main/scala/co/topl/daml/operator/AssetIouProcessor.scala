package co.topl.daml.operator

import cats.effect.IO
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.da.types
import co.topl.daml.api.model.topl.organization.AssetIou
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent

import java.util.stream

class AssetIouProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[AssetIou, AssetIou.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    AssetIou.TEMPLATE_ID,
    e => AssetIou.fromValue(e.getArguments()),
    e => AssetIou.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (assetIou, assetIouContract) =>
    for {
      res <- IO(callback.apply(assetIou, assetIouContract))
    } yield stream.Stream.empty()
  }

}
