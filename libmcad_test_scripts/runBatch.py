#!/usr/bin/python

################################################################################
''' imports '''

from settings import *
import sys
import time
from math import ceil
from os.path import expanduser
################################################################################
''' functions '''
def getLoads(minClients, maxClients, incFactor, incParcel) :
    clients = []
    numClients = minClients
    while numClients <= maxClients :
        clients.append(numClients)
        numClients = int(ceil(numClients * incFactor + incParcel))
    return clients
################################################################################
''' experiment variables
    - (int)  numClients
    - (int)  numLearners
    - (str)  algorithm
    - (int)  msgSize
    - (dic)  configuration
    - (bool) writeToDisk
    
    order given to alg scrip: numClients, numLearners, numGroups, numPxPerGroup, msgSize, writeToDisk
    
'''
##########################################
minClients = 10
maxClients = 70
incFactor = 1
incParcel = 10
#loads = getLoads(minClients, maxClients, incFactor, incParcel)
#loadsGlobal = [1, 5, 10, 20, 25, 30, 35, 40, 50, 80, 120, 200, 300, 400, 500, 1000, 5000, 10000]#500, 1000, 2000, 5000, 7500, 10000, 15000, 20000, 30000, 50000, 75000, 100000]
#loadsGlobal = [300, 400, 500]
#loadsGlobal = [1,5,6,7,8,9,10,11,12,13,14,15,20,25,30,35,36,37,38,39,40,41,42,43,44,45, 50, 80, 120, 200, 300, 400, 500]
#loadsGlobal = [1000,2500,5000,10000,15000,20000,50000]
#loadsGlobal = [6250]
#loadsGlobal = [36,37,38,39,40,41,42,43,44]
#loadsGlobal = [20]
loadsGlobal = range(1,11)
#numPermits = 1
##########################################
#numsLearners = [1, 2, 4, 8, 16, 32]
#numsLearners = [1, 2, 4, 8, 32]
numsLearners = [3]
##########################################
#algorithms = ["mrp", "ridge", "lpnorand"]
#algorithms = ["ridge"]
#algorithms = ["mrp"]
#algorithms = ["libpaxos"]
#algorithms = ["lpnorand"]
#algorithms = ["ridge", "mrp"]
algorithms = ["cfabcast"]
##########################################
#messageSizes = [200, 8192, 65536]
#messageSizes = [65536]
#messageSizes = [8192]
messageSizes = [200]
##########################################
groups = 0
pxpergroup = 1
groupConfigs = [{groups : 1, pxpergroup : 1},
                #{groups : 1, pxpergroup : 2},
                #{groups : 1, pxpergroup : 4},
                #{groups : 1, pxpergroup : 8},
                #{groups : 2, pxpergroup : 1},
                #{groups : 4, pxpergroup : 1},
                #{groups : 8, pxpergroup : 1},
               ]
##########################################
#diskConfigs = [False, True]
diskConfigs = [False]
################################################################################
''' clean up environment before running batch
'''
#localcmd(cleaner)
#time.sleep(5)
#localcmd(systemParamSetter)
#localcmd(clockSynchronizer)
################################################################################

loadsDic = {}
# for lpnorand
# (nl, ng, size)
#loadsDic[( 1,1,200  )] = [9,11,49,51]
#loadsDic[( 1,1,8192 )] = [2,4]
#loadsDic[( 1,1,65536)] = [2,4]
#loadsDic[( 2,1,200  )] = [9,11,49,51]
#loadsDic[( 2,1,8192 )] = [2,4,6]
#loadsDic[( 2,1,65536)] = [2,4]
#loadsDic[( 4,1,200  )] = [9,11,49,51]
#loadsDic[( 4,1,8192 )] = [2,4]
#loadsDic[( 4,1,65536)] = [2,4]
#loadsDic[( 8,1,200  )] = [9,11,49,50,51,90]
#loadsDic[( 8,1,8192 )] = [2,4]
#loadsDic[( 8,1,65536)] = [1,2,3,4]
#loadsDic[(16,1,200  )] = [9,11,49,51]
#loadsDic[(16,1,8192 )] = [2,4]
#loadsDic[(16,1,65536)] = [1,2,3,4]
#loadsDic[(32,1,200  )] = [9,11,49,51]
#loadsDic[(32,1,8192 )] = [2,4]
#loadsDic[(32,1,65536)] = [2,4]

