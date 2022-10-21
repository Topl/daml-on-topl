package co.topl.daml.base

import akka.http.scaladsl.model.Uri
import akka.actor.ActorSystem
import co.topl.client.Provider
import co.topl.attestation.Evidence
import co.topl.utils.NetworkType
import co.topl.daml.DamlAppContext
import co.topl.daml.ToplContext

trait BaseTest {

  val damlAppContext = DamlAppContext("testApp", "operator", null)
  val uri = Uri("http://localhost");
  val actorSystem = ActorSystem()
  val toplContext = ToplContext(actorSystem, new Provider.PrivateTestNet(uri, ""))

  val dummyEvidence =
    Evidence(NetworkType.PrivateTestnet.netPrefix, Evidence.EvidenceContent(Array.fill(32)(Byte.MaxValue)))

}
