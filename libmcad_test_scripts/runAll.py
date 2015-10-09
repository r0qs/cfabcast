#!/usr/bin/python
# -*- coding: utf-8 -*-

import settings
import sys, os
from os.path import join, expanduser
from time import sleep

runLocal = True

javaCommand = "java -XX:+UseG1GC -Xms3g -Xmx3g"

SRC = currentDir = os.getcwd()
# Directories
#SRC = join(expanduser('~'), "test")
#if runLocal:
#    SRC = join(expanduser('~'), "src/mestrado/scala/test")

DEPLOY = join(SRC, "deploy")
DEBUG = join(SRC, "debug")
JARS = join(SRC, "jars")

# Jars
sensejar = join(JARS, "libsense-git.jar")
netwrapperjar = join(JARS, "libnetwrapper-git.jar")
ridgejar = join(JARS, "ridge-git.jar")
cfabcastjar = join(JARS, "CFABCast-assembly-0.1-SNAPSHOT.jar")
libmcadjar = join(JARS, "libmcad-git-allinone.jar")

# cleanup logs
#clean_log(logdir)

lastNode = settings.availableNodes[len(settings.availableNodes)-1:]
remainingNodes = settings.availableNodes[:len(settings.availableNodes)-1]
serviceNodes = remainingNodes
serverNodes = remainingNodes
clientsNodes = remainingNodes
monitorNode = lastNode[0]

if runLocal:
    serviceNodes = ["node1", "node2", "node3"]
    serverNodes = clientsNodes = ["127.0.0.1"]
    monitorNode = "127.0.0.1"

# MONITORING
gathererNode = monitorNode
javaGathererClass = "ch.usi.dslab.bezerra.sense.DataGatherer"
javaDynamicGathererClass = "ch.usi.dslab.bezerra.mcad.benchmarks.DynamicBenchGatherer"
gathererPort = "60000"

# Global config
configPath = SRC + "/config_parameters.json"
# número de requisições que o cliente pode fazer por vez antes de receber alguma resposta
numPermits = 10
# tamanho da msg em bytes
messageSize = 200
# duração em segundos
benchDuration = 60
# numero de clientes (BenchClient)
numClients = 1
# numero de grupos de multicast
numGroups = 1
# Uma maneira de aumentar throughput é ter vários grupos de acceptors independentes gerando mensagens, e os learners fazem merge determinístico das streams de mensagens. Isso divide a carga de ordenação entre conjuntos de processos paxos independentes, mas aumenta o processamento dos learners e, potencialmente, aumenta a latência se os conjuntos de paxos não estiverem sincronizados.
numPxPerGroup = 1
# cada learner tem um BenchServer associado, cria-se numLearners learners, e daí eles são divididos entre os numGroups grupos de multicast
numLearners = 1

writeToDisk = False

logdir = settings.get_logdir("cfabcast", numClients, numPermits, numLearners, numGroups, numPxPerGroup, messageSize, writeToDisk)
print logdir

sshcmd = settings.sshcmd
sshcmdbg = settings.sshcmdbg
localcmd = settings.localcmd
localcmdbg = settings.localcmdbg

print "Starting service nodes..."
cfabcast_config = join(DEPLOY, "cfabcast-deploy.conf")
if runLocal:
    cfabcast_config = join(DEBUG, "cfabcast-debug.conf")

for node in serviceNodes:
    javaservicecmd = "%s -cp %s -Dconfig.file=%s Main %s" % (javaCommand, cfabcastjar, cfabcast_config, node)
    if runLocal:
        localcmdbg(javaservicecmd)
    else:
        sshcmdbg(node, javaservicecmd)
sleep(5)

# Server Config
# Run server
##serverCommand = "mvn "+ mvn_options + " exec:java -Dexec.mainClass=%s -Dexec.args=\"%s %s %s %s %s %s\" " % ( benchServerClass, serverId, configPath, gathererNode, gathererPort, logdir, benchDuration)
benchServerClass = "ch.usi.dslab.bezerra.mcad.benchmarks.BenchServer"
server_config = join(DEPLOY, "server-deploy.conf")
if runLocal:
    server_config = join(DEBUG, "server-debug.conf")
    
for node in settings.createIdPerNodeList(serverNodes):
    env = "export APP_HOST=\"%s\" APP_PORT=%s ;" % (node["host"], 2550)
    serverCommand = "%s -cp %s -Dconfig.file=%s %s %s %s %s %s %s %s" % (javaCommand, libmcadjar, server_config, benchServerClass, node["id"], configPath, gathererNode, gathererPort, logdir, benchDuration)
    if runLocal:
        localcmdbg(serverCommand, env)
    else:
        sshcmdbg(node["host"], serverCommand, env)
sleep(5)

# Client config
# Run client
##export MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n"
##    clientCommand = "mvn "+ mvn_options + " exec:java -Dexec.mainClass=%s -Dexec.args=\"%s %s %s %s %s %s %s\" " % ( benchClientClass, clientId, configPath, messageSize, numPermits, gathererNode, gathererPort, benchDuration)
benchClientClass = "ch.usi.dslab.bezerra.mcad.benchmarks.BenchClient"
client_config = join(DEPLOY, "client-debug.conf")
if runLocal:
    client_config = join(DEBUG, "client-debug.conf")

for node in settings.createIdPerNodeList(clientsNodes):
    env = "export APP_HOST=\"%s\" APP_PORT=%s SERVER_HOST=\"%s\" SERVER_PORT=%s ;" % (node["host"], 0, node["host"], 2550)
    clientCommand = "%s -cp %s -Dconfig.file=%s %s %s %s %s %s %s %s %s" % (javaCommand, libmcadjar, client_config, benchClientClass, node["id"], configPath, messageSize, numPermits, gathererNode, gathererPort, benchDuration)
    if runLocal:
        localcmdbg(clientCommand, env)
    else:
        sshcmdbg(node["host"], clientCommand, env)
sleep(5)

    
# DataGatherer:
#             0         1           2          3         4
# <command> <port> <directory> {<resource> <nodetype> <count>}+

## numClients * numPermits as "load"/as "numClients"?
javagatherercmd = "%s -cp %s %s %s %s" % (javaCommand, libmcadjar, javaGathererClass, gathererPort, logdir)
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
    localcmd("touch %s/FAILED.txt" % (logdir))

sleep(10)

if runLocal:
    localcmd("killall -9 java")

sys.exit(exitcode)
