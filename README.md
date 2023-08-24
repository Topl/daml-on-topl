# `daml-on-topl`

`daml-on-topl` is a set of projects to integrate DAML with the Topl blockchain.
Currently, the project includes two subprojects:

- `daml-topl-lib`
- `daml-topl-broker`

## `daml-topl-lib`

`daml-topl-lib` is a set of [DAML Modules](https://www.digitalasset.com/developers) for interfacing with the Topl blockchain and the DAML ledger.

## `daml-topl-broker`

`daml-topl-broker` is a broker that connects to the DAML ledger and the Topl blockchain. It is responsible for listening to the DAML ledger for transactions and sending them to the Topl blockchain. It also listens to the Topl blockchain for transactions and sends them to the DAML ledger.
