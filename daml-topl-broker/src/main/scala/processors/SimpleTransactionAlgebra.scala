package processors

import cats.data.EitherT
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import co.topl.brambl.builders.TransactionBuilderApi
import co.topl.brambl.codecs.AddressCodecs
import co.topl.brambl.dataApi.GenusQueryAlgebra
import co.topl.brambl.dataApi.WalletStateAlgebra
import co.topl.brambl.models.Indices
import co.topl.brambl.models.LockAddress
import co.topl.brambl.models.box.Lock
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.Credentialler
import co.topl.brambl.wallet.CredentiallerInterpreter
import co.topl.brambl.wallet.WalletApi
import co.topl.genus.services.Txo
import co.topl.node.services.BroadcastTransactionReq
import co.topl.node.services.BroadcastTransactionRes
import co.topl.node.services.NodeRpcGrpc
import io.grpc.ManagedChannel
import quivr.models.KeyPair
import cats.data.Kleisli

import java.io.FileInputStream
import java.io.FileOutputStream

sealed trait SimpleTransactionAlgebraError {

  def description: String
}

case class CannotInitializeProtobuf(description: String) extends SimpleTransactionAlgebraError

case class InvalidProtobufFile(description: String) extends SimpleTransactionAlgebraError

case class CannotSerializeProtobufFile(description: String) extends SimpleTransactionAlgebraError

case class NetworkProblem(description: String) extends SimpleTransactionAlgebraError

case class UnexpectedError(description: String) extends SimpleTransactionAlgebraError

case class CreateTxError(description: String) extends SimpleTransactionAlgebraError
