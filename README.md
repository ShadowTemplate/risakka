# Risakka

A didactic implementation of [Raft](
https://en.wikipedia.org/wiki/Raft_(computer_science)) consensus algorithm in 
[Akka](http://akka.io/).

---
## Information

**Status**: `Completed`

**Type**: `Academic project`

**Course**: `Distributed Systems`

**Development year(s)**: `2016-2017`

**Authors**: [gforresu](https://github.com/gforresu) (journaling, failback, 
conf), [SaraGasperetti](https://github.com/SaraGasperetti) (Raft client and 
server), [ShadowTemplate](https://github.com/ShadowTemplate) (Raft server, GUI)

---
## Getting Started

In order to execute Raft it is necessary to run both a server and some clients.
Their configurations can be customized in the appropriate resources files (IP
addresses, names, timeout, log paths). Alternatively, it is possible to run the
algorithm with the default settings.

To launch several clients, assign to each one a different combination of 
*hostname* and *port* in the ```client_configuration.conf``` file.

### Prerequisites

Clone the repository:

```
$ git clone https://github.com/ShadowTemplate/risakka.git
```

### Installing

Compile both the server and the client:

```
$ cd risakka
$ mvn clean install
```

Run the server:

```
$ mvn exec:java -Dexec.mainClass="risakka.cluster.ClusterManager"
```

Run the clients:

```
$ mvn exec:java -Dexec.mainClass="risakka.raft.actor.RaftClient"
```

---
## Building tools

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) - 
Programming language
* [Akka](http://akka.io/) - Actor model toolkit
* [Maven](https://maven.apache.org/) - Build automation
* [Lombok](https://projectlombok.org/) - Reduce Java boilerplate

---
## Contributing

This project is not actively maintained and issues or pull requests may be 
ignored.

---
## License

This project is licensed under the GNU GPLv3 license.
Please refer to the [LICENSE.md](LICENSE.md) file for details.

---
*This README.md complies with [this project template](
https://github.com/ShadowTemplate/project-template). Feel free to adopt it
and reuse it.*
