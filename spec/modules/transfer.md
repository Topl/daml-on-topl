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
| txToSign | Text | The transaction to be signed serialized by the `PolyTransferSerializer` and encoded in JSON. |
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

An unsigned asset transfer. It is created when a `TransferRequest_Accept` is exercise in an `TransferRequest`. In includes all the data of the `AssetTransferRequest` and also the transaction to sign  encoded in JSON and the box nonce. 

**Module:** Topl.Transfer

##### Parameters

| Name     | Type           | Description                                                  |
| -------- | -------------- | ------------------------------------------------------------ |
| operator | Party          | The operator that will handle this request.                  |
| user     | Party          | The party that requested this transfer operation.            |
| from     | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the polys are going to be transferred. |
| to       | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| txToSign | Text           | The transaction to be signed serialized by the `PolyTransferSerializer` and encoded in JSON. |

##### Choices

##### `UnsignedTransfer_Sign`

Signs the request for broadcasting to the network by the operator.

###### Parameters

| Name     | Type | Description                                                  |
| -------- | ---- | ------------------------------------------------------------ |
| signedTx | Text | The signed transfer transaction serialized by the `AssetTransferSerializer` and encoded in JSON. |

##### `UnsignedTransfer_Archive`

Archives the signing request.

###### Parameters

None.

#### `SignedTransfer`

A signed transfer request. It is created when a `UnsignedTransfer_Sign` is exercised in an `UnsignedTransfer`. In includes all the data of the `UnsignedTransfer` and also the signed transaction encoded in JSON and a send status. The send status informs the current status of the transfer request with respect to the network. 

**Module:** Topl.Transfer

##### Parameters

| Name       | Type           | Description                                                  |
| ---------- | -------------- | ------------------------------------------------------------ |
| operator   | Party          | The operator that will handle this request.                  |
| user       | Party          | The party that requested this transfer operation.            |
| from       | [ Text ]       | A list of from addresses encoded in Base58. The addresses from where the polys or LVLs are going to be transferred. |
| to         | [ (Text, Int)] | A list of pairs containing the addresses and the amounts to be transferred to the to addresses. |
| txToSign   | Text           | The transfer transaction to be signed serialized by the `PolyTransferSerializer` and encoded in JSON. |
| signedTx   | Text           | The signed transfer transaction serialized by the `AssetTransferSerializer` and encoded in JSON. |
| sendStatus | SendStatus     | The status of the transaction. It can be: New, Pending, Sent, FailedToSend, and Confirmed. |

##### Choices

##### `SignedTransfer_Send`

Marks the transaction as `Pending`.

###### Parameters

None.

##### `SignedTransfer_Confirm`

Marks this transaction as confirmed.

###### Parameters

| Name  | Type | Description                                           |
| ----- | ---- | ----------------------------------------------------- |
| txId  | Text | The identifier of this transaction in the blockchain. |
| depth | Int  | The depth at which the transaction is done.           |

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