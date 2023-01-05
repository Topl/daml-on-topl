## Setup Environment

### 1. Environment

To run the daml-bifrost-module, the DAML SDK (version >  2.3.2) must be installed on your system. The Java app is a mixed Java and Scala project, that uses Maven as the build tool. You need Maven installed to compile and execute the various targets. 

### 2. Compile

Three steps are required to compile:

1. Build the DAML module. This will compile the DAML code to a DAR file. This step is required for the code generation. This is done with the `daml` tool:

```shell
$ daml build
```

2. Generate the Java code. This will generate the Java classes corresponding to the DAML contracts.

```shell
$ mvn clean
# some response [..]
$ daml codegen java
```

3. Compile the Java/Scala code. Please do not clean here because it will remove the generated Java code.

```shell
$ mvn compile
```

### 3. Run the DAML sandbox and navigator

To test the code, we need a server implementing the DAML Ledger API. This server is called the DAML sandbox. To run it we execute the command:

```shell
$ daml start
```

This command will also start the Navigator. A component that allows to see the status of the DAML ledger. Upon initialization, the DAML sandbox will execute the initialize script in the `Main.daml` file. The script is shown below:

```haskell
initialize = do
      operator <- allocateParty "Operator"
      operatorId <- validateUserId "operator"
      alice <- allocateParty "Alice"
      aliceId <- validateUserId "alice"
      operatorCid <- submit operator do
        createCmd Operator with operator
      userInvitationCid <- submit operator do
        exerciseCmd operatorCid  Operator_InviteUser 
          with user = alice
      aliceUserCid <- submit alice do
        exerciseCmd userInvitationCid UserInvitation_Accept
      createUser (Daml.Script.User aliceId (Some alice)) [CanActAs alice]
      createUser (Daml.Script.User operatorId (Some operator)) [CanActAs operator]
      pure [alice]
```

The script creates two parties, Alice and Operator, and the executes the onboarding flow. After this script is run, we can directly exercise the choices in the `User` contract.

