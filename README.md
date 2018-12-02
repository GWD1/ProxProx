# ProxProx

ProxProx is a Proxy written in pure Java. It allows creating Plugins and moving Players between all sorts
of PE Servers without the need of third party plugins inside them.

The API currently is a bit thin but you can work on the project and contribute to it so we can grow the API together.

## Project

Build Version | Builds
------------ | -------------|
Master Build | [Latest Build on Jenkins](ci.gomint.io/job/ProxProx/job/master/)

## Testing

Currently the project is in testing only mode. There is no "download the jar and run" mode. You need to setup an IDE or
run the build process via Maven.

## License

The code found in this repository is licensed under a 3-clause BSD license. See the LICENSE file for further
details.

## Documentation

There currently is no documentation as things are still changing frequently.

## TODO List 

* Documentation (JavaDocs) for every piece of Code inside of the api Package
* Events for Chat
* Working Playerlist manipulation, currently switching Servers will cause corruption in the List
* Code cleanup inside of the Down and Upstream Connections (like abstract the read Threads into Executorpools)
* Better Debug interface, something like Watchdog for CPU, Network Trace for RakNet, both configurable
