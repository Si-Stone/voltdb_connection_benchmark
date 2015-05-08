package connection_benchmark;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.voltdb.CLIConfig;
import org.voltdb.client.Client;
import org.voltdb.client.ClientConfig;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ClientStats;
import org.voltdb.client.ClientStatsContext;
import org.voltdb.client.ClientStatusListenerExt;
import org.voltdb.client.ProcedureCallback;

import java.sql.*;
import java.io.*;

public class ConnectionBenchmark {

    // handy, rather than typing this out several times
    static final String HORIZONTAL_RULE =
            "----------" + "----------" + "----------" + "----------" +
            "----------" + "----------" + "----------" + "----------" + "\n";

    // validated command line configuration
    final BenchmarkConfig config;
    
	// Reference to the database connection we will use
    Client client;
	
	long benchmarkStartTime;
	long benchmarkEndTime;

	long totalTimeMilli = 0;
	long connectTimeMilli = 0;
	long procTimeMilli = 0;
	long disconnectTimeMilli = 0;
	
	long connectionStartTime;
	long connectionEndTime;

	long procCallsStartTime;
	long procCallsEndTime;

	long disconnectionStartTime;
	long disconnectionEndTime;

    // overall success/failure counts
    AtomicLong totalCalls = new AtomicLong(0);
    AtomicLong successCalls = new AtomicLong(0);
    AtomicLong failedCalls = new AtomicLong(0);
    
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

        @Option(desc = "Client type - on of jdbc, native synch or native asynch (see run.sh)")
        String clientType = "";
	}

    /**
     * Constructor.
     * Configures VoltDB client.
     *
     * @param config Parsed & validated CLI options.
     */
    public ConnectionBenchmark(BenchmarkConfig config) {
        this.config = config;

		//System.out.printf("clientType is: %s\n", config.clientType);

		// valid clientType?
		if (! config.clientType.equals("jdbc_client") &&
			! config.clientType.equals("native_synch_client") &&
			! config.clientType.equals("native_asynch_client")) {
				throw new IllegalArgumentException("Uncatered for clientType");
		}
		
		
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

	 class StatusListener extends ClientStatusListenerExt {
        @Override
        public void connectionLost(String hostname, int port, int connectionsLeft, DisconnectCause cause) {
			//System.Out.printf("Connection to %s:%d was lost: %s. Number of connections left is: %d\n", hostname, port, cause, connectionsLeft);
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
                System.err.printf("Connection failed - retrying in %d second(s).\n", sleep / 1000);
                try { Thread.sleep(sleep); } catch (Exception interruted) {}
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
/*   Only catering for 1 server
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
    class CountCallback implements ProcedureCallback {
        @Override
        public void clientCallback(ClientResponse response) throws Exception {
            totalCalls.incrementAndGet();
            if (response.getStatus() == ClientResponse.SUCCESS) {
                successCalls.incrementAndGet();
            }
            else {
                failedCalls.incrementAndGet();
                System.err.println("Procedure returned with error: " + response.getStatusString());
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
     * Setup the benchmark counters, control the looping, output the results.
     *
     * @throws Exception if anything unexpected happens.
     */
    public void runBenchmark() throws Exception {
		System.out.println("\n" + HORIZONTAL_RULE + " Benchmark Starts\n" + HORIZONTAL_RULE);
		
		benchmarkStartTime = System.currentTimeMillis();
		
		for (int i=0; i<config.numberOfConnections; i++) {

			if (config.clientType.equals("jdbc_client")) {
				runJdbcIteration(i);
			} else if  (config.clientType.equals("native_synch_client")) {
				runNativeSynchIteration(i);
			} else if  (config.clientType.equals("native_asynch_client")) {
				runNativeAsynchIteration(i);
			}
			
		}

		benchmarkEndTime = System.currentTimeMillis();
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
		System.out.printf("Total comparison time per connection in milliseconds was         : %d\n", Math.round((float) totalTimeMilli / totalCalls.get()));
		System.out.printf("Connect comparison time per connection in milliseconds was       : %d\n", Math.round((float) connectTimeMilli / totalCalls.get()));
		System.out.printf("Procedure call comparison time per connection in milliseconds was: %d\n", Math.round((float) procTimeMilli / totalCalls.get()));
		System.out.printf("Disconnect comparison time per connection in milliseconds was    : %d\n", Math.round((float) disconnectTimeMilli / totalCalls.get()));
		System.out.println("\n" + HORIZONTAL_RULE + "\n");
		System.out.printf("Percentage of time spent connecting   : %,9d\n", Math.round(connectTimeMilli * 100.0 / totalTimeMilli));
		System.out.printf("Percentage of time spent in proc call : %,9d\n", Math.round(procTimeMilli * 100.0 / totalTimeMilli));
		System.out.printf("Percentage of time spent disconnecting: %,9d\n", Math.round(disconnectTimeMilli * 100.0 / totalTimeMilli));
		System.out.println("\n" + HORIZONTAL_RULE + "\n");
    }

    /**
     * Core benchmark code for a jdbc client iteration
     * Connect. Call proc n times. Disconnect. Maintain counters.
     *
     * @throws Exception if anything unexpected happens.
     */
    public void runJdbcIteration(int client_id) throws Exception {

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
     *
     * @throws Exception if anything unexpected happens.
     */
    public void runNativeSynchIteration(int client_id) throws Exception {
        
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
    public void runNativeAsynchIteration(int client_id) throws Exception {

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

			// synchronous call
			client.callProcedure(new CountCallback(),
								"create_client_location",
								 i,
								 client_id,
								 1000.0,
								 1500.0,
								 "some asynch text to store");
		}

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
