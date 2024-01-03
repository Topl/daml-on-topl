package co.topl.dapp

import java.util.Optional
import java.util.UUID

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.effect.kernel.Async
import cats.effect.kernel.Sync
import cats.implicits._
import co.topl.brambl.servicekit.WalletKeyApi
import co.topl.brambl.utils.Encoding
import co.topl.brambl.wallet.WalletApi
import co.topl.crypto.encryption.VaultStore
import co.topl.daml.api.model.topl.onboarding.WalletCreationRequest
import co.topl.daml.api.model.topl.wallet.CurrentInteraction
import co.topl.daml.api.model.topl.wallet.Vault
import co.topl.daml.api.model.topl.wallet.VaultState
import co.topl.daml.api.model.topl.wallet.WalletFellowship
import co.topl.daml.api.model.topl.wallet.WalletLockTemplate
import com.daml.ledger.javaapi.data.CommandsSubmission
import com.daml.ledger.rxjava.ActiveContractsClient
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.interop.reactivestreams._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.syntax._

trait InitializerModule {

  def createWalletLockTemplate[F[_]: Async](
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          DappCLIParamConfig
  ) = for {
    activeContracts <- Async[F].delay(
      activeContractClient.getActiveContracts(
        WalletLockTemplate.contractFilter(),
        Set(paramConfig.dappParty).asJava,
        false
      )
    )
    activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
    activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
    _ <- Async[F]
      .delay(
        client
          .getCommandClient()
          .submitAndWaitForTransaction(
            CommandsSubmission
              .create(
                "damlhub",
                UUID.randomUUID().toString,
                List(
                  VaultState
                    .byKey(paramConfig.dappParty)
                    .exerciseVaultState_CreateWalletContract(
                      "default",
                      """{
                          "threshold" : 1,
                          "innerTemplates" : [
                            {
                              "routine" : "ExtendedEd25519",
                              "entityIdx" : 0,
                              "type" : "signature"
                            }
                          ],
                          "type" : "predicate"
                        }"""
                    )
                ).asJava
              )
              .withActAs(paramConfig.dappParty)
          )
      )
      .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
      .whenA(activeContracts.isEmpty)
  } yield ()

  def createCurrentInteraction[F[_]: Async: Logger](
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          DappCLIParamConfig
  ) = {
    import co.topl.crypto.encryption.VaultStore.Codecs._
    import io.circe.parser.decode
    for {
      // we should check for VaultState
      _ <- Async[F].timeout(
        (for {
          _ <- info"Waiting for wallet fellowship"
          activeContracts <- Async[F].delay(
            activeContractClient.getActiveContracts(
              WalletFellowship.contractFilter(),
              Set(paramConfig.dappParty).asJava,
              false
            )
          )
          activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
          activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
          _ <-
            if (activeContracts.isEmpty) Sync[F].sleep(1000.millis)
            else Sync[F].unit
        } yield activeContracts.isEmpty).iterateWhile(identity),
        10.seconds // die after 10 seconds
      )
      vaults <- Async[F].delay(
        activeContractClient.getActiveContracts(Vault.contractFilter(), Set(paramConfig.dappParty).asJava, false)
      )
      vault <- fromPublisher(vaults, 1)(Async[F]).compile.toList.map(_.get(0).get.activeContracts.get(0))
      activeContracts <- Async[F].delay(
        activeContractClient.getActiveContracts(
          CurrentInteraction.contractFilter(),
          Set(paramConfig.dappParty).asJava,
          false
        )
      )
      keyData = vault.data.vault
      walletKeyApi = WalletKeyApi.make[F]()
      walletApi = WalletApi.make(walletKeyApi)
      vaultStore <- Async[F].fromEither(
        decode[VaultStore[F]](keyData)
      )
      eitherKeyPair <- walletApi.extractMainKey(vaultStore, paramConfig.password.getBytes())
      keyPair <- Async[F].fromEither(
        eitherKeyPair
      )
      deriveChildKey      <- walletApi.deriveChildKeysPartial(keyPair, 1, 1)
      activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
      activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
      _ <- Async[F]
        .delay(
          client
            .getCommandClient()
            .submitAndWait(
              CommandsSubmission
                .create(
                  "damlhub",
                  UUID.randomUUID().toString,
                  List(
                    VaultState
                      .byKey(paramConfig.dappParty)
                      .exerciseVaultState_InviteToConversation(
                        UUID.randomUUID().toString(),
                        "self",
                        "default",
                        List(paramConfig.dappParty).asJava, // can send
                        Optional.of(Encoding.encodeToBase58(deriveChildKey.vk.toByteArray))
                      )
                  ).asJava
                )
                .withActAs(paramConfig.dappParty)
            )
        )
        .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
        .whenA(activeContracts.isEmpty)
    } yield ()
  }

