package co.topl.daml.operator

import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.AbstractProcessor
import com.daml.ledger.javaapi.data.CreatedEvent

import java.util.stream
import com.daml.ledger.javaapi.data.Command
import co.topl.daml.processEventAux
import co.topl.daml.api.model.topl.organization.MembershipAcceptance
import cats.effect.IO

class MembershipAcceptanceProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[MembershipAcceptance, MembershipAcceptance.ContractId, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    MembershipAcceptance.TEMPLATE_ID,
    e => MembershipAcceptance.fromValue(e.getArguments()),
    e => MembershipAcceptance.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (membershipAcceptance, membershipAcceptanceContract) =>
    IO(
      stream.Stream.of(
        membershipAcceptanceContract
          .exerciseAddUserToOrganization()
      )
    )

  }

}
