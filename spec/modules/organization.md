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

By exercising this choice the operator addes the user to the organization and then exercises the `Organization_Update` choice to update the organization and related contracts.

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

##### `MembershipOffer_Accept`

By exercising this choice the invitee creates `MembershipAcceptance` contract that enables the operator to add the member to the `Organization` contract.

###### Parameters

None.

##### `MembershipOffer_Reject`

By exercising this choice the invitee archives the `MembershipOffer`.

###### Parameters

None.

##### `MembershipOffer_Archive`

By exercising this choice the operator archives the `MembershipOffer`.

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

By exercising this choice the requestor initiates the modification of an asset on chain through the creation of a `AssetTransferRequest`.

###### Parameters

##### 

| Name          | Type          | Description                                                  |
| ------------- | ------------- | ------------------------------------------------------------ |
| requestor     | Party         | Party that requests the update. Must me a member of the organization |
| newCommitRoot | Optional Text | A new commit root encoded in a base 58 string or `None` if there is not commit root. |
| newMetadata   | Optional Text | Some metadata or `None` if no metadata is present.           |
| someFee       | Int           | The address where the asset are stored.                      |
| iouIdentifier | Text          | Some fee to perform the transaction. If no fee is provided, the default is 100. |

### 