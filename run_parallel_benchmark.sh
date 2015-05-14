#!/usr/bin/env bash

set -e

echo started
date

# run all benchmarks and output to text files

# first argument to run.sh is the type
# second argument to run.sh is number of connections (made in serial)
# third argument to run.sh is number of proc calls made (per connection)
# 

# see how many we can run in parallel
# i.e. parallel connections, each one of which calls proc once and disconnects
number_of_connections=5000
number_of_proc_calls=1

file=rpt_many_connections_1_proc_call_native_asynch_client_parallel.txt
sqlcmd < truncate.sql > $file 2>&1
#./run.sh native_asynch_client_parallel $number_of_connections $number_of_proc_calls >> $file 2>&1
./run.sh jdbc_client_parallel $number_of_connections $number_of_proc_calls >> $file 2>&1
#./run.sh native_synch_client_parallel $number_of_connections $number_of_proc_calls >> $file 2>&1
sqlcmd < sanity_check_rowcount.sql >> $file 2>&1

echo ended
date
