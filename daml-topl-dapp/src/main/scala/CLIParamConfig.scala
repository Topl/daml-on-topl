import scopt.OParser
import co.topl.brambl.constants.NetworkConstants

case class CLIParamConfig(
  damlHost:            String = "localhost",
  damlPort:            Int = 6865,
  damlHub:             Boolean = false,
  damlSecurityEnabled: Boolean = false,
  network:             NetworkIdentifiers = InvalidNet,
  damlAccessToken:     Option[String] = None,
  dappParty:           String = "",
  operatorParty:       String = "",
  password:            String = ""
)

sealed abstract class NetworkIdentifiers(
  val i:         Int,
  val name:      String,
  val networkId: Int
) {
  override def toString: String = name
}

case object NetworkIdentifiers {

  def values = Set(Mainnet, Testnet, Privatenet)

  def fromString(s: String): Option[NetworkIdentifiers] =
    s match {
      case "mainnet" => Some(Mainnet)
      case "testnet" => Some(Testnet)
      case "private" => Some(Privatenet)
      case _         => None
    }
}

case object Mainnet extends NetworkIdentifiers(0, "mainnet", NetworkConstants.MAIN_NETWORK_ID)
case object Testnet extends NetworkIdentifiers(1, "testnet", NetworkConstants.TEST_NETWORK_ID)

case object Privatenet
    extends NetworkIdentifiers(
      2,
      "private",
      NetworkConstants.PRIVATE_NETWORK_ID
    )

case object InvalidNet
    extends NetworkIdentifiers(
      -1,
      "invalid",
      NetworkConstants.PRIVATE_NETWORK_ID
    )

trait ParameterProcessorModule {

  val builder = OParser.builder[CLIParamConfig]

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
        )
        .required(),
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
      opt[String]('w', "password")
        .action((x, c) => c.copy(password = x))
        .text("the password for the keyfile")
        .validate(x =>
          if (x.isEmpty) failure("password must not be empty")
          else success
        )
        .required()
    )
  }

}
