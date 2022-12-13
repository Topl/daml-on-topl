package co.topl.daml

import akka.actor.ActorSystem
import co.topl.client.Provider
import com.daml.ledger.rxjava.DamlLedgerClient

final case class DamlAppContext(appId: String, operatorParty: String, client: DamlLedgerClient)

final case class ToplContext(
  actorSystem: ActorSystem,
  provider:    Provider
)
