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
  lambda:         java.util.function.BiConsumer[AssetIou, AssetIou.ContractId]
) extends AbstractProcessor(damlAppContext, toplContext) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command] = processEventAux(AssetIou.TEMPLATE_ID, event) {
    val assetIouContract =
      AssetIou.Contract.fromCreatedEvent(event).id
    val assetIou =
      AssetIou.fromValue(
        event.getArguments()
      )
    lambda.accept(assetIou, assetIouContract)
    stream.Stream.empty()
  }

}
