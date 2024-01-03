package co.topl.broker.processors



sealed trait SimpleTransactionAlgebraError {

  def description: String
}

case class CannotInitializeProtobuf(description: String) extends SimpleTransactionAlgebraError

case class InvalidProtobufFile(description: String) extends SimpleTransactionAlgebraError

case class CannotSerializeProtobufFile(description: String) extends SimpleTransactionAlgebraError

case class NetworkProblem(description: String) extends SimpleTransactionAlgebraError

case class UnexpectedError(description: String) extends SimpleTransactionAlgebraError

case class CreateTxError(description: String) extends SimpleTransactionAlgebraError