  def createVaultState[F[_]: Async](
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          DappCLIParamConfig
  ) = for {
    activeContracts <- Async[F].delay(
      activeContractClient.getActiveContracts(VaultState.contractFilter(), Set(paramConfig.dappParty).asJava, false)
    )
    activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
    activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
    // there should be one or no active contracts
    _ <- Async[F]
      .delay(
        client
          .getCommandClient()
          .submitAndWaitForTransaction(
            CommandsSubmission
              .create(
                "damlhub",
                UUID.randomUUID().toString,
                List(WalletCreationRequest.create(paramConfig.dappParty, paramConfig.operatorParty)).asJava
              )
              .withActAs(paramConfig.dappParty)
          )
      )
      .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
      .whenA(activeContracts.isEmpty)
  } yield ()

  def createVault[F[_]: Async](
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          DappCLIParamConfig
  ) = {
    // TODO: make sure this is only created once, there is another in another file
    val walletKeyApi = WalletKeyApi.make[F]()
    val walletApi = WalletApi.make(walletKeyApi)
    import co.topl.crypto.encryption.VaultStore.Codecs._
    import io.circe.syntax._
    for {
      activeContracts <- Async[F].delay(
        activeContractClient.getActiveContracts(Vault.contractFilter(), Set(paramConfig.dappParty).asJava, false)
      )
      activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
      activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
      // there should be one or no active contracts
      eitherVaultStore <- walletApi.createNewWallet(paramConfig.password.getBytes())
      vaultStore       <- Async[F].fromEither(eitherVaultStore)
      _ <- Sync[F]
        .delay(
          client
            .getCommandClient()
            .submitAndWaitForTransaction(
              CommandsSubmission
                .create(
                  "damlhub",
                  UUID.randomUUID().toString,
                  List(
                    Vault.create(
                      paramConfig.dappParty,
                      vaultStore.mainKeyVaultStore.asJson.noSpaces
                    )
                  ).asJava
                )
                .withActAs(paramConfig.dappParty)
            )
        )
        .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
        .whenA(activeContracts.isEmpty)
    } yield ()
  }

  def createWalletFellowship[F[_]: Async: Logger](
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          DappCLIParamConfig
  ) = for {
    activeContracts <- Async[F].delay(
      activeContractClient.getActiveContracts(
        WalletFellowship.contractFilter(),
        Set(paramConfig.dappParty).asJava,
        false
      )
    )
    activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
    activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
    // we should check for VaultState
    _ <- Async[F].timeout(
      (for {
        _ <- info"Waiting for vault state"
        activeContracts <- Async[F].delay(
          activeContractClient.getActiveContracts(
            VaultState.contractFilter(),
            Set(paramConfig.dappParty).asJava,
            false
          )
        )
        activeContractsList <- fromPublisher(activeContracts, 1)(Async[F]).compile.toList
        activeContracts     <- Sync[F].delay(activeContractsList.flatMap(_.activeContracts.asScala.toList))
        _ <-
          if (activeContracts.isEmpty) Sync[F].sleep(1000.millis)
          else Sync[F].unit
      } yield activeContracts.isEmpty).iterateWhile(identity),
      10.seconds // die after 10 seconds
    )
    _ <- Async[F]
      .delay(
        client
          .getCommandClient()
          .submitAndWaitForTransaction(
            CommandsSubmission
              .create(
                "damlhub",
                UUID.randomUUID().toString,
                List(
                  VaultState
                    .byKey(paramConfig.dappParty)
                    .exerciseVaultState_InviteFellowship(UUID.randomUUID().toString, List(paramConfig.dappParty).asJava)
                ).asJava
              )
              .withActAs(paramConfig.dappParty)
          )
      )
      .flatMap(tx => Sync[F].blocking(tx.blockingGet()))
      .whenA(activeContracts.isEmpty)
  } yield ()
}
