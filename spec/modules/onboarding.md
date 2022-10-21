### Onboarding Contracts

#### `Operator`

The operator contract is a contract signed uniquely by the operator. It includes as a parameter the operator’s address.

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

A choice where the user is the controller. On rejection, the invitation is archived.

###### Parameters

None.

##### `UserInvitation_Cancel`

A choice where the operator is the controller. The controller can revoke the invitation. On cancel, the invitation is archived.

###### Parameters

None.

#### `User`

The `User` contract allows a user to ask an operator to transfer polys on the user’s behalf.

**Module:** Topl.Onboarding

##### Parameters

| Name     | Type  | Description                                                  |

| -------- | ----- | ------------------------------------------------------------ |

| operator | Party | The operator that will perform operations on behalf of the user. |

| user     | Party | The user that agrees that the operator performs operations on the user’s behalf. |

##### Choices

##### `User_SendPolys`

A choice where the user is the controller. When this choice is exercised, a new `TransferRequest` contract is created and the operator software processes the transaction. This is a nonconsuming choice, and thus it can be exercised without archiving the `User` contract.

###### Parameters

| Name          | Type           | Description                                                  |

| ------------- | -------------- | ------------------------------------------------------------ |

| from          | [Text]         | A list of addresses where the sent polys are stored currently. |

| to            | [ (Text, Int)] | A list of addresses and amounts. For each pair in the list, we send the given amount of polyst to the account. |

| changeAddress | Text           | The address where the unspent polys are returned.            |

| fee           | Int            | The number of polys to be sent.                              |

##### `User_Revoke`

A choice where the operator is the controller. It archives the user contract.

###### Parameters

None.

### 


