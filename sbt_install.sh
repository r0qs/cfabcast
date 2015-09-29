#!/bin/bash

# This script download and extract the sbt 0.13.0 file, removing the .tgz and put uncompressed file in ./sbt directory.

# Over proxy, uncomment this line with your proxy settings
#export http_proxy="http://proxy.ufu.br:3128"

wget -c -O - https://dl.bintray.com/sbt/native-packages/sbt/0.13.9/sbt-0.13.9.tgz | tar -xvzf -
