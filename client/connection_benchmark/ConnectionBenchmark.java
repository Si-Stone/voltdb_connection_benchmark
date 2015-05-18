package connection_benchmark;

import java.util.Arrays;
import java.util.IllegalFormatCodePointException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.voltdb.CLIConfig;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ClientStatusListenerExt;
import org.voltdb.client.ProcedureCallback;

import java.sql.*;
import java.io.*;

class ConnectionBenchmark {

    // handy, rather than typing this out several times
    private static final String HORIZONTAL_RULE =
            "----------" + "----------" + "----------" + "----------" +
            "----------" + "----------" + "----------" + "----------" + "\n";

    // validated command line configuration
    private final BenchmarkConfig config;

    private long totalTimeMilli = 0;
    private long connectTimeMilli = 0;
    private long procTimeMilli = 0;
    private long disconnectTimeMilli = 0;

    private long connectionStartTime;
    private long connectionEndTime;

    private long procCallsStartTime;
    private long procCallsEndTime;

    private long disconnectionStartTime;
    private long disconnectionEndTime;

    // overall success/failure counts
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);

    /**
     * Uses included {@link CLIConfig} class to
     * declaratively state command line options with defaults
     * and validation.
     */
    static class BenchmarkConfig extends CLIConfig {

        @Option(desc = "server to connect to.")
        String server = "localhost:21212";
/*
        @Option(desc = "CURRENTLY UNUSED: Maximum TPS rate for benchmark.")
        int ratelimit = Integer.MAX_VALUE;
*/
        @Option(desc = "User name for connection.")
        String user = "";

        @Option(desc = "Password for connection.")
        String password = "";

        @Option(desc = "Number of connections to make (in serial) for the benchmark.")
        int numberOfConnections = 1;

        @Option(desc = "Number of procedure calls (inserts) to make per connection for the benchmark.")
        int numberOfProcCallsPerConnection = 1;

        @Option(desc = "Client type - ONE OF JDBC_CLIENT, JDBC_CLIENT_PARALLEL, NATIVE_SYNCH_CLIENT" +
                ", NATIVE_SYNCH_CLIENT_PARALLEL, NATIVE_ASYNCH_CLIENT OR  NATIVE_ASYNCH_CLIENT_PARALLEL (see run.sh)")
        String clientType = ""; // Note: CLIConfig does not cater for enums, so use string
    }

    /**
     * Constructor.
     * Configures VoltDB client.
     *
     * @param config Parsed & validated CLI options.
     */
    private ConnectionBenchmark(BenchmarkConfig config) {
        this.config = config;

        //System.out.printf("clientType is: %s\n", config.clientType);

        // valid clientType?
/* todo: remove?
        if (! config.clientType.equals("jdbc_client") &&
            ! config.clientType.equals("jdbc_client_parallel") &&
            ! config.clientType.equals("native_synch_client") &&
            ! config.clientType.equals("native_synch_client_parallel") &&
            ! config.clientType.equals("native_asynch_client") &&
            ! config.clientType.equals("native_asynch_client_parallel")) {
                throw new IllegalArgumentException("Uncatered for clientType");
        }
*/
        
        System.out.print(HORIZONTAL_RULE);
        System.out.println(" Command Line Configuration");
        System.out.println(HORIZONTAL_RULE);
        System.out.println(config.getConfigDumpString());
    }

    /**
     * Provides a callback to be notified on node failure.
     * This example only logs the event.
     * This is only applicable for the native client asynchronous case.
     */

    private class MyStatusListener extends ClientStatusListenerExt {

        @Override
        public void connectionLost(String hostname, int port, int connectionsLeft, DisconnectCause cause) {
            System.err.printf("MyStatusListener: " +
                    "A connection to the database has been lost. " +
                    "There are %d connections remaining.\n", connectionsLeft);
            System.err.flush();
        }
        
        @Override
        public void backpressure(boolean status) {
            System.err.println("MyStatusListener: " +
                    "Backpressure from the database is causing a delay in processing requests.");
            System.err.flush();
        }
        
        @Override
        public void uncaughtException(ProcedureCallback callback, ClientResponse r, Throwable e) {
            System.err.println("MyStatusListener: " +
                    "An error has occurred in a callback procedure. " +
                    "Check the following stack trace for details.");
            e.printStackTrace();
            System.err.flush();
        }
        
        @Override
        public void lateProcedureResponse(ClientResponse response, String hostname, int port) {
            System.err.printf("MyStatusListener: " +
                    "A procedure that timed out on host %s:%d has now responded.\n", hostname, port);
            System.err.flush();
        }
    }

    /**
     * Connect to a single server with retry. Limited exponential backoff.
     * No timeout. This will run until the process is killed if it's not
     * able to connect.
     *
     * @param server hostname:port or just hostname (hostname can be ip).
     */
/*
     void connectToOneServerWithRetry(String server) {
        int sleep = 1000;
        while (true) {
            try {
                client.createConnection(server);    
                break;
            }
            catch (Exception e) {
                System.err.printf("runNativeAsynchIteration(): " +
                        "Connection failed: %s - retrying in %d second(s).\n", e.getMessage(), sleep / 1000);
                System.err.flush();
                //try { Thread.sleep(sleep); } catch (Exception interrupted) {}
                if (sleep < 8000) sleep += sleep;
            }
        }
        //System.out.printf("Connected to VoltDB node at: %s.\n", server);
    }
*/
    /**
     * Connect to a set of servers in parallel. Each will retry until
     * connection. This call will block until all have connected.
     *
     * @param servers A comma separated list of servers using the hostname:port
     * syntax (where :port is optional).
     * @throws InterruptedException if anything bad happens with the threads.
     */
/*   Commented out as only catering for 1 server
     void connect(String servers) throws InterruptedException {
        System.out.println("Connecting to VoltDB...");

        String[] serverArray = servers.split(",");
        final CountDownLatch connections = new CountDownLatch(serverArray.length);

        // use a new thread to connect to each server
        for (final String server : serverArray) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToOneServerWithRetry(server);
                    connections.countDown();
                }
            }).start();
        }
        // block until all have connected
        connections.await();
    }
*/
    /**
     * Callback to handle the response to a stored procedure call.
     * Tracks response types.
     *
     */
    private class MyProcedureCallback implements ProcedureCallback {
        @Override
        public void clientCallback(ClientResponse response) throws Exception {
            totalCalls.incrementAndGet();
            if (response.getStatus() == ClientResponse.SUCCESS) {
                successCalls.incrementAndGet();
            }
            else {
                failedCalls.incrementAndGet();
                System.err.println("MyProcedureCallback: Procedure returned with error: " + response.getStatusString());
                System.err.flush();
            }
        }
    }

    /**
     * Main routine creates a benchmark instance and kicks off the run method.
     *
     * @param args Command line arguments.
     * @throws Exception if anything goes wrong.
     * @see {@link BenchmarkConfig}
     */
    public static void main(String[] args) throws Exception {
        // create a configuration from the arguments
        BenchmarkConfig config = new BenchmarkConfig();
        config.parse(ConnectionBenchmark.class.getName(), args);

        ConnectionBenchmark c = new ConnectionBenchmark(config);
        c.runBenchmark();
    }

    /**
     * Control benchmark code.
     * Setup the benchmark counters, control the looping and whether serial or parallel output the results.
     *
     * @throws Exception if anything unexpected happens.
     */
    private void runBenchmark() throws Exception {
        
        final CountDownLatch connections = new CountDownLatch(config.numberOfConnections);

        System.out.println("\n" + HORIZONTAL_RULE + " Benchmark Starts\n" + HORIZONTAL_RULE);

        long benchmarkStartTime = System.currentTimeMillis();
        
        for (int i=0; i<config.numberOfConnections; i++) {

            switch (config.clientType) {
                case "JDBC_CLIENT":
                    runJdbcIteration(i);
                    break;
                case "JDBC_CLIENT_PARALLEL":

                    int client_id_hack = i;

                    // run a new thread for each connection so that they happen in parallel
                    new Thread(new Runnable() {
                        final int p = client_id_hack;

                        @Override
                        public void run() {
                            try {
                                // the following is to try to spin up as many threads as possible concurrently
                                // (i.e. so that the ones that start first are still in existence)
                                Thread.sleep(10000);
                                System.out.printf("Successfully created jdbc client for client_id %d\n", p);
                                runJdbcIteration(p);
                            } catch (Exception e) {
                                System.err.printf("runBenchmark(): " +
                                        "Something uncatered for  happened in a thread!!!... %s \n", e.getMessage());
                                e.printStackTrace();
                                System.err.flush();
                            } finally {
                                connections.countDown();
                            }
                        }
                    }).start();
                    break;
                case "NATIVE_SYNCH_CLIENT":
                    runNativeSynchIteration(i);
                    break;
                case "NATIVE_SYNCH_CLIENT_PARALLEL":

                    client_id_hack = i;

                    // run a new thread for each connection so that they happen in parallel
                    new Thread(new Runnable() {
                        final int p = client_id_hack;

                        @Override
                        public void run() {
                            try {
                                // the following is to try to spin up as many threads as possible concurrently
                                // (i.e. so that the ones that start first are still in existence)
                                Thread.sleep(10000);
                                System.out.printf("Successfully created native synch client for client_id %d\n", p);
                                runNativeSynchIteration(p);
                            } catch (Exception e) {
                                System.err.printf("runBenchmark(): " +
                                        "Something uncatered for  happened in a thread!!!... %s \n", e.getMessage());
                                System.err.flush();
                            } finally {
                                connections.countDown();
                            }
                        }
                    }).start();
                    break;
                case "NATIVE_ASYNCH_CLIENT":
                    runNativeAsynchIteration(i);
                    break;
                case "NATIVE_ASYNCH_CLIENT_PARALLEL":

                    client_id_hack = i;

                    // run a new thread for each connection so that they happen in parallel
                    new Thread(new Runnable() {
                        final int p = client_id_hack;

                        @Override
                        public void run() {
                            try {
                                // the following is to try to spin up as many threads as possible concurrently
                                // (i.e. so that the ones that start first are still in existence)
                                Thread.sleep(10000);
                                System.out.printf("Successfully created native asynch client for client_id %d\n", p);
                                runNativeAsynchIteration(p);
                            } catch (Exception e) {
                                System.err.printf("runBenchmark(): " +
                                        "Something uncatered for  happened in a thread!!!... %s \n", e.getMessage());
                                e.printStackTrace();
                                System.err.flush();
                            } finally {
                                connections.countDown();
                            }
                        }
                    }).start();
                    break;
                default:
                    throw new IllegalArgumentException("Client Type is not catered for");
            }
        }

        if  (config.clientType.equals("NATIVE_ASYNCH_CLIENT_PARALLEL") ||
                config.clientType.equals("JDBC_CLIENT_PARALLEL") ||
                config.clientType.equals("NATIVE_SYNCH_CLIENT_PARALLEL")) {
            connections.await();
        }

        long benchmarkEndTime = System.currentTimeMillis();
        long time = Math.round((benchmarkEndTime - benchmarkStartTime) / 1000.0);

        System.out.println("\n" + HORIZONTAL_RULE + " Benchmark Ends\n" + HORIZONTAL_RULE);
        System.out.printf("Total run time (HH:MI:SS) was: %02d:%02d:%02d\n", time / 3600, (time / 60) % 60, time % 60);
        System.out.println("\n" + HORIZONTAL_RULE + "\n");
        System.out.printf("Total number of procedure calls was: %d\n", totalCalls.get());
        System.out.printf("Total number of successes was      : %d\n", successCalls.get());
        System.out.printf("Total number of failures was       : %d\n", failedCalls.get());
        System.out.println("\n" + HORIZONTAL_RULE + "\n");
        System.out.printf("Total comparison time in milliseconds was          : %d\n", totalTimeMilli);
        System.out.printf("Connect comparison time in milliseconds was        : %d\n", connectTimeMilli);
        System.out.printf("Procedure call comparison time in milliseconds was : %d\n", procTimeMilli);
        System.out.printf("Disconnect comparison time in milliseconds was     : %d\n", disconnectTimeMilli);
        System.out.println("\n" + HORIZONTAL_RULE + "\n");
        System.out.printf("Total comparison time per connection in milliseconds was         : %d\n",
                Math.round((float) totalTimeMilli / totalCalls.get()));
        System.out.printf("Connect comparison time per connection in milliseconds was       : %d\n",
                Math.round((float) connectTimeMilli / totalCalls.get()));
        System.out.printf("Procedure call comparison time per connection in milliseconds was: %d\n",
                Math.round((float) procTimeMilli / totalCalls.get()));
        System.out.printf("Disconnect comparison time per connection in milliseconds was    : %d\n",
                Math.round((float) disconnectTimeMilli / totalCalls.get()));
        System.out.println("\n" + HORIZONTAL_RULE + "\n");
        System.out.printf("Percentage of time spent connecting   : %,9d\n",
                Math.round(connectTimeMilli * 100.0 / totalTimeMilli));
        System.out.printf("Percentage of time spent in proc call : %,9d\n",
                Math.round(procTimeMilli * 100.0 / totalTimeMilli));
        System.out.printf("Percentage of time spent disconnecting: %,9d\n",
                Math.round(disconnectTimeMilli * 100.0 / totalTimeMilli));
        System.out.println("\n" + HORIZONTAL_RULE + "\n");
    }

    /**
     * Core benchmark code for a jdbc client iteration
     * Connect. Call proc n times. Disconnect. Maintain counters.
     */
    private void runJdbcIteration(int client_id) {

        String driver = "org.voltdb.jdbc.Driver";
        String url = "jdbc:voltdb://" + config.server;
        
        try {

            // figure out time spent connecting
            connectionStartTime = System.currentTimeMillis();

            // Load driver. Create connection.
            Class.forName(driver);
            Connection conn = DriverManager.getConnection(url);
            
            connectionEndTime = System.currentTimeMillis();

            // store time spent connecting
            totalTimeMilli+=(connectionEndTime-connectionStartTime);
            connectTimeMilli+=(connectionEndTime-connectionStartTime);

            // figure out time spent in proc
            procCallsStartTime = System.currentTimeMillis();
                        
            for (int i=0; i<config.numberOfProcCallsPerConnection; i++) {

                try {

                    totalCalls.incrementAndGet();

                    CallableStatement proc = conn.prepareCall("{call create_client_location(?,?,?,?,?)}");
                    proc.setInt(1, i);
                    proc.setInt(2, client_id);
                    proc.setDouble(3, 10.0);
                    proc.setDouble(4, 15.0);
                    proc.setString(5, "some jdbc text to store");

                    ResultSet results = proc.executeQuery();

                    //Close statements, connections, etc.
                    proc.close();
                    results.close();

                    successCalls.incrementAndGet();

                } catch (Exception e) {
                    failedCalls.incrementAndGet();
                    e.printStackTrace();
                }
            }
/*
            while (results.next()) {
                System.out.printf("%d\n", results.getLong(1));
            }
*/            
            procCallsEndTime = System.currentTimeMillis();

            // store time spent in proc
            totalTimeMilli+=(procCallsEndTime-procCallsStartTime);
            procTimeMilli+=(procCallsEndTime-procCallsStartTime);

            // figure out time spent disconnecting
            disconnectionStartTime = System.currentTimeMillis();
            
            // close down the client connection
            conn.close();

            disconnectionEndTime = System.currentTimeMillis();

            // store time spent in proc
            totalTimeMilli+=(disconnectionEndTime-disconnectionStartTime);
            disconnectTimeMilli+=(disconnectionEndTime-disconnectionStartTime);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Core benchmark code for a native synchronous client iteration
     * Connect. Call proc n times. Disconnect. Maintain counters.
     */
    private void runNativeSynchIteration(int client_id) {
        
        Client client;
        
        try {

            // figure out time spent connecting
            connectionStartTime = System.currentTimeMillis();

            // connect
            ClientConfig clientConfig = new ClientConfig(config.user, config.password);
            client = ClientFactory.createClient(clientConfig);
            client.createConnection(config.server); // inline, no checking for errors (!) in order to get best time

            connectionEndTime = System.currentTimeMillis();

            // store time spent connecting
            totalTimeMilli+=(connectionEndTime-connectionStartTime);
            connectTimeMilli+=(connectionEndTime-connectionStartTime);

            // figure out time spent in proc
            procCallsStartTime = System.currentTimeMillis();
                        
            for (int i=0; i<config.numberOfProcCallsPerConnection; i++) {

                try {

                    totalCalls.incrementAndGet();

                    // synchronous call
                    client.callProcedure("create_client_location",
                                         i,
                                         client_id,
                                         100.0,
                                         150.0,
                                         "some synch text to store");


                    successCalls.incrementAndGet();

                } catch (Exception e) {
                    failedCalls.incrementAndGet();
                    e.printStackTrace();
                }
            }

            procCallsEndTime = System.currentTimeMillis();

            // store time spent in proc
            totalTimeMilli+=(procCallsEndTime-procCallsStartTime);
            procTimeMilli+=(procCallsEndTime-procCallsStartTime);

            // figure out time spent disconnecting
            disconnectionStartTime = System.currentTimeMillis();
            
            // close down the client connection
            client.close();

            disconnectionEndTime = System.currentTimeMillis();

            // store time spent in proc
            totalTimeMilli+=(disconnectionEndTime-disconnectionStartTime);
            disconnectTimeMilli+=(disconnectionEndTime-disconnectionStartTime);
             
        } catch (Exception e) {
            failedCalls.incrementAndGet();
            e.printStackTrace();
        }
    }

    /**
     * Core benchmark code for a native asynchronous client iteration
     * Connect. Call proc n times. Disconnect. Maintain counters.
     *
     * @throws Exception if anything unexpected happens.
     */
    private void runNativeAsynchIteration(int client_id) throws Exception {
        
        // try to connect and call proc with exponential backoff
        // Note: will continue to try for ever under various circumstances...
        // if something is long running then check the log

        connectionStartTime = System.currentTimeMillis();
        ClientConfig clientConfig = new ClientConfig(config.user, config.password, new MyStatusListener());
        Client client = ClientFactory.createClient(clientConfig);
        connectionEndTime = System.currentTimeMillis();
        // store time spent connecting
        totalTimeMilli+=(connectionEndTime-connectionStartTime);
        connectTimeMilli+=(connectionEndTime-connectionStartTime);

        int sleep = 1000;

        while (true) {
            // try to get a connection
            try {
                // figure out time spent connecting
                connectionStartTime = System.currentTimeMillis();
                client.createConnection(config.server);
            } 
            catch (IOException e) {
                // Note this should also catch NoConnectionsException
                System.err.printf("runNativeAsynchIteration() A: " +
                        "Connection failed: %s - retrying in %d second(s).\n", e.getMessage(), sleep / 1000);
                System.err.flush();
                Thread.sleep(sleep);
                if (sleep < 8000) sleep += sleep;
            }
            finally {
                connectionEndTime = System.currentTimeMillis();
                // store time spent connecting
                totalTimeMilli+=(connectionEndTime-connectionStartTime);
                connectTimeMilli+=(connectionEndTime-connectionStartTime);
            }

            // try to call the stored procedure
            try {
                // figure out time spent in proc
                procCallsStartTime = System.currentTimeMillis();
                            
                for (int i=0; i<config.numberOfProcCallsPerConnection; i++) {

                    // synchronous call
                    client.callProcedure(new MyProcedureCallback(),
                                        "create_client_location",
                                         i,
                                         client_id,
                                         1000.0,
                                         1500.0,
                                         "some asynch text to store");
                }
                break;
            } 
            catch (IOException e) {
                // Note this should also catch NoConnectionsException
                System.err.printf("runNativeAsynchIteration() B: " +
                        "Connection failed: %s - retrying in %d second(s).\n", e.getMessage(), sleep / 1000);
                System.err.flush();
                Thread.sleep(sleep);
                if (sleep < 8000) sleep += sleep;
            }
            finally {
                // block until all outstanding txns return
                client.drain();

                procCallsEndTime = System.currentTimeMillis();

                // store time spent in proc
                totalTimeMilli+=(procCallsEndTime-procCallsStartTime);
                procTimeMilli+=(procCallsEndTime-procCallsStartTime);

                // figure out time spent disconnecting
                disconnectionStartTime = System.currentTimeMillis();
                
                // close down the client connections
                client.close();

                disconnectionEndTime = System.currentTimeMillis();

                // store time spent in proc
                totalTimeMilli+=(disconnectionEndTime-disconnectionStartTime);
                disconnectTimeMilli+=(disconnectionEndTime-disconnectionStartTime);
            }
        }
    }
}