# for mrp
# (nl, ng, size)
#loadsDic[( 1,1,200  )] = [39,41]
#loadsDic[( 1,1,8192 )] = [11,13]
#loadsDic[( 1,1,65536)] = []
#loadsDic[( 2,1,200  )] = [15,17]
#loadsDic[( 2,1,8192 )] = [13,15]
#loadsDic[( 2,1,65536)] = [8]
#loadsDic[( 4,1,200  )] = [11]
#loadsDic[( 4,1,8192 )] = []
#loadsDic[( 4,1,65536)] = [11]
#loadsDic[( 8,1,200  )] = [11]
#loadsDic[( 8,1,8192 )] = [8]
#loadsDic[( 8,1,65536)] = [13,15]
#loadsDic[(16,1,200  )] = [32,34,36,38,50]
#loadsDic[(16,1,8192 )] = [13,15]
#loadsDic[(16,1,65536)] = [19,21]
#loadsDic[(32,1,200  )] = [32,34,36,38,75,80,85]
#loadsDic[(32,1,8192 )] = [13,15]
#loadsDic[(32,1,65536)] = [29,31]
#loadsDic[(8 ,2,8192 )] = [7,8,9,11,12,13,75,100,125]
#loadsDic[(16,4,65536)] = [7,8,9,11,12,13,17,18,19,21,22,23]
#loadsDic[(32,8,200  )] = [7,8,9,11,12,13,250,375]
#loadsDic[(32,8,65536)] = [7,8,9,11,12,13,250,375]

# for ridge
# (nl, ng, size)
#loadsDic[( 1,1,200  )] = [25,29,31,35]
#loadsDic[( 1,1,8192 )] = [11,13]
#loadsDic[( 1,1,65536)] = [8]
#loadsDic[( 2,1,200  )] = [19,21]
#loadsDic[( 2,1,8192 )] = [14,16]
#loadsDic[( 2,1,65536)] = [8]
#loadsDic[( 4,1,200  )] = [9,11]
#loadsDic[( 4,1,8192 )] = [9,11]
#loadsDic[( 4,1,65536)] = [14,16]
#loadsDic[( 8,1,200  )] = [9,11]
#loadsDic[( 8,1,8192 )] = []
#loadsDic[( 8,1,65536)] = []
#loadsDic[(16,1,200  )] = [9,11]
#loadsDic[(16,1,8192 )] = [8]
#loadsDic[(16,1,65536)] = [19,21]
#loadsDic[(32,1,200  )] = [4,6]
#loadsDic[(32,1,8192 )] = [6,8]
#loadsDic[(32,1,65536)] = [31,32,33,35,37,41,43]



# *** loadsDic[( 2,2,200  )] = [150,175]
#loadsDic[( 2,2,8192 )] = [12,15,17]
# *** loadsDic[( 2,2,65536)] = [6]
# *** loadsDic[( 4,2,200  )] = [175,200]
#loadsDic[( 4,2,8192 )] = [7,9]
#loadsDic[( 4,2,65536)] = [7,9,100,125,150]
#loadsDic[( 8,2,200  )] = [100,125,150]
# *** loadsDic[( 8,2,8192 )] = [4,6]
#loadsDic[( 8,2,65536)] = [7,15,100,125,150]
#loadsDic[(16,2,200  )] = [100,125,150]
#loadsDic[(16,2,8192 )] = [7,9,12,14,16]
#loadsDic[(16,2,65536)] = [7,9,12,100,125,150]
# *** loadsDic[(32,2,200  )] = [250,300]
#loadsDic[(32,2,8192 )] = [6,7,8,9,22,24]
#loadsDic[(32,2,65536)] = [100,120,140,160,180]


# lipaxou
# (l,g,s)
#for l in [1,2,4,8] :
#    loadsDic[(l,1,65536)] = [6,7,8,9]

# lpnorand
#for l in [1,2,4,8,16,32] :
#    loadsDic[(l,1,200)] = [10,20,30,40,50,90]

skips = 0
if len(sys.argv) > 1 :
    skips = iarg(1)

for writeToDisk in diskConfigs :
    for messageSize in messageSizes :
        for algorithm in algorithms :
            for groupConfig in groupConfigs :
#################################
                #numsLearners = [groupConfig[groups] * 4]
#################################
                for numLearners in numsLearners :
                    lgs = (numLearners,groupConfig[groups],messageSize)
                    if loadsDic.has_key(lgs) : loads = loadsDic[lgs]
                    else                     : loads = loadsGlobal
                    for load in loads :
                        if groupConfig[groups] > numLearners :
                            print "Not running with less learners than groups"
                        elif skips > 0 :
                            print("SKIPPING run %s with %s clients, with %s multicast groups (%s Paxos groups each), message size %s bytes, useDisk is %s" % \
                                  (algorithm, load, groupConfig[groups], groupConfig[pxpergroup], messageSize, writeToDisk))
                            skips -= 1
                        else:
                            tries = 3
                            exitcode = -1
                            while tries > 0 and exitcode != 0 :
                                tries -= 1
                                print("Running %s with load %s, %s learners, with %s multicast groups (%s Paxos groups each), message size %s bytes, useDisk is %s" % \
                                     (algorithm, load, numLearners, groupConfig[groups], groupConfig[pxpergroup], messageSize, writeToDisk))
                                exitcode = localcmd(onceRunner[algorithm] + " %s %s %s %s %s %s" % (load, numLearners, groupConfig[groups], groupConfig[pxpergroup], messageSize, writeToDisk))
                                if exitcode != 0 :
                                    print("Failed last experiment try")
