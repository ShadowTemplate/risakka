# risakka
A didactic implementation of Raft consensus algorithm in Akka

## Setup

Clone the project and build it:
* ```git clone https://github.com/ShadowTemplate/risakka.git```
* ```cd risakka```
* ```mvn clean install```

#### How to run the server
* ```mvn exec:java -Dexec.mainClass="risakka.cluster.ClusterManager"```

#### How to run the client
* ```mvn exec:java -Dexec.mainClass="risakka.raft.actor.RaftClient"```

To launch several clients, assign to each one a different combination of ```hostname``` and ```port``` variables in the ```client_configuration.conf``` file. 
