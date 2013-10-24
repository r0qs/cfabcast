#!/bin/bash

# This script download and extract the sbt 0.13.0 file, removing the .tgz and put uncompressed file in ./sbt directory.

# Over proxy, uncomment this line with your proxy settings
#export http_proxy="http://proxy.ufu.br:3128"

wget -c -O - http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.0/sbt.tgz | tar -xvzf -
