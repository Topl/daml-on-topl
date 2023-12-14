package co.topl.broker

import co.topl.shared.NetworkIdentifiers
import co.topl.shared.InvalidNet
import scopt.OParser

case class BrokerCLIParamConfig(
  damlHost:            String = "localhost",
  damlPort:            Int = 6865,
  damlHub:             Boolean = false,
  damlSecurityEnabled: Boolean = false,
  network:             NetworkIdentifiers = InvalidNet,
  damlAccessToken:     Option[String] = None,
  operatorParty:       String = ""
)

private [broker] trait ParameterProcessorModule {

  val builder = OParser.builder[BrokerCLIParamConfig]

  implicit val networkRead: scopt.Read[NetworkIdentifiers] =
    scopt.Read
      .reads(NetworkIdentifiers.fromString(_))
      .map(_ match {
        case Some(value) => value
        case None =>
          throw new IllegalArgumentException(
            "Invalid network. Possible values: mainnet, testnet, private"
          )
      })

  val parser = {
    import builder._
    OParser.sequence(
      programName("daml-topl-broker"),
      head("daml-topl-dapp", "0.1"),
      opt[String]("daml-host")
        .action((x, c) => c.copy(damlHost = x))
        .text("The URL of the DAML ledger to connect to. Defaults to localhost."),
      opt[Int]("daml-port")
        .action((x, c) => c.copy(damlPort = x))
        .text("The port of the DAML ledger to connect to. Defaults to 6865."),
      opt[Boolean]("is-daml-hub")
        .action((x, c) => c.copy(damlHub = x))
        .text("Whether to use DAML Hub to authenticate. Defaults to false."),
      opt[Boolean]('s', "daml-security-enabled")
        .action((x, c) => c.copy(damlSecurityEnabled = x))
        .text("whether to use TLS for the connection to the ledger"),
      opt[NetworkIdentifiers]('n', "network")
        .action((x, c) => c.copy(network = x))
        .text(
          "Network name: Possible values: mainnet, testnet, private. (mandatory)"
        )
        .validate(x => if (x == InvalidNet) failure("Invalid network") else success)
        .required(),
      opt[String]("operator-party")
        .action((x, c) => c.copy(operatorParty = x))
        .text("the operator party")
        .validate(x =>
          if (x.isEmpty) failure("operator-party must not be empty")
          else success
        )
        .required(),
      opt[Option[String]]('t', "daml-access-token")
        .action((x, c) => c.copy(damlAccessToken = x))
        .text("the access token for the ledger"),
    )
  }

}
