## Running Included Applications

Before running the applications make sure to follow the [how to setup the environment](setupenv.md) instructions. You also need docker installed to run the applications on a local Topl node.

### 0. Running a Local Bifrost Node

From the terminal run the following command:

```shell
$ docker run -p 9085:9085 toplprotocol/bifrost-node:1.10.2 --forge --disableAuth --seed test --debug
```

### 1. Running the Operator App

The operator app can be run with the following command:

```shell
$ mvn compile exec:java -Doperator
```

### 2. Running the Alice App

The Alice app will be signing the transactions to be sent to the network. For this, it needs some extra information: a keyfile and a password. By default, the keyfile is called `keyfile.json` and it must be at the root of the project to be loaded by the Alice App. The keyfile must be provided by the user. The password is also provided as an environment variable. The following variables must then be exported before:

```bash
export KEY_PASSWORD="<keyfile password>"
```

Once the variables are exported and the keyfile is at the right place, we can run the following command:

```shell
$ mvn compile exec:java -Dalice
```

