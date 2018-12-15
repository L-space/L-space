[![Gitter chat](https://badges.gitter.im/gitterHQ/gitter.png)](https://gitter.im/L-space/L-space)
[![Waffle.io - Columns and their card count](https://badge.waffle.io/L-space/L-space.svg?columns=all)](https://waffle.io/L-space/L-space)

[![Build Status](https://travis-ci.org/L-space/L-space.svg)](https://travis-ci.org/L-space/L-space)
[![codecov](https://codecov.io/gh/L-space/L-space/branch/master/graph/badge.svg)](https://codecov.io/gh/L-space/L-space)
[![](https://jitpack.io/v/eu.l-space/l-space.svg)](https://jitpack.io/#eu.l-space/l-space)
[![Release](https://jitpack.io/v/eu.l-space/l-space.svg)](https://jitpack.io/#eu.l-space/l-space)

# L-space
Unlimited Linked (Open) Data, traversing and reading the data multiverse: "Scientia potentia est"

L-space, a graph computing framework for Scala.

L-space is a graph computing framework WIP with a heavy focus towards Linked (Open) Data. 
The name is inspired by the L-space concept portrayed by Terry Pratchett's Discworld: 
"Because L-space links every library, it is possible to reach any one of these throughout space, 
time and the multiverse (Linked Data?)." Travelling through L-space requires knowledge and ability. 
Hence, steps are defined to instruct the Librarian how to travel.

## Getting started

L-space modules are available via jitpack.io. Add it in your `build.sbt` at the end of resolvers:
```
resolvers += "jitpack" at "https://jitpack.io"
```
L-space is available for Scala 2.11.x and 2.12.x. 
To include `lspace-core` (all core structures and basic implementations (in-memory) of the graph, 
data-models and traversal-engines) add the following to your `build.sbt`:
```
libraryDependencies += "eu.l-space" %% "lspace-core" % "{version}"
```

## Modules

L-space provides additional modules for remote traversals, parsing io, rest-api templates and tinkerpop-adapters.

- `lspace-parse`: parsers for linked data
- `lspace-client`: clients to communicatie with other graph(-services)
- `lspace-graph`: implementation for persistence and indexing remote (e.g. Cassandra, Elasticsearch, HDFS, ...)
- `lspace-services`: basic implementation for a rest service which accepts Librarian queries in Json-LD and returns results in Json-LD
```
libraryDependencies += "eu.l-space" %% "{lspace-xxx}" % "{version}"
```

## Examples
`examples` on how to use the project modules in various environments and 
sample-environments (e.g. Jupyter-notebook with samples)

## Acknowledgments
* This project uses as few libraries as possible, the main libraries for core, parse, client and services are:
  * [Shapeless](https://github.com/milessabin/shapeless/wiki)
  * [Monix](https://monix.io/)
  * [Finch](https://finagle.github.io/finch/)
  * [Argonaut](http://argonaut.io/)