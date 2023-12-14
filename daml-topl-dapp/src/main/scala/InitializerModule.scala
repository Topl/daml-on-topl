import java.util.Optional
import java.util.UUID

import scala.jdk.CollectionConverters._

import cats.effect.IO
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
import com.daml.ledger.rxjava.ActiveContractsClient
import com.daml.ledger.rxjava.DamlLedgerClient
import fs2.interop.reactivestreams._
import co.topl.daml.api.model.topl.wallet.WalletLockTemplate
import com.daml.ledger.javaapi.data.CommandsSubmission

trait InitializerModule {

  def createWalletLockTemplate(
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          CLIParamConfig
  ) = for {
    activeContracts <- IO(
      activeContractClient.getActiveContracts(
        WalletLockTemplate.contractFilter(),
        Set(paramConfig.dappParty).asJava,
        false
      )
    )
    activeContracts <- fromPublisher(activeContracts, 1)(IO.asyncForIO).compile.toList
    _ <- IO(
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
                  .exerciseVaultState_CreateWalletContract("default", "threshold(1, sign(0))")
              ).asJava
            )
            .withActAs(paramConfig.dappParty)
        )
    ).whenA(activeContracts.isEmpty)
  } yield ()

  def createCurrentInteraction(
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          CLIParamConfig
  ) = {
    import co.topl.crypto.encryption.VaultStore.Codecs._
    import io.circe.parser.decode
    for {
      vaults <- IO(
        activeContractClient.getActiveContracts(Vault.contractFilter(), Set(paramConfig.dappParty).asJava, false)
      )
      vault <- fromPublisher(vaults, 1)(IO.asyncForIO).compile.toList.map(_.get(0).get.activeContracts.get(0))
      activeContracts <- IO(
        activeContractClient.getActiveContracts(
          CurrentInteraction.contractFilter(),
          Set(paramConfig.dappParty).asJava,
          false
        )
      )
      keyData = vault.data.vault
      walletKeyApi = WalletKeyApi.make[IO]()
      walletApi = WalletApi.make(walletKeyApi)
      vaultStore <- IO.fromEither(
        decode[VaultStore[IO]](keyData)
      )
      eitherKeyPair <- walletApi.extractMainKey(vaultStore, paramConfig.password.getBytes())
      keyPair <- IO.fromEither(
        eitherKeyPair
      )
      deriveChildKey  <- walletApi.deriveChildKeysPartial(keyPair, 1, 1)
      activeContracts <- fromPublisher(activeContracts, 1)(IO.asyncForIO).compile.toList
      _ <- IO(
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
                    .exerciseVaultState_InviteToConversation(
                      UUID.randomUUID().toString(),
                      "self",
                      "default",
                      List(paramConfig.dappParty).asJava, // can send
                      Optional.of(Encoding.encodeToBase58(deriveChildKey.vk.toByteArray))
                    )
                ).asJava
              )
          )
      ).whenA(activeContracts.isEmpty)
    } yield ()
  }

  def createVaultState(
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          CLIParamConfig
  ) = for {
    activeContracts <- IO(
      activeContractClient.getActiveContracts(VaultState.contractFilter(), Set(paramConfig.dappParty).asJava, false)
    )
    activeContracts <- fromPublisher(activeContracts, 1)(IO.asyncForIO).compile.toList
    // there should be one or no active contracts
    _ <- IO(
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
    ).whenA(activeContracts.isEmpty)
  } yield ()

  def createWalletFellowship(
    client:               DamlLedgerClient,
    activeContractClient: ActiveContractsClient,
    paramConfig:          CLIParamConfig
  ) = for {
    activeContracts <- IO(
      activeContractClient.getActiveContracts(
        WalletFellowship.contractFilter(),
        Set(paramConfig.dappParty).asJava,
        false
      )
    )
    activeContracts <- fromPublisher(activeContracts, 1)(IO.asyncForIO).compile.toList
    _ <- IO(
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
    ).whenA(activeContracts.isEmpty)
  } yield ()
}
