package co.topl.daml.operator

import cats.effect.IO
import co.topl.daml.AbstractProcessor
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext
import co.topl.daml.api.model.topl.organization.MembershipAcceptance
import co.topl.daml.api.model.topl.organization.MembershipOffer
import com.daml.ledger.javaapi.data.Command
import com.daml.ledger.javaapi.data.CreatedEvent

import java.util.stream

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
          .exerciseMembershipOffer_Accept()
      )
    )
  }

}
