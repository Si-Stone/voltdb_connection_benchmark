#!/usr/bin/env bash

set -e

echo started
date

# run all benchmarks and output to text files

# first argument to run.sh is the type
# second argument to run.sh is number of connections (made in serial)
# third argument to run.sh is number of proc calls made (per connection)
# 

# first try to get average connection/disconnection overhead as a percentage for each type
# i.e. just connect, call proc once and disconnect
number_of_connections=1000
number_of_proc_calls=1

echo
echo many connections in serial starts  at `date`
echo
echo number of connections is:  ${number_of_connections}
echo number of proc calls is:   ${number_of_connections}
echo

file=rpt_many_connections_1_proc_call_native_synch_client.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh native_synch_client ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

file=rpt_many_connections_1_proc_call_native_asynch_client.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh native_asynch_client ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

file=rpt_many_connections_1_proc_call_jdbc_client.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh jdbc_client ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

echo
echo many connections in serial ends at `date`
echo

# next try to see how things change when connection is held open for many proc calls
# i.e. connect, call procs many times and disconnect
number_of_connections=1
number_of_proc_calls=1000

echo
echo single connection many proc calls starts at `date`
echo
echo number of connections is:  ${number_of_connections}
echo number of proc calls is:   ${number_of_connections}
echo

file=rpt_1_connection_many_proc_calls_native_synch_client.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh native_synch_client ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

file=rpt_1_connection_many_proc_calls_native_asynch_client.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh native_asynch_client ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

file=rpt_1_connection_many_proc_call_jdbc_client.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh jdbc_client ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

echo
echo single connection many proc calls ends at `date`
echo


# now see how many we can run in parallel
# i.e. parallel client connections as separate threads, each one of which calls proc once and disconnects

number_of_connections=1000
number_of_proc_calls=1

echo
echo many connections in parallel starts at `date`
echo
echo number of connections is:  ${number_of_connections}
echo number of proc calls is:   ${number_of_connections}
echo


file=rpt_many_connections_1_proc_call_native_synch_client_parallel.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh native_synch_client_parallel ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

file=rpt_many_connections_1_proc_call_native_asynch_client_parallel.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh native_asynch_client_parallel ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

file=rpt_many_connections_1_proc_call_jdbc_client_parallel.txt
sqlcmd < truncate.sql > ${file} 2>&1
./run.sh jdbc_client_parallel ${number_of_connections} ${number_of_proc_calls} >> ${file} 2>&1
sqlcmd < sanity_check_rowcount.sql >> ${file} 2>&1

echo
echo many connections in parallel ends at `date`
echo

echo ended
date
