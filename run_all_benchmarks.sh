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
number_of_connections=100000
number_of_proc_calls=1

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


# next try to see how things change when connection is held open for many proc calls
# i.e. connect, call procs many times and disconnect
number_of_connections=1
number_of_proc_calls=100000

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

echo ended
date
