# **Specification**

## Goal

The goal of this document is to enable the reader to implement the different processors and APIs needed to allow DAML contracts to interact with the Topl blockchain.

## Prerequirements

The reader of this document must be familiar with DAML and its basic concepts.

## **Definitions**

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

- Transfer of polys from one party to another
- Minting and transfer of assets in standalone fashion
- Minting and transfer of assets as part of an organization

### Transfer of polys from one party to another

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

## Flow

## **Processors**



[^1]: https://docs.daml.com/concepts/glossary.html#participant-node

[^2]: https://docs.daml.com/concepts/glossary.html#party

