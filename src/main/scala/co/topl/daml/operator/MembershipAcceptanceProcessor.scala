package co.topl.daml.operator

import java.util.stream

import cats.effect.IO
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.topl.organization.MembershipAcceptance
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent

class MembershipAcceptanceProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[MembershipAcceptance, MembershipAcceptance.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError) {

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
