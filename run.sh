#!/usr/bin/env bash

#set -o nounset #exit if an unset variable is used
set -o errexit #exit on any single command fail

# find voltdb binaries in either installation or distribution directory.
if [ -n "$(which voltdb 2> /dev/null)" ]; then
    VOLTDB_BIN=$(dirname "$(which voltdb)")
else
    VOLTDB_BIN="$(dirname $(dirname $(pwd)))/bin"
    echo "The VoltDB scripts are not in your PATH."
    echo "For ease of use, add the VoltDB bin directory: "
    echo
    echo $VOLTDB_BIN
    echo
    echo "to your PATH."
    echo
fi
# move voltdb commands into path for this script
PATH=$VOLTDB_BIN:$PATH

# installation layout has all libraries in $VOLTDB_ROOT/lib/voltdb
if [ -d "$VOLTDB_BIN/../lib/voltdb" ]; then
    VOLTDB_BASE=$(dirname "$VOLTDB_BIN")
    VOLTDB_LIB="$VOLTDB_BASE/lib/voltdb"
    VOLTDB_VOLTDB="$VOLTDB_LIB"
# distribution layout has libraries in separate lib and voltdb directories
else
    VOLTDB_BASE=$(dirname "$VOLTDB_BIN")
    VOLTDB_LIB="$VOLTDB_BASE/lib"
    VOLTDB_VOLTDB="$VOLTDB_BASE/voltdb"
fi

APPCLASSPATH=$CLASSPATH:$({ \
    \ls -1 "$VOLTDB_VOLTDB"/voltdb-*.jar; \
    \ls -1 "$VOLTDB_LIB"/*.jar; \
    \ls -1 "$VOLTDB_LIB"/extension/*.jar; \
} 2> /dev/null | paste -sd ':' - )
CLIENTCLASSPATH=connection_benchmark-client.jar:$CLASSPATH:$({ \
    \ls -1 "$VOLTDB_VOLTDB"/voltdbclient-*.jar; \
    \ls -1 "$VOLTDB_LIB"/commons-cli-1.2.jar; \
} 2> /dev/null | paste -sd ':' - )
LOG4J="$VOLTDB_VOLTDB/log4j.xml"
LICENSE="$VOLTDB_VOLTDB/license.xml"
HOST="localhost"

# remove binaries, logs, runtime artifacts, etc... but keep the jars
function clean() {
    rm -rf voltdbroot log \
        client/connection_benchmark/*.class
}

# remove everything from "clean" as well as the jarfiles
function cleanall() {
    clean
    rm -rf connection_benchmark-client.jar
}

# compile the source code for procedures and the client into jarfiles
function jars() {
    # compile java source
    javac -target 1.8 -source 1.8 -classpath $CLIENTCLASSPATH \
        client/connection_benchmark/*.java
    jar cf connection_benchmark-client.jar -C client connection_benchmark
    # remove compiled .class files
    rm -rf \
        client/connection_benchmark/*.class
}

# compile the procedure and client jarfiles if they don't exist
function jars-ifneeded() {
    if [ ! -e connection_benchmark-client.jar ]; then
        jars;
    fi
}

# run the voltdb server locally
function server() {
	echo
    echo "Starting the VoltDB server."
	echo
    voltdb create -d deployment.xml catalog.jar
}

# Use this target for argument help
function client-help() {
    jars-ifneeded
    java -classpath $CLIENTCLASSPATH connection_benchmark.ConnectionBenchmark --help
}

function help() {
    echo "Usage: ./run.sh {clean|jars|server|jdbc_client [numberOfConnections numberOfProcCalls] |...}"
}

# run the benchmark
function jdbc_client() {

    jars-ifneeded

    java -classpath $CLIENTCLASSPATH -Dlog4j.configuration=file://$LOG4J \
        connection_benchmark.ConnectionBenchmark \
        --clientType=jdbc_client \
        --numberOfConnections=$1 \
        --numberOfProcCallsPerConnection=$2
}

# run the benchmark
function native_synch_client() {

    jars-ifneeded

    java -classpath $CLIENTCLASSPATH -Dlog4j.configuration=file://$LOG4J \
        connection_benchmark.ConnectionBenchmark \
        --clientType=native_synch_client \
        --numberOfConnections=$1 \
        --numberOfProcCallsPerConnection=$2
}

# run the benchmark
function native_asynch_client() {

    jars-ifneeded

    java -classpath $CLIENTCLASSPATH -Dlog4j.configuration=file://$LOG4J \
        connection_benchmark.ConnectionBenchmark \
        --clientType=native_asynch_client \
        --numberOfConnections=$1 \
        --numberOfProcCallsPerConnection=$2
}

# Run the target passed as the first arg on the command line
# If no first arg, run server
if [ $1 = "jdbc_client" ]; then $1 $2 $3; exit; fi
if [ $1 = "native_synch_client" ]; then $1 $2 $3; exit; fi
if [ $1 = "native_asynch_client" ]; then $1 $2 $3; exit; fi
if [ $# -gt 1 ]; then help; exit 1; fi
if [ $# = 1 ]; then $1; else server; fi
