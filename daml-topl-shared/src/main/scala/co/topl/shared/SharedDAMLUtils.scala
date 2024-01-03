package co.topl.shared

import java.util.Collections

import cats.effect.kernel.Async
import cats.implicits._
import com.daml.ledger.javaapi.data.FiltersByParty
import com.daml.ledger.javaapi.data.LedgerOffset
import com.daml.ledger.javaapi.data.NoFilter
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.io.net.Network
import io.circe.generic.auto._
import io.grpc.netty.GrpcSslContexts
import org.http4s.BasicCredentials
import org.http4s.EntityDecoder
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

case class LoginResponse(access_token: String, token: String)

object SharedDAMLUtils {

  def createClient[F[_]: Async](
    host:                String,
    port:                Int,
    damlSecurityEnabled: Boolean,
    damlAccessToken:     Option[String]
  ) = {
    import cats._
    val enableSecurity: Endo[DamlLedgerClient.Builder] =
      (if (damlSecurityEnabled)
         _.withSslContext(GrpcSslContexts.forClient().build())
       else identity _)
    val enableAccessToken: Endo[DamlLedgerClient.Builder] =
      damlAccessToken
        .map(x => (y: DamlLedgerClient.Builder) => y.withAccessToken(x))
        .getOrElse(identity _)
    Async[F].delay {
      enableSecurity
        .compose(enableAccessToken)(DamlLedgerClient.newBuilder(host, port))
        .build()
    }
  }

  def login2DamlHub[F[_]: Async: Network](
    damlHost:        String,
    damlAccessToken: String
  ): F[Option[String]] = {
    implicit val loginResponseDecoder: EntityDecoder[F, LoginResponse] = jsonOf[F, LoginResponse]
    for {
      uri <- Async[F].fromEither(
        Uri.fromString(s"https://${damlHost}/.hub/v1/sa/login")
      )
      req = Request[F](
        method = Method.POST,
        uri = uri
      ).withHeaders(
        Headers(
          Authorization(BasicCredentials(damlAccessToken))
        )
      )
      res <- EmberClientBuilder.default.build
        .use(c => c.run(req).use(r => r.as[LoginResponse]))
    } yield Some(res.access_token)
  }

  def getTransactions[F[_]: Async](client: DamlLedgerClient, operatorParty: String) = Async[F].delay(
    client
      .getTransactionsClient()
      .getTransactions(
        LedgerOffset.LedgerEnd.getInstance(),
        new FiltersByParty(
          Collections.singletonMap(operatorParty, NoFilter.instance)
        ),
        true
      )
  )

  def connect[F[_]: Async: Logger](tries: Int, client: DamlLedgerClient): F[Unit] =
    if (tries > 5) Async[F].raiseError(new RuntimeException("Could not connect to the ledger"))
    else
      Async[F].delay(client.connect()).recoverWith { case _ =>
        info"Retrying connection..." *> connect(tries + 1, client)
      }

}
