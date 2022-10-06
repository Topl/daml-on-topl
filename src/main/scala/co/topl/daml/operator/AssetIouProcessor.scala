package co.topl.daml.operator

import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.AbstractProcessor
import com.daml.ledger.javaapi.data.CreatedEvent

import java.util.stream
import com.daml.ledger.javaapi.data.Command
import co.topl.daml.processEventAux
import co.topl.daml.api.model.topl.organization.AssetIou
import co.topl.daml.api.model.da.types

class AssetIouProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[AssetIou, AssetIou.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) = processEventAux(
    AssetIou.TEMPLATE_ID,
    e => AssetIou.fromValue(e.getArguments()),
    e => AssetIou.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (assetIou, assetIouContract) =>
    val assetIouContract =
      AssetIou.Contract.fromCreatedEvent(event).id
    val assetIou =
      AssetIou.fromValue(
        event.getArguments()
      )
    val mustContinue = callback.apply(assetIou, assetIouContract)
    stream.Stream.empty()
  }

}
