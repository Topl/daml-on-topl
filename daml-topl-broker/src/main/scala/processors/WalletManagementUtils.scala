package processors

import cats.effect.kernel.Sync
import co.topl.brambl.dataApi.WalletKeyApiAlgebra
import co.topl.brambl.wallet.WalletApi
import co.topl.crypto.encryption.VaultStore
import quivr.models.KeyPair

trait WalletManagementUtilsAlgebra[F[_]] {

  def loadKeys(password: String): DAMLKleisli[F, KeyPair]

  def readInputFile(
  ): DAMLKleisli[F, VaultStore[({ type L[A] = DAMLKleisli[F, A] })#L]]

}

object WalletManagementUtilsAlgebra {

  def makeWalletManagementUtilsAlgebra[F[_]: Sync](
    walletApi: WalletApi[({ type L[A] = DAMLKleisli[F, A] })#L],
    dataApi:   WalletKeyApiAlgebra[({ type L[A] = DAMLKleisli[F, A] })#L]
  ) = new WalletManagementUtilsAlgebra[F] {


    type G[A] = DAMLKleisli[F, A]

    override def readInputFile(): G[VaultStore[G]] = ???

    override def loadKeys(password: String): G[KeyPair] = for {
      wallet <- readInputFile()
      keyPair <-
        walletApi
          .extractMainKey(wallet, password.getBytes())
          .flatMapF(
            _.fold(
              _ =>
                Sync[F].raiseError[KeyPair](
                  new Throwable("No input file (should not happen)")
                ),
              Sync[F].point(_)
            )
          )
    } yield keyPair

  }

}