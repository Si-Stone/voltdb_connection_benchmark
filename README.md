# voltdb_connection_benchmark

Benchmarking tool with the aim of getting some timings to show connection/disconnection overhead
for various drivers and configurations.

---------------------------------------------------------------------------------
tl;dr version:
==============

start one node server with the deployment and catalog file provided
run: run_all_benchmarks.sh
see the rpt*.txt files output for time comparisons

---------------------------------------------------------------------------------

The longer version:
===================

Can configure for any combination of:
1..many connections (in serial) and 1..many procedure calls (inserts) for each connection

Note: see the run.sh client function where the all configuration options can be seen and set set

run.sh actions
-----------
- *run.sh* : start the server
- *run.sh server* : start the server
- *run.sh jars* : compile all Java clients and stored procedures into two Java jar files
- *run.sh client* : start the client, more than 1 client is permitted
- *run.sh clean* : remove compilation and runtime artifacts

after each run you will need to truncate the inserted records from the database table:
	truncate table client_location;
alternatively:
	sqlcmd < truncate.sql
	
after each run you may wish to check the number of rows inserted into database table:
	select count(*) from client_location;
	
to run all benchmarks:
	run_all_benchmarks.sh
(this will do the truncation in between runs)
