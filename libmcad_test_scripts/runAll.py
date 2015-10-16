#!/usr/bin/python
# -*- coding: utf-8 -*-

import settings
import sys, os
from os.path import join, expanduser
from time import sleep

sshcmd = settings.sshcmd
sshcmdbg = settings.sshcmdbg
localcmd = settings.localcmd
localcmdbg = settings.localcmdbg

runLocal = False

SRC = os.getcwd()
DEPLOY = join(SRC, "deploy")
DEBUG = join(SRC, "debug")
JARS = join(SRC, "jars")

# Jars
sensejar = join(JARS, "libsense-git.jar")
netwrapperjar = join(JARS, "libnetwrapper-git.jar")
ridgejar = join(JARS, "ridge-git.jar")
cfabcastjar = join(JARS, "CFABCast-assembly-0.1-SNAPSHOT.jar")
libmcadjar = join(JARS, "libmcad-git-allinone.jar")

# número de requisições que o cliente pode fazer por vez antes de receber alguma resposta
numPermits = 10
# tamanho da msg em bytes
messageSize = 200
# duração em segundos
benchDuration = 600
# numero de clientes (BenchClient)
numClients = 2
# numero de grupos de multicast
numGroups = 1
# Uma maneira de aumentar throughput é ter vários grupos de acceptors independentes gerando mensagens, e os learners fazem merge determinístico das streams de mensagens. Isso divide a carga de ordenação entre conjuntos de processos paxos independentes, mas aumenta o processamento dos learners e, potencialmente, aumenta a latência se os conjuntos de paxos não estiverem sincronizados.
numPxPerGroup = 1
# cada learner tem um BenchServer associado, cria-se numLearners learners, e daí eles são divididos entre os numGroups grupos de multicast
numLearners = 2

# quantidade de nós do protocolo:
numService = 3

writeToDisk = False

# MONITORING
javaGathererClass = "ch.usi.dslab.bezerra.sense.DataGatherer"
javaDynamicGathererClass = "ch.usi.dslab.bezerra.mcad.benchmarks.DynamicBenchGatherer"
gathererPort = "60000"

libmcad_logdir = settings.get_logdir("cfabcast", numClients, numPermits, numLearners, numGroups, numPxPerGroup, messageSize, writeToDisk)
print libmcad_logdir
logdir = libmcad_logdir + "/logs"
gcLogFile = logdir + "/gc.log"
# cleanup logs
#clean_log(logdir)

MEM_OPTS="-Xms1538M -Xmx1538M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=" + logdir + " -XX:+UseParallelGC -XX:+UseCompressedOops"
PRINT_GC_OPTS="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -verbose:gc -Xloggc:" + gcLogFile
TUNNING_GC="-XX:+UseG1GC"
#HOST_OPTS="-Dakka.remote.netty.tcp.hostname=$HOST -Dakka.cluster.seed-nodes.1=akka.tcp://CFABCastSystem@$SEED1 -Dakka.cluster.seed-nodes.2=akka.tcp://CFABCastSystem@$SEED2"
#PORT_OPTS="-Dakka.remote.netty.tcp.port=$PORT"
#LOG_OPTS="-DLOG_DIR=" + cfabcast_log
#DEBUG="-Dakka.loglevel=DEBUG -Dakka.log-dead-letters=1000 -Dakka.remote.log-received-messages=on -Dakka.remote.log-sent-messages=on"
#APP_OPTS="-Dakka.cluster.roles.1=cfabcast"
JAVA_OPTS= MEM_OPTS + " " + PRINT_GC_OPTS

javaCommand="java " + JAVA_OPTS

# Global config
configPath = SRC + "/config_parameters.json"

#lastNode = settings.availableNodes[len(settings.availableNodes)-1:]
#remainingNodes = settings.availableNodes[:len(settings.availableNodes)-1]
#serviceNodes = remainingNodes
#serverNodes = remainingNodes
#clientsNodes = remainingNodes
#monitorNode = lastNode[0]

lastNode = settings.availableNodes[len(settings.availableNodes)-1:]
remainingNodes = settings.availableNodes[:len(settings.availableNodes)-1]
serviceNodes = remainingNodes[:numService]
qtdService = len(serviceNodes)
# same nodes
serverNodes = remainingNodes[:numLearners]
clientsNodes = remainingNodes[:numClients]
# distinct nodes
#serverNodes = remainingNodes[qtdService:(qtdService + numLearners)]
#clientsNodes = remainingNodes[qtdService:(qtdService + numClients)]
monitorNode = lastNode[0]

gathererNode = monitorNode

if runLocal:
    serviceNodes = ["node1", "node2", "node3"]
    serverNodes = clientsNodes = ["127.0.0.1"]
    monitorNode = "127.0.0.1"

