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
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[MembershipAcceptance, MembershipAcceptance.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) = processEventAux(MembershipAcceptance.TEMPLATE_ID, event) {
    val membershipAcceptanceContract =
      MembershipAcceptance.Contract.fromCreatedEvent(event).id

    val membershipAcceptance =
      MembershipAcceptance.fromValue(
        event.getArguments()
      )
    val mustContinue = callback.apply(membershipAcceptance, membershipAcceptanceContract)
    if (mustContinue) {
      (
        mustContinue,
        stream.Stream.of(
          membershipAcceptanceContract
            .exerciseAddUserToOrganization()
        )
      )
    } else {
      (mustContinue, stream.Stream.empty())
    }
  }

}
