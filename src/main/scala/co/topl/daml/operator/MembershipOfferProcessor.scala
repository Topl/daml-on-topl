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
import cats.effect.IO

class MembershipOfferProcessor(
  damlAppContext: DamlAppContext,
  toplContext:    ToplContext,
  callback:       java.util.function.BiFunction[MembershipOffer, MembershipOffer.ContractId, Boolean],
  onError:        java.util.function.Function[Throwable, Boolean]
) extends AbstractProcessor(damlAppContext, toplContext, callback, onError) {

  def processEvent(
    workflowsId: String,
    event:       CreatedEvent
  ): IO[(Boolean, stream.Stream[Command])] = processEventAux(
    MembershipOffer.TEMPLATE_ID,
    e => MembershipOffer.fromValue(e.getArguments()),
    e => MembershipOffer.Contract.fromCreatedEvent(e).id,
    callback.apply,
    event
  ) { (membershipOffer, membershipOfferContract) =>
    IO(
      stream.Stream.of(
        membershipOfferContract
          .exerciseMembershp_Accept()
      )
    )
  }

}
