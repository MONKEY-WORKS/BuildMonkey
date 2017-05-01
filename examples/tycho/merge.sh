#!/bin/bash

java -jar $ECLIPSE_HOME/Contents/Eclipse/plugins/org.eclipse.equinox.launcher_*.jar \
    -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication \
    -source /Users/jake/code/hackathon/BuildMonkey/examples/tycho/mars/build/p2-repository \
    -destination /Users/jake/code/hackathon/BuildMonkey/examples/tycho/p2-repo \
    -consoleLog