#!/bin/bash

# This script download and extract the sbt 0.13.9 file, removing the .tgz and put uncompressed file in ./sbt directory.

# Requires Java runtime!

# Over proxy, uncomment this line with your proxy settings
#export SBT_OPTS="$SBT_OPTS -Dhttp.proxyHost=proxy.ufu.br -Dhttp.proxyPort=3128"

wget -c -O - https://dl.bintray.com/sbt/native-packages/sbt/0.13.9/sbt-0.13.9.tgz | tar -xvzf -

sleep 5

sbt/bin/sbt compile
