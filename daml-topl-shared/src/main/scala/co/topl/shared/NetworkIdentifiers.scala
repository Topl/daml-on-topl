package co.topl.shared

import co.topl.brambl.constants.NetworkConstants

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
