# Specification

[TOC]

## Goal

The goal of this document is to enable the reader to implement the different processors and APIs needed to allow DAML contracts to interact with the Topl blockchain.

## Prerequirements

The reader of this document must be familiar with DAML, the Topl blockchain, and their basic respective concepts. 

## Definitions

Most definitions in this section come from the [DAML glossary](https://docs.daml.com/concepts/glossary.html). We included them here to improve the readability of the document.

**Participant node.-** The participant node is a server that provides users consistent programmatic access to a ledger through the Ledger API. The participant nodes handle transaction signing and validation, such that users don’t have to deal with cryptographic primitives but can trust the participant node that the data they are observing has been properly verified to be correct.[^1]

**Party.**- A party represents a person or legal entity. Parties can create contracts and exercise choices. Signatories, observers, controllers, and maintainers all must be parties, represented by the Party data type in DAML and determine who may see contract data. We host parties on participant nodes and a participant node can host over one party. A party can be hosted on several participant nodes simultaneously. [^2]

**Processor.-** A processor is a listener installed in the client application to respond to events happening in the participant node.

## Overview

### DAML

DAML is a domain-specific language and a platform for the development of multi-party applications. In particular, DAML allows modeling of real world contracts ergonomically using functional constructs.

The domain-specific language allows the definition of multi-party contracts, which mirror real-world contracts. In particular, DAML contracts can have signatories, observers, controllers and other constructs that come from the domain of real-world contracts. We upload these contracts to a participant node, which provides an API to interact with the different contracts and executes their semantics.

A multi-party application, from DAML’s point of view, is an application that relies on DAML contracts and their semantics to govern the different multi-party interactions. Other modules of the application handle other interactions.

### Topl

Topl Blockchain is an implementation of the blockchain technology focused on sustainability.It offers a native token (the poly or level) and the possibility of minting native assets, which can be traded and stored in the blockchain.

### DAML-Topl integration

A multi-party application that uses DAML contracts on top of the Topl blockchain needs to model the blockchain primitives as DAML contracts and provide glue-code to interact with the participant node. 

DAML provides two ways to interact with the participant node:

- A gRPC API that allows to execute different contract operations as the creation of contracts and the execution of choices

- A reactive API that allows to react to events in the participant node. This API also allows to send commands to the participant node in response to different events.

The former API allows the application to start operations in DAML on behalf of the different parties. The latter allows the application to react to the events generated when the DAML semantics is executed. 

We assume that the user starts operations in the participan node using directly the gRPC API, thus; the library does not provide wrappers around this API.

The library defines a standard flow for transfer of polys, and minting and transfer of assets. 

## Use cases

The library fulfills the three following use cases:

- Transfer of polys from one address to another address on behalf of a party

- Minting and transfer of assets in standalone fashion

- Minting and transfer of assets as part of an organization

### Transfer of polys from one address to another

We structured the transfer of polys from one party to another in three steps:

- Request

- Signing

- Transaction broadcast

The reason for this separation is flexibility. Indeed, this setup allows three different parties to perform each of the steps separately. This also allows the same party to perform all three steps.

### Minting and transfer of assets in standalone fashion

We structure the minting and transfer of assets in the same way as the poly transfer:

- Request

- Signing

- Transaction broadcast

The reason is the same. We want to have maximum flexibility and allow each operation to be performed separately.

### Minting and transfer of assets as part of an organization

The organization module is a set of contracts that allow to a list of parties to share an asset and perform operations on it through an IOU contract. The IOU contract is a DAML contract that models the collective ownership of a asset by a set of parties delimited by the organization.

The organization contract models an organization that can have members and manage different assets. All members of an organization can mint and transfer assets and also create new assets.

## DAML contracts architecture

### Participants

We implemented the DAML contracts assuming the existence of certain participants or parties.

#### Operator

The operator party represents an application that performs operations on behalf of a user. For example, given an application that allows to send polys from one address to another, the operator is the software that will actually perform the operations on the blockchain. Depending on the implementation, the operator can perform all steps in a transaction: requesting, signing and broadcasting. 

#### Organization

An organization is composed of a set of parties (members), and an operator that performs operations on behalf of the organization. In our implementation, all members of the organization may request operations for the operator to perform. The operations are asset related, i.e. minting and modification of security root and metadata.

### Modules

We organized the DAML contracts in 5 modules, each module being a DAML file. The modules are:

- Asset

- Onboarding

- Organization

- Transfer

- Utils

Additional DAML files are in the code base are meant for testing (stored under the Tests folder), and for sandbox initialization (`Main.daml`).

#### Asset

The Asset module contains the code for the [Minting and transfer of assets in standalone fashion](#minting-and-transfer-of-assets-in-standalone-fashion). This module allows the user to mint and transfer assets on the blockchain. This module only depends on the Utils module. It contains contracts modeling a minting/transfer request, an unsigned minting/transfer, and a signed minting/transfer in different states.

#### Onboarding

The Onboarding module contains the code for the operator and user contract. The module contains three contracts. One contract is for the operator itself, that allows the software to create the other contracts. The two others model an invitation to another party to become a user, and the user contract. The user contract contains the operations that the user can ask the operator to perform. Transfer of polys from one address to another is the only operation supported by the contract.

#### Organization

The organization module contains the contracts that are necessary to handle the organization. It includes the Organization contract itself as well as the invitations to become a member, the acceptance contract, the asset creation contract and the IOUs contracts modelling the ownership of assets in the blockchain.

#### Transfer

The transfer module contains the code for the [Transfer of polys from one address to another](#transfer-of-polys-from-one-address-to-another). This module allows the user to start a transfer of polys on the blockchain. In contains contracts modeling transfer requests, unsigned transfer and signed transfer in different states.

#### Utils

The Utils module contains the supporting functions and data structures used in the other modules.

## Contracts

In this section, we present the different contracts available in our implementation. We present each contract with its parameters and choices. The data types are the standard data types for DAML contracts. `Text` is equivalent to Java `String`and `[ Type ]` is equivalent to a list of the type and `Int` is equivalent to a Java `Long`.

- [Assets](modules/assets.md)

- [Onboarding](modules/onboarding.md)

- [Organization](modules/organization.md)

- [Transfer](modules/transfer.md)

[^1]: https://docs.daml.com/concepts/glossary.html#participant-node

[^2]: https://docs.daml.com/concepts/glossary.html#party


