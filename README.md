# daml-bifrost-module

daml-bifrost-module is a set of [DAML Modules](https://www.digitalasset.com/developers) for interfacing with the Topl blockchain and the DAML ledger. It includes a reference application. The App is implemented in Java using DAML's Java bindings for interaction with the DAML ledger, and brambl for using bifrost gRPC API. The code, design and documentation of this library is heavily inspired by [Hemera](https://github.com/liakakos/hemera), a DAML Library for Ethereum integration.

## The DAML Topl Library

The DAML Topl library is meant for a single operator and multiple user parties. The operator and the users establish a contract which allows the users to request the execution of certain operations by the operator through exercising their choices (rights) on the contract. The operator, on the other hand, performs operations on the Topl network and gets the results in the form of contract responses.

### Onboarding

The possible interactions among users in DAML are defined through contracts. For a user to be able to request something from an operator, there must be a contract that establishes what can be rights can be exercised by the user and the operator. In this case, the contract is the `User` template. To sign the `User` contract, the operator first creates an instance of the `Operator` template, exercises the `Operator_InviteUser` choice. This creates `UserInvitation` contract, which is accepted by the user by exercising the choice `UserInvitation_Accept`. The latter choice creates the `User` contract.

### Transfer

If the user wants to send polys to an address they use the Transfer module. The mechanics is the following:

- On the `User` contract, the user exercises the choice `User_SendPolys` with the data for the transfer (from and to addresses, amount, fees, etc), which creates a `TransferRequest` contract,
- The `TransferRequest` is accepted by the operator and an `UnsignedTransfer` is created.
- The `UnsignedTransfer` is then signed by the user by exercising the `UnsignedTransfer_Sign` choice, creating a `SignedTransfer`.
- Finally, the controller broadcasts the `SignedTransfer`to the network.


