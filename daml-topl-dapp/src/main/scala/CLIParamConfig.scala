import scopt.OParser

case class CLIParamConfig(
  damlHost:            String = "localhost",
  damlPort:            Int = 6865,
  damlHub:             Boolean = false,
  damlSecurityEnabled: Boolean = false,
  damlAccessToken:     Option[String] = None,
  dappParty:           String = "",
  operatorParty:       String = "",
  password:            String = ""
)

trait ParameterProcessorModule {

  val builder = OParser.builder[CLIParamConfig]

  val parser = {
    import builder._
    OParser.sequence(
      programName("daml-topl-dapp"),
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
      opt[String]("dapp-party")
        .action((x, c) => c.copy(dappParty = x))
        .text("the party that will be used to submit transactions to the ledger")
        .validate(x =>
          if (x.isEmpty) failure("dapp-party must not be empty")
          else success
        ),
      opt[String]("operator-party")
        .action((x, c) => c.copy(operatorParty = x))
        .text("the operator party")
        .validate(x =>
          if (x.isEmpty) failure("operator-party must not be empty")
          else success
        ),
      opt[Option[String]]('t', "daml-access-token")
        .action((x, c) => c.copy(damlAccessToken = x))
        .text("the access token for the ledger"),
      opt[String]('w', "password")
        .action((x, c) => c.copy(password = x))
        .text("the password for the keyfile")
        .validate(x =>
          if (x.isEmpty) failure("password must not be empty")
          else success
        )
    )
  }

}
