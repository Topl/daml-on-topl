package co.topl.daml.operator

import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.AbstractProcessor
import com.daml.ledger.javaapi.data.CreatedEvent

import java.util.stream
import com.daml.ledger.javaapi.data.Command
import co.topl.daml.processEventAux
import co.topl.daml.api.model.topl.organization.MembershipAcceptance
import co.topl.daml.api.model.topl.organization.MembershipOffer

class MembershipOfferProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext
) extends AbstractProcessor(damlAppContext, toplContext) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): stream.Stream[Command] = processEventAux(MembershipOffer.TEMPLATE_ID, event) {
    val membershipOfferContract =
      MembershipOffer.Contract.fromCreatedEvent(event).id

    stream.Stream.of(
      membershipOfferContract
        .exerciseMembershp_Accept()
    )
  }

}