print "Starting service nodes..."
cfabcast_config = join(DEPLOY, "cfabcast-deploy.conf")
if runLocal:
    cfabcast_config = join(DEBUG, "cfabcast-debug.conf")

for node in serviceNodes:
    node_stdout = logdir + "/cfabcast-" + node + ".out"
    javaservicecmd = "%s -DLOG_DIR=%s -cp %s -Dconfig.file=%s Main %s" % (javaCommand, logdir, cfabcastjar, cfabcast_config, node)
    if runLocal:
        localcmdbg(javaservicecmd, "", node_stdout)
    else:
        sshcmdbg(node, javaservicecmd, "", node_stdout)
sleep(20)

# Server Config
# Run server
##serverCommand = "mvn "+ mvn_options + " exec:java -Dexec.mainClass=%s -Dexec.args=\"%s %s %s %s %s %s\" " % ( benchServerClass, serverId, configPath, gathererNode, gathererPort, logdir, benchDuration)
benchServerClass = "ch.usi.dslab.bezerra.mcad.benchmarks.BenchServer"
server_config = join(DEPLOY, "server-deploy.conf")
if runLocal:
    server_config = join(DEBUG, "server-debug.conf")
    
for node in settings.createIdPerNodeList(serverNodes):
    node_stdout = logdir + "/server-" + str(node["id"]) + ".out"
    env = "export APP_HOST=\"%s\" APP_PORT=%s ;" % (node["host"], 2550)
    serverCommand = "%s -cp %s -Dconfig.file=%s %s %s %s %s %s %s %s" % (javaCommand, libmcadjar, server_config, benchServerClass, node["id"], configPath, gathererNode, gathererPort, libmcad_logdir, benchDuration)
    if runLocal:
        localcmdbg(serverCommand, env, node_stdout)
    else:
        sshcmdbg(node["host"], serverCommand, env, node_stdout)
sleep(10)

# Client config
# Run client
##export MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
##    clientCommand = "mvn "+ mvn_options + " exec:java -Dexec.mainClass=%s -Dexec.args=\"%s %s %s %s %s %s %s\" " % ( benchClientClass, clientId, configPath, messageSize, numPermits, gathererNode, gathererPort, benchDuration)
benchClientClass = "ch.usi.dslab.bezerra.mcad.benchmarks.BenchClient"
client_config = join(DEPLOY, "client-deploy.conf")
if runLocal:
    client_config = join(DEBUG, "client-debug.conf")

for node in settings.createIdPerNodeList(clientsNodes):
    node_stdout = logdir + "/client-" + str(node["id"]) + ".out"
    env = "export APP_HOST=\"%s\" APP_PORT=%s SERVER_HOST=\"%s\" SERVER_PORT=%s ;" % (node["host"], 0, node["host"], 2550)
    clientCommand = "%s -cp %s -Dconfig.file=%s %s %s %s %s %s %s %s %s" % (javaCommand, libmcadjar, client_config, benchClientClass, node["id"], configPath, messageSize, numPermits, gathererNode, gathererPort, benchDuration)
    if runLocal:
        localcmdbg(clientCommand, env, node_stdout)
    else:
        sshcmdbg(node["host"], clientCommand, env, node_stdout)
sleep(5)

    
# DataGatherer:
#             0         1           2          3         4
# <command> <port> <directory> {<resource> <nodetype> <count>}+

## numClients * numPermits as "load"/as "numClients"?
javagatherercmd = "%s -cp %s %s %s %s" % (javaCommand, libmcadjar, javaGathererClass, gathererPort, libmcad_logdir)
javagatherercmd += " throughput conservative " + str(numClients)
javagatherercmd += " throughput optimistic   " + str(numClients)
javagatherercmd += " latency    conservative " + str(numClients)
javagatherercmd += " latency    optimistic   " + str(numClients)
javagatherercmd += " latencydistribution conservative " + str(numClients)
javagatherercmd += " latencydistribution optimistic   " + str(numClients)
javagatherercmd += " mistakes   server       " + str(numLearners)

timetowait = benchDuration + (numClients + numGroups * numPxPerGroup * 2 + numLearners) * 10

exitcode = 0
if runLocal:
    exitcode = localcmd(javagatherercmd, timetowait)
else:
    exitcode = sshcmd(gathererNode, javagatherercmd, timetowait)

if exitcode != 0 :
    localcmd("touch %s/FAILED.txt" % (libmcad_logdir))

sleep(10)

if runLocal:
    localcmd("killall -9 java")
else:
  for node in settings.availableNodes :
    sshcmdbg(node, "killall -9 -u lasaro &> /dev/null")

sys.exit(exitcode)
