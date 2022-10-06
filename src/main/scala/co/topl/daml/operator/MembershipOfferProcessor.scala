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
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[MembershipOffer, MembershipOffer.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): (Boolean, stream.Stream[Command]) = processEventAux(MembershipOffer.TEMPLATE_ID, event) {
    val membershipOfferContract =
      MembershipOffer.Contract.fromCreatedEvent(event).id
    val membershipOffer =
      MembershipOffer.fromValue(
        event.getArguments()
      )
    val mustContinue = callback.apply(membershipOffer, membershipOfferContract)
    if (mustContinue) {
      (
        mustContinue,
        stream.Stream.of(
          membershipOfferContract
            .exerciseMembershp_Accept()
        )
      )
    } else {
      (mustContinue, stream.Stream.empty())
    }
  }

}
