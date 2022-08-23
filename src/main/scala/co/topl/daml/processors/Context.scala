package co.topl.daml.processors

import co.topl.client.Provider
import com.daml.ledger.rxjava.DamlLedgerClient
import akka.actor.ActorSystem

final case class DamlAppContext(appId: String, operatorParty: String, client: DamlLedgerClient)

final case class ToplContext(
  actorSystem: ActorSystem,
  provider:    Provider
)
