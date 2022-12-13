### Asset Processors

Asset processors handle and process contracts related with the minting and transfer of assets.

#### `AssetMintingRequestProcessor`

This processor processes the minting requests. It reacts to the creation of the `AssetMintingRequest` contract in the DAML participant node. If the creation of a transaction succeeds it exercises the `MintingRequest_Accept` choice on the `AssetMintingRequest` contract. If the processor does not succeed in the creation of the transaction it exercises the `TransferRequest_Reject` choice.

The transaction is serialized and then encoded in JSON before being passed to the `MintingRequest_Accept` choice. Also, this processor computes the new box nonce where the asset to be minted is stored.

#### `AssetTransferRequestProcessor`

This processor processes the transfer requests. It reacts to the creation of the `AssetTransferRequest` contract in the DAML participant node. If the creation of a transaction succeeds it exercises the `AssetTransferRequest_Accept` choice on the `AssetTransferRequest` contract.  If the processor does not succeed in the creation of the transaction it exercises the `TransferRequest_Reject` choice.

The transaction is serialized and then encoded in JSON before being passed to the `AssetTransferRequest_Accept` choice. Also, this processor computes the new box nonce where the asset to be transferred is stored.

#### `SignedAssetTransferRequestProcessor`

This processor processes the signed transfer requests. It reacts to the creation of the `SignedAssetTransfer` contract in the DAML participant node. This processor handles the `SignedAssetTransfer` contract in its various possible states. 

If the `SignedAssetTransfer` is in `Pending` state, the transaction is broadcast to the network. If the broadcast of the transaction succeeds it exercises the `SignedAssetTransfer_Sent` choice on the `SignedAssetTransfer` contract.  If the processor does not succeed in the broadcast of the transaction it exercises the `SignedAssetTransfer_Fail` choice. After the exercise of `SignedAssetTransfer_Sent`, a new `SignedAssetTransfer` contract is created with status `Sent`.

If the `SignedAssetTransfer` is in `Sent` state, the processor checks the confirmation status on the server until the confirmation threshold is met and then  executes the `SignedAssetTransfer_Confirm` choice, which creates a new  `SignedAssetTransfer` with status `Confirmed`.

If the `SignedAssetTransfer` is in `Confirmed` state, then the `Organization_AddSignedAssetTransfer` choice (from the `Organization` contract) is exercised. This choice creates an `AssetIou` contract to represent the asset in the system.

Errors during the broadcast exercise the `SignedAssetTransfer_Fail` choice.

#### `SignedMintingRequestProcessor`

This processor processes the signed minting requests. It reacts to the creation of the `SignedAssetMinting` contract in the DAML participant node. This processor handles the `SignedAssetMinting` contract in its various possible states. 

If the `SignedAssetMinting` is in `Pending` state, the transaction is broadcast to the network. If the broadcast of the transaction succeeds it exercises the `SignedAssetMinting_Sent` choice on the `SignedAssetMinting` contract.  If the processor does not succeed in the broadcast of the transaction it exercises the `SignedAssetMinting_Fail` choice. After the exercise of `SignedAssetMinting_Sent`, a new `SignedAssetMinting` contract is created with status `Sent`.

If the `SignedAssetMinting` is in `Sent` state, the processor checks the confirmation status on the server until the confirmation threshold is met and then executes the `SignedAssetMinting_Confirm` choice, which creates a new  `SignedAssetMinting` with status `Confirmed`.

If the `SignedAssetMinting` is in `Confirmed` state, then the `Organization_AddSignedAssetMinting` choice (from the `Organization` contract) is exercised. This choice creates an `AssetIou` contract to represent the asset in the system.

Errors during the broadcast exercise the `SignedAssetMinting_Fail` choice.

#### `UnsignedAssetTransferRequestProcessor`

This processor processes the unsigned transfer requests. It reacts to the creation of the `UnsignedAssetTransferRequest` contract. It takes the transaction created and serialized by the `AssetTransferRequestProcessor` , signs it and exercises `UnsignedAssetTransfer_Sign` choice on the contract, which creates `SignedAssetTransfer`.

The signed transaction is serialized and encoded in JSON to be passed to the  `UnsignedAssetTransfer_Sign` choice.

#### `UnsignedMintingRequestProcessor`

This processor processes the unsigned minting requests. It reacts to the creation of the `UnsignedAssetMinting` contract. It takes the transaction created and serialized by the `AssetTransferRequestProcessor` , signs it and exercises `UnsignedMinting_Sign` choice on the contract, which creates `SignedAssetMinting`.

The signed transaction is serialized and encoded in JSON to be passed to the  `UnsignedMinting_Sign` choice.
