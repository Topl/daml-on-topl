# Specification

[TOC]



## Goal

The goal of this document is to enable the reader to implement the different processors and APIs needed to allow DAML contracts to interact with the Topl blockchain.

## Prerequirements

The reader of this document must be familiar with DAML, the Topl blockchain, and their basic respective concepts. 

## Definitions

Most definitions in this section come from the [DAML glossary](https://docs.daml.com/concepts/glossary.html). We included them here to improve the readability of the document.

**Participant node.-** The participant node is a server that provides users a consistent programmatic access to a ledger through the Ledger API. The participant nodes handles transaction signing and validation, such that users don’t have to deal with cryptographic primitives but can trust the participant node that the data they are observing has been properly verified to be correct.[^1]

**Party.**- A party represents a person or legal entity. Parties can create contracts and exercise choices. Signatories, observers, controllers, and maintainers all must be parties, represented by the Party data type in Daml and determine who may see contract data. Parties are hosted on participant nodes and a participant node can host more than one party. A party can be hosted on several participant nodes simultaneously. [^2]

**Processor.-** A processor is a listener installed in the client application to respond to events happening in the participant node.

## Overview

### DAML

DAML is a domain-specific language and a platform for the development of multi-party applications. In particular DAML allows modeling of real world contracts in an ergonomic way using functional constructs.

The domain-specific language allows the definition of multi-party contracts, which mirror real-world contracts. In particular, DAML contracts can have signatories, observers, controllers and other constructs that come from the domain of real-world contracts. These contracts are uploaded to a participant node which provides an API to interact with the different contracts and executes their semantics.

A multi-party application, from DAML's point of view, is an application that relies on DAML contracts and their semantics to govern the different multi-party interactions. Other interactions are handled by other modules of the application.

### Topl

Topl Blockchain is an implementation of the blockchain technology focused on sustainability.It offers a native token (the poly or level) and the possibility of minting native assets which can in turn be traded and stored in the blockchain.

### DAML-Topl integration

A multi-party application that uses DAML contracts on top of the Topl blochain needs to model the blockchain primitives as DAML contracts and provide glue-code to interact with the participant node. 

DAML provides two ways to interact with the participant node:

- A gRPC API that allows to execute different contract operations as the creation of contracts and the execution of choices
- A reactive API that allows to react to events in the participant node. This API also allows to send commands to the participant node in response to different events.

The former API allows the application to initiate operations in DAML on behalf of the different parties. The latter allows the application to react to the events generated when the DAML semantics is executed. 

We assume that the user initiates operations in the participan node using directly the gRPC API, thus, the library does not provide wrappers around this API.

On the other hand, the library defines a standard flow for transfer of polys, and minting and transfer of assets. 

## Use cases

The library is designed to fulfill the three following use cases:

- Transfer of polys from one address to another address on behalf of a party
- Minting and transfer of assets in standalone fashion
- Minting and transfer of assets as part of an organization

### Transfer of polys from one address to another

The transfer of polys from one party to another is structured in three steps:

- Request
- Signing
- Transaction broadcast

The reason of this separation is flexibility. Indeed, this setup allows three different parties to perform each of the steps separately. This also allows the same party to perform all three steps.

### Minting and transfer of assets in standalone fashion

The minting and transfer of assets is structured in the exact same way as the poly transfer:

- Request
- Signing
- Transaction broadcast

The reason is the same. We want to have maximum flexibility and allow each operation to be performed separately.

### Minting and transfer of assets as part of an organization

The organization module is a set of contracts that allow to a list of parties to share an asset and perform operations on it through an IOU contract. The IOU contract is a DAML contract that models the collective ownership of a given asset by a set of parties delimited by the organization.

The organization contract models an organization that can have members, and manage different kinds of assets. All members of an organization can mint and transfer assets, and also create new assets.

## DAML contracts architecture

### Participants

We implemented the DAML contracts assuming the existence of certain participants or parties.

#### Operator

The operator party represents an application that performs operations on behalf of a user. For example, given an application that allows to send polys from one address to another, the operator is the software that will actually perform the operations on the blockchain. Depending on the implementation, the operator can perform all steps in a transaction: requesting, signing and broadcasting. 

#### Organization

An organization is composed of a set of parties (members), and an operator that performs operations on behalf of the organization. In our implementation, all members of the organization are allowed to request operations for the operator to perform. The operations are asset related, i.e. minting and modification of security root and metadata.

### Modules

The DAML contracts are organized in 5 modules, each module being a DAML file. The modules are:

- Asset
- Onboarding
- Organization
- Transfer
- Utils

Additional DAML files are in the code base are meant for testing (stored under the Tests folder), and for sandbox initialization (`Main.daml`).

#### Asset

The Asset modules contains the code for the [Minting and transfer of assets in standalone fashion](#minting-and-transfer-of-assets-in-standalone-fashion). This module allows the user to mint and transfer assets on the blockchain. This module only depends on the Utils module. It contains contracts modeling a minting/transfer request, an unsigned minting/transfer and a signed minting/transfer in different states.

#### Onboarding

The Onboarding module contains the code for the operator and user contract. The module contains three contracts. One of the contracts is for the operator itself, that allows the software to create the other contracts. The two others model an invitation to another party to become a user, and the user contract. The user contract contains the operations that the user can ask the operator to perform. The only operation supported by the contract is the transfer of polys from one address to another.

#### Organization

The organization module contains the contracts that are necessary to handle the organization. It includes the Organization contract itself as well as the invitations to become a member, the acceptance contract, the asset creation contract and the IOUs contracts modelling the ownership of assets in the blockchain.

#### Transfer

The transfer module contains the code for the [Transfer of polys from one address to another](#transfer-of-polys-from-one-address-to-another). This module allows the user to initiate a transfer of polys on the blockchain. In contains contracts modeling transfer requests, unsigned transfer and signed transfer in different states.

#### Utils

The Utils module contains the supporting functions and data structures used in the other modules.

## Contracts

In this section we present the different contracts available in our implementation. Each contract is presented with its parameters and choices. The data types are the standard data types for DAML contracts. It is worth noting that `Text` is equivalent to Java `String`and `[ Type ]` is equivalent to a list of the given type and `Int` is equivalent to a Java `Long`.

### Asset Contracts

#### `AssetMintingRequest`

The asset minting request contract models a request from some user to the operator to mint a particular asset. The asset to mint is passed as a parameter at the creation time of the contract.

**Module:** Topl.Asset

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| requestor      | Party          | The party that requested this minting operation.             |
| someOrgId      | Optional Text  | If this minting request is related to an organization, this field contains the organization id, otherwise, `None`. |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |

##### Choices

##### `MintingRequest_Accept`

Accepts the request for further processing by the operator.

###### Parameters

| Name     | Type | Description                                                  |
| -------- | ---- | ------------------------------------------------------------ |
| txToSign | Text | The transaction to be signed serialized by the `AssetTransferSerializer` and encoded in Base58. |
| boxNonce | Int  | The nonce of the box where the asset is going to be stored.  |

##### `TransferRequest_Reject`

Rejects the request.

###### Parameters

None.

##### `TransferRequest_Archive`

Archives the request. Archive implies that the archiving was not done because of an error.

###### Parameters

None.

#### `UnsignedAssetMinting`

An unsigned asset minting. It is created when a `MintingRequest_Accept` is exercise in an `AssetMintingRequest`. In includes all the data of the `AssetMintingRequest` and also the transaction to sign  encoded in Base58 and the box nonce. 

**Module:** Topl.Asset

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| requestor      | Party          | The party that requested this minting operation.             |
| someOrgId      | Optional Text  | If this minting request is related to an organization, this field contains the organization id, otherwise, `None`. |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| boxNonce       | Int            | The nonce of the box where the asset is going to be stored.  |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |
| mintTxToSign   | Text           | The minting transaction to be signed serialized by the `AssetTransferSerializer` and encoded in Base58. |

##### Choices

##### `UnsignedMinting_Sign`

Signs the request for broadcasting to the network by the operator.

###### Parameters

| Name         | Type | Description                                                  |
| ------------ | ---- | ------------------------------------------------------------ |
| signedMintTx | Text | The signed minting transaction serialized by the `AssetTransferSerializer` and encoded in Base58. |

##### `UnsignedMinting_Reject`

Rejects the signing request.

###### Parameters

None.

##### `UnsignedMinting_Archive`

Archives the signing request. Archive implies that the archiving was not done because of an error.

###### Parameters

None.

#### `SignedAssetMinting`

A signed asset minting request. It is created when a `UnsignedMinting_Sign` is exercised in an `UnsignedAssetMinting`. In includes all the data of the `UnsignedAssetMinting` and also the signed transaction encoded in Base58 and a send status. The send status informs the current status of the minting request with respect to the network. 

**Module:** Topl.Asset

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| requestor      | Party          | The party that requested this minting operation.             |
| someOrgId      | Optional Text  | If this minting request is related to an organization, this field contains the organization id, otherwise, `None`. |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| boxNonce       | Int            | The nonce of the box where the asset is going to be stored.  |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |
| mintTxToSign   | Text           | The minting transaction to be signed serialized by the `AssetTransferSerializer` and encoded in Base58. |
| signedMintTx   | Text           | The minting transaction signed serialized by the `AssetTransferSerializer` and encoded in Base58. |
| sendStatus     | SendStatus     | The status of the transaction. It can be: New, Pending, Sent, FailedToSend, and Confirmed. |

##### Choices

##### `SignedAssetMinting_Send`

Marks the transaction as `Pending`.

###### Parameters

None.

##### `SignedAssetMinting_Sent`

Marks a pending transaction with a new status

###### Parameters

| Name          | Type       | Description                                                  |
| ------------- | ---------- | ------------------------------------------------------------ |
| newSendStatus | SendStatus | The new status of this transaction after being sent to the network. |

##### `SignedAssetMinting_Fail`

Marks this transaction as failed

###### Parameters

| Name   | Type | Description                |
| ------ | ---- | -------------------------- |
| reason | Text | The reason of the failure. |

##### `SignedAssetMinting_Confirm`

Marks this transaction as confirmed.

###### Parameters

| Name  | Type | Description                                           |
| ----- | ---- | ----------------------------------------------------- |
| txId  | Text | The identifier of this transaction in the blockchain. |
| depth | Int  | The depth at which the transaction is done.           |

##### `SignedAssetMinting_Archive`

Archives the contract.

###### Parameters

None.

#### `AssetTransferRequest`

The asset transfer request contract models a request from some user to the operator to modify a particular asset (its commit root or its metadata). The asset to modify and the boxNonce where the asset is stored is passed as a parameter at the creation time of the contract.

**Module:** Topl.Asset

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| requestor      | Party          | The party that requested this minting operation.             |
| someOrgId      | Optional Text  | If this minting request is related to an organization, this field contains the organization id, otherwise, `None`. |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| boxNonce       | Int            | The nonce of the box where the asset is stored.              |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |

##### Choices

##### `AssetTransferRequest_Accept`

Accepts the request for further processing by the operator.

###### Parameters

| Name        | Type | Description                                                  |
| ----------- | ---- | ------------------------------------------------------------ |
| txToSign    | Text | The transaction to be signed serialized by the `AssetTransferSerializer` and encoded in Base58. |
| newBoxNonce | Int  | The nonce of the box where the asset is going to be stored.  |

##### `AssetTransferRequest_Reject`

Rejects the request.

###### Parameters

None.

##### `AssetTransferRequest_Archive`

Archives the request. Archive implies that the archiving was not done because of an error.

###### Parameters

None.

#### `UnsignedAssetTransferRequest`

An unsigned asset transfer. It is created when a `AssetTransferRequest_Accept` is exercise in an `AssetTransferRequest`. In includes all the data of the `AssetTransferRequest` and also the transaction to sign  encoded in Base58 and the box nonce. 

**Module:** Topl.Asset

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| requestor      | Party          | The party that requested this minting operation.             |
| someOrgId      | Optional Text  | If this minting request is related to an organization, this field contains the organization id, otherwise, `None`. |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| boxNonce       | Int            | The nonce of the box where the asset is going to be stored.  |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |
| txToSign       | Text           | The transfer transaction to be signed serialized by the `AssetTransferSerializer` and encoded in Base58. |

##### Choices

##### `UnsignedAssetTransfer_Sign`

Signs the request for broadcasting to the network by the operator.

###### Parameters

| Name     | Type | Description                                                  |
| -------- | ---- | ------------------------------------------------------------ |
| signedTx | Text | The signed transfer transaction serialized by the `AssetTransferSerializer` and encoded in Base58. |

##### `UnsignedAssetTransfer_Reject`

Rejects the signing request.

###### Parameters

None.

##### `UnsignedAssetTransfer_Archive`

Archives the signing request. Archive implies that the archiving was not done because of an error.

###### Parameters

None.

#### `SignedAssetTransfer`

A signed asset transfer request. It is created when a `UnsignedAssetTransfer_Sign` is exercised in an `UnsignedAssetTransferRequest`. In includes all the data of the `UnsignedAssetTransferRequest` and also the signed transaction encoded in Base58 and a send status. The send status informs the current status of the transfer request with respect to the network. 

**Module:** Topl.Asset

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| requestor      | Party          | The party that requested this minting operation.             |
| someOrgId      | Optional Text  | If this minting request is related to an organization, this field contains the organization id, otherwise, `None`. |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| boxNonce       | Int            | The nonce of the box where the asset is going to be stored.  |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |
| txToSign       | Text           | The transfer transaction to be signed serialized by the `AssetTransferSerializer` and encoded in Base58. |
| signedTx       | Text           | The signed transfer transaction serialized by the `AssetTransferSerializer` and encoded in Base58. |
| sendStatus     | SendStatus     | The status of the transaction. It can be: New, Pending, Sent, FailedToSend, and Confirmed. |

##### Choices

##### `SignedAssetTransfer_Send`

Marks the transaction as `Pending`.

###### Parameters

None.

##### `SignedAssetTransfer_Sent`

Marks a pending transaction with a new status

###### Parameters

| Name          | Type       | Description                                                  |
| ------------- | ---------- | ------------------------------------------------------------ |
| newSendStatus | SendStatus | The new status of this transaction after being sent to the network. |

##### `SignedAssetTransfer_Fail`

Marks this transaction as failed

###### Parameters

| Name   | Type | Description                |
| ------ | ---- | -------------------------- |
| reason | Text | The reason of the failure. |

##### `SignedAssetTransfer_Confirm`

Marks this transaction as confirmed.

###### Parameters

| Name  | Type | Description                                           |
| ----- | ---- | ----------------------------------------------------- |
| txId  | Text | The identifier of this transaction in the blockchain. |
| depth | Int  | The depth at which the transaction is done.           |

##### `SignedAssetTransfer_Archive`

Archives the contract.

###### Parameters

None.



### Onboarding Contracts

#### `Operator`

The operator contract is a contract signed uniquely by the operator. It includes as a parameter the operator's address.

**Module:** Topl.Onboarding

##### Parameters

| Name     | Type  | Description                                                  |
| -------- | ----- | ------------------------------------------------------------ |
| operator | Party | The operator that will provide this operator contract.       |
| address  | Text  | The Base58 address used by this operator to perform its operations. |

##### Choices

##### `Operator_InviteUser`

Invites a user to become a user of the system. After accepting the invitation, the user becomes a signatory of the User contract.

###### Parameters

| Name | Type  | Description                                 |
| ---- | ----- | ------------------------------------------- |
| user | Party | The user invited to sign the user contract. |

##### `Operator_CreateOrganization`

Creates an Organization contract where this operator is the operator and the address of the organization is inherited by the organization.

###### Parameters

| Name    | Type | Description                                                  |
| ------- | ---- | ------------------------------------------------------------ |
| orgId   | Text | The unique identifier of the organization. This identifier is treated as a database key. It needs to be unique for a specific operator. |
| orgName | Text | The human readable name of the organization.                 |

#### `UserInvitation`

The user invitation contract is a contract signed by the operator where the user is an observer, i.e. can interact with the contract. The user can accept or reject the invitation.

**Module:** Topl.Onboarding

##### Parameters

| Name     | Type  | Description                                                  |
| -------- | ----- | ------------------------------------------------------------ |
| operator | Party | The operator that invites the user to sign the User contract. |
| user     | Party | The user invited to sign the user contract.                  |

##### Choices

##### `UserInvitation_Accept`

A choice where the user is the controller. On acceptance, a new `User` contract is created.

###### Parameters

None.

##### `UserInvitation_Reject`

A choice where the user is the controller. On rejection the invitaion is archived.

###### Parameters

None.

##### `UserInvitation_Cancel`

A choice where the operator is the controller. The controller can at any time revoke the invitaion. On cancel, the invitation is archived.

###### Parameters

None.

#### `User`

The `User` contract allows a user to ask an operator to transfer polys on the user's behalf.

**Module:** Topl.Onboarding

##### Parameters

| Name     | Type  | Description                                                  |
| -------- | ----- | ------------------------------------------------------------ |
| operator | Party | The operator that will perform operations of behalf of the user. |
| user     | Party | The user that agrees that the operator performs operations on the user's behalf. |

##### Choices

##### `User_SendPolys`

A choice where the user is the controller. When this choice is exercised a new `TransferRequest` contract is created and the operator software proceeds to process the transaction. This is a nonconsuming choice, and thus it can be exercised without archiving the `User` contract.

###### Parameters

| Name          | Type           | Description                                                  |
| ------------- | -------------- | ------------------------------------------------------------ |
| from          | [Text]         | A list of addresses where the sent polys are stored currently. |
| to            | [ (Text, Int)] | A list of addresses and amounts. For each pair in the list the given amount of polys is sent to the account. |
| changeAddress | Text           | The address where the unspent polys are returned.            |
| fee           | Int            | The number of polys to be sent.                              |

##### `User_Revoke`

A choice where the operator is the controller. It archives the user contract.

###### Parameters

None.

### Organization Contracts

#### `AssetCreator`

A contract that allows the creation of a certain asset. The contract is parametrized by an organization, an address, a list of members and the asset in question.

**Module:** Topl.Organization

##### Parameters

| Name     | Type      | Description                                                  |
| -------- | --------- | ------------------------------------------------------------ |
| operator | Party     | The operator that will provide the asset creation service.   |
| orgId    | Text      | An unique identifier that identifies the owner of this asset creator contract. |
| address  | Text      | The Base58 address used by this operator to perform its operations. |
| members  | [Party ]  | The  list of members that can create an asset.               |
| asset    | AssetCode | The asset code of the asset being created.                   |

##### Choices

##### `MintAsset`

Mints an asset in the blockchain. It does so by creating an `AssetMintingRequest` contract.

###### Parameters

| Name           | Type          | Description                                                  |
| -------------- | ------------- | ------------------------------------------------------------ |
| requestor      | Party         | The party that requested the minting. It has to be a part of the list of members. |
| quantity       | Int           | The quantity of assets to be minted.                         |
| someCommitRoot | Optional Text | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text | Some metadata or `None` if no metadata is needed.            |
| someFee        | Optional Int  | Some fee to perform the transaction. If no fee is provided, the default is 100. |

#### `Organization`

A contract representing an organization. An organization is a list of members that share `AssetCreators` and `AssetIOUs`. Thus, all members of the organization can mint the collectively owned assets or modify them.

**Module:** Topl.Organization

##### Parameters

| Name              | Type                     | Description                                                  |
| ----------------- | ------------------------ | ------------------------------------------------------------ |
| orgId             | Text                     | The unique identifier of the organization. This identifier is treated as a database key. It needs to be unique for a specific operator. |
| orgName           | Text                     | The human readable name of the organization.                 |
| address           | Text                     | The Base58 address used by this organization to perform its operations. |
| wouldBeMembers    | [ Party ]                | List of parties that received an invitation to be part of the organization. |
| members           | [Party ]                 | The  list of members that are members of the organization.   |
| assetCodesAndIous | [ (AssetCode, [Text] ) ] | A list of pairs representing the list of assets paired to the codes of their current IOU contracts. |

##### Choices

##### `Organization_InviteMember`

The operator can choose to invite a member to the organization.

###### Parameters

| Name    | Type  | Description                            |
| ------- | ----- | -------------------------------------- |
| invitee | Party | The party invited to the organization. |

##### `Organization_AddSignedAssetMinting`

This choice allows the operator to take a `SignedAssetMinting` contract and transform it to an IOU. After the its execution the `SignedAssetMinting` is archived and the organization's `assetCodesAndIous` field is updated with a new code.

###### Parameters

| Name                  | Type                          | Description                                                  |
| --------------------- | ----------------------------- | ------------------------------------------------------------ |
| iouIdentifier         | Text                          | The identifier of an asset IOU. This identifier is intended to the key to this IOU on the client side. |
| signedAssetMintingCid | ContractId SignedAssetMinting | The signed minting request to be transformed to IOU contract. |

##### `Organization_AddSignedAssetTransfer`

This choice allows the operator to take a `SignedAssetTransfer` contract and transform it to an IOU. After the its execution the `SignedAssetTransfer` is archived and the organization's `assetCodesAndIous` field is updated with a new code.

###### Parameters

| Name                   | Type                           | Description                                                  |
| ---------------------- | ------------------------------ | ------------------------------------------------------------ |
| iouIdentifier          | Text                           | The identifier of an asset IOU. This identifier is intended to the key to this IOU on the client side. |
| signedAssetTransferCid | ContractId SignedAssetTransfer | The signed transfer request to be transformed to IOU contract. |

##### `Organization_Update`

This choice is executed by the operator to update the different contracts related to the organization. In particular, when a member is added all invitations need to be updated, as well as the asset creators and IOUs. After this choice is executed all contracts are modified to reflect all members.

###### Parameters

None.

##### `Organization_CreateAsset`

A member of the organization can exercise this choice create a new AssetCreator contract.

###### Parameters

| Name      | Type  | Description                                                  |
| --------- | ----- | ------------------------------------------------------------ |
| requestor | Party | The party that requests the creation of the asset. Needs to be a member. |
| version   | Int   | The version of the asset. Only version 1 is currently supported by the Topl blockchain. |
| shortName | Text  | The short name of the asset.                                 |

#### `MembershipAcceptance`

A contract that states that the operator and the invitee agree to add the user to some organization.

**Module:** Topl.Organization

##### Parameters

| Name     | Type     | Description                                  |
| -------- | -------- | -------------------------------------------- |
| operator | Party    | The operator that handles the organization.  |
| invitee  | Party    | The party that accepted the invitation       |
| orgId    | Text     | The identifier of the organization.          |
| members  | [Party ] | The  list of members offered the invitation. |

##### Choices

##### `AddUserToOrganization`

By exericsing this choice the operator addes the user to the organization and then exercises the `Organization_Update` choice to update the organization and related contracts.

###### Parameters

None.

#### `MembershipOffer`

This contract is and invitation from the operator and the members of an organization to an invitee.

**Module:** Topl.Organization

##### Parameters

| Name     | Type     | Description                                  |
| -------- | -------- | -------------------------------------------- |
| operator | Party    | The operator that handles the organization.  |
| orgId    | Text     | The identifier of the organization.          |
| members  | [Party ] | The  list of members offered the invitation. |
| invitee  | Party    | The party being invited.                     |

##### Choices

##### `Membershp_Accept`

By exericsing this choice the invitee creates `MembershipAcceptance` contract that enables the operator to add the member to the `Organization` contract.

###### Parameters

None.

##### `Membership_Reject`

By exericsing this choice the invitee archives the `MembershipOffer`.

###### Parameters

None.

##### `MembershipOffer_Archive`

By exericsing this choice the operator archives the `MembershipOffer`.

###### Parameters

None.

#### `AssetIou`

Represents a contract between the members of an organization and the operator that states that the operator owes them certain asset.

**Module:** Topl.Organization

##### Parameters

| Name           | Type          | Description                                                  |
| -------------- | ------------- | ------------------------------------------------------------ |
| operator       | Party         | The operator that handles the organization.                  |
| orgId          | Text          | The identifier of the organization.                          |
| members        | [Party ]      | The  list of members offered the invitation.                 |
| address        | Text          | The address where the asset are stored.                      |
| iouIdentifier  | Text          | The unique identifier of the IOU. It is used by the client application to refer to it. |
| quantity       | Int           | The number of tokens.                                        |
| someMetadata   | Optional Text | Some metadata or `None` if no metadata is present.           |
| assetCode      | AssetCode     | The asset code                                               |
| someCommitRoot | Optional Text | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| boxNonce       | Int           | The nonce of the box where the asset is stored.              |

##### Choices

##### `AssetIou_UpdateAsset`

By exericsing this choice the requestor initiates the modification of an asset on chain through the creation of a `AssetTransferRequest`.

###### Parameters

##### 

| Name          | Type          | Description                                                  |
| ------------- | ------------- | ------------------------------------------------------------ |
| requestor     | Party         | Party that requests the update. Must me a member of the organization |
| newCommitRoot | Optional Text | A new commit root encoded in a base 58 string or `None` if there is not commit root. |
| newMetadata   | Optional Text | Some metadata or `None` if no metadata is present.           |
| someFee       | Int           | The address where the asset are stored.                      |
| iouIdentifier | Text          | Some fee to perform the transaction. If no fee is provided, the default is 100. |

### Transfer Contracts

#### `TransferRequest`

The transfer request contract models a request to transfer polys from one address to another.

**Module:** Topl.Transfer

##### Parameters

| Name          | Type           | Description                                                  |
| ------------- | -------------- | ------------------------------------------------------------ |
| operator      | Party          | The operator that will handle this request.                  |
| user          | Party          | The party that requested this transfer operation.            |
| from          | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the polys are going to be transferred. |
| to            | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| changeAddress | Text           | The address where the change will be sent after the transaction. |
| fee           | Int            | The amount of polys that are paid to perform this transaction. |

##### Choices

##### `TransferRequest_Accept`

Accepts the request for further processing by the operator.

###### Parameters

| Name     | Type | Description                                                  |
| -------- | ---- | ------------------------------------------------------------ |
| txToSign | Text | The transaction to be signed serialized by the `PolyTransferSerializer` and encoded in Base58. |
| boxNonce | Int  | The nonce of the box where the asset is going to be stored.  |

##### `TransferRequest_Reject`

Rejects the request.

###### Parameters

None.

##### `TransferRequest_Archive`

Archives the request. Archive implies that the archiving was not done because of an error.

###### Parameters

None.

#### `UnsignedTransfer`

An unsigned asset transfer. It is created when a `TransferRequest_Accept` is exercise in an `TransferRequest`. In includes all the data of the `AssetTransferRequest` and also the transaction to sign  encoded in Base58 and the box nonce. 

**Module:** Topl.Transfer

##### Parameters

| Name     | Type           | Description                                                  |
| -------- | -------------- | ------------------------------------------------------------ |
| operator | Party          | The operator that will handle this request.                  |
| user     | Party          | The party that requested this transfer operation.            |
| from     | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the polys are going to be transferred. |
| to       | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| txToSign | Text           | The transaction to be signed serialized by the `PolyTransferSerializer` and encoded in Base58. |

##### Choices

##### `UnsignedTransfer_Sign`

Signs the request for broadcasting to the network by the operator.

###### Parameters

| Name     | Type | Description                                                  |
| -------- | ---- | ------------------------------------------------------------ |
| signedTx | Text | The signed transfer transaction serialized by the `AssetTransferSerializer` and encoded in Base58. |

##### `UnsignedTransfer_Archive`

Archives the signing request.

###### Parameters

None.

#### `SignedTransfer`

A signed transfer request. It is created when a `UnsignedTransfer_Sign` is exercised in an `UnsignedTransfer`. In includes all the data of the `UnsignedTransfer` and also the signed transaction encoded in Base58 and a send status. The send status informs the current status of the transfer request with respect to the network. 

**Module:** Topl.Transfer

##### Parameters

| Name           | Type           | Description                                                  |
| -------------- | -------------- | ------------------------------------------------------------ |
| operator       | Party          | The operator that will handle this request.                  |
| user           | Party          | The party that requested this minting operation.             |
| from           | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the assets are going to be minted. |
| to             | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| txToSign       | Text           | The transfer transaction to be signed serialized by the `PolyTransferSerializer` and encoded in Base58. |
| changeAddress  | Text           | The address where the change will be sent after the transaction. |
| assetCode      | AssetCode      | The code of the asset being minted                           |
| quantity       | Int            | The number of assets to mint.                                |
| someCommitRoot | Optional Text  | A commit root encoded in a base 58 string or `None` if there is not commit root. |
| someMetadata   | Optional Text  | Some metadata or `None` if no metadata is needed.            |
| boxNonce       | Int            | The nonce of the box where the asset is going to be stored.  |
| fee            | Int            | The amount of polys that are paid to perform this transaction. |
|                |                |                                                              |
| signedTx       | Text           | The signed transfer transaction serialized by the `AssetTransferSerializer` and encoded in Base58. |
| sendStatus     | SendStatus     | The status of the transaction. It can be: New, Pending, Sent, FailedToSend, and Confirmed. |

##### Choices

##### `SignedTransfer_Send`

Marks the transaction as `Pending`.

###### Parameters

None.

##### `SignedTransfer_Sent`

Marks a pending transaction with a new status

###### Parameters

| Name          | Type       | Description                                                  |
| ------------- | ---------- | ------------------------------------------------------------ |
| newSendStatus | SendStatus | The new status of this transaction after being sent to the network. |

##### `SignedTransfer_Fail`

Marks this transaction as failed

###### Parameters

| Name   | Type | Description                |
| ------ | ---- | -------------------------- |
| reason | Text | The reason of the failure. |

##### `SignedAssetTransfer_Confirm`

Marks this transaction as confirmed.

###### Parameters

| Name  | Type | Description                                           |
| ----- | ---- | ----------------------------------------------------- |
| txId  | Text | The identifier of this transaction in the blockchain. |
| depth | Int  | The depth at which the transaction is done.           |

##### `SignedTransfer_Archive`

Archives the contract.

###### Parameters

None.

[^1]: https://docs.daml.com/concepts/glossary.html#participant-node
[^2]: https://docs.daml.com/concepts/glossary.html#party

