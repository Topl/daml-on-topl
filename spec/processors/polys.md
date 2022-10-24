### Polys Processors

#### `SignedTransferProcessor`

This processor processes the signed transfer requests. It reacts to the creation of the `SignedTransfer` contract in the DAML participant node. This processor handles the `SignedTransfer` contract in its various possible states. 

If the `SignedTransfer` is in `Pending` state, the transaction is broadcast to the network. If the broadcast of the transaction succeeds it exercises the `SignedTransfer_Sent` choice on the `SignedTransfer` contract.  If the processor does not succeed in the broadcast of the transaction it exercises the `SignedTransfer_Fail` choice. After the exercise of `SignedTransfer_Sent`, a new `SignedTransfer` contract is created with status `Sent`.

If the `SignedTransfer` is in `Sent` state, the current implementation executes the `SignedTransfer_Archive` choice, which archive the transfer.

#### `TransferRequestProcessor`

This processor processes the transfer requests. It reacts to the creation of the `TransferRequest` contract in the DAML participant node. If the creation of a transaction succeeds it exercises the `TransferRequest_Accept` choice on the `TransferRequest` contract.  If the processor does not succeed in the creation of the transaction it exercises the `TransferRequest_Reject` choice.

The transaction is serialized and then encoded in Base58 before being passed to the `TransferRequest_Accept` choice. 

#### `UnsignedTransferProcessor`

This processor processes the unsigned transfer requests. It reacts to the creation of the `UnsignedTransfer` contract. It takes the transaction created and serialized by the `TransferRequestProcessor` , signs it and exercises `UnsignedAssetTransfer_Sign` choice on the contract, which creates `SignedTransfer`.

The signed transaction is serialized and encoded in Base58 to be passed to the  `UnsignedAssetTransfer_Sign` choice.

