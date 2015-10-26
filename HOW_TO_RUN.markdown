# How to run this code

### Run sbt install script

```
$ chmod +x sbt_install.sh
$ ./sbt_install.sh
```
### Run sbt

Please look in the official [documentation of sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Running.html) for more commands.

```
Running with SBT:
sbt -Dconfig.file=benchmark/debug/cfabcast-debug.conf 'run-main Main node1'

Running with uber jar:
java -cp target/scala-2.11/CFABCast-assembly-0.1-SNAPSHOT.jar -Dconfig.file=benchmark/debug/cfabcast-debug.conf Main node1
```
