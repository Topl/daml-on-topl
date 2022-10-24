### Operator Processors

#### `AssetIouProcessor`

This is a generic processor that allows the to execute the `callback` when an `AssetIou` contract is created in the DAML participant node. It can be used for example to create a database entry when an `AssetIou` contract. It does not perform any action on the DAML side.

#### `MembershipAcceptanceProcessor`

This processor automatically exercises the `AddUserToOrganization` choice when a `MembershipAcceptance` contract is created. It is used by the operator to add the user to the organization.

#### `MembershipOfferProcessor`

This processor automatically exercises the `Membershp_Accept` choice when the `MembershipOffer` contract is created. This is done when the user can automatically be added to the organization by the operator. To have the user accept the invitation instead, another processor needs to be implemented.

