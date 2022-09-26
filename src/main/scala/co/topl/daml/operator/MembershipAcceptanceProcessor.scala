package co.topl.daml.operator

import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.AbstractProcessor
import com.daml.ledger.javaapi.data.CreatedEvent

import java.util.stream
import com.daml.ledger.javaapi.data.Command
import co.topl.daml.processEventAux
import co.topl.daml.api.model.topl.organization.MembershipAcceptance

class MembershipAcceptanceProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext
) extends AbstractProcessor(damlAppContext, toplContext) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command] = processEventAux(MembershipAcceptance.TEMPLATE_ID, event) {
    val membershipAcceptanceContract =
      MembershipAcceptance.Contract.fromCreatedEvent(event).id

    stream.Stream.of(
      membershipAcceptanceContract
        .exerciseAddUserToOrganization()
    )
  }

}
