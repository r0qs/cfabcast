#!/usr/bin/python

import settings
import time

# cleaning remote nodes
for node in settings.availableNodes :
    print "Cleaning worker " + node + "..."
    settings.sshcmdbg(node, "killall -9 -u lasaro &> /dev/null")
#    settings.localcmdbg("ssh lasaro@node249 ssh " + node + " sudo killall -9 -u lasaro &> /dev/null")

time.sleep(5)
