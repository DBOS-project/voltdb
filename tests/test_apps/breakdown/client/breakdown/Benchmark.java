package breakdown;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;
import org.voltdb.client.exampleutils.PerfCounterMap;

public class Benchmark {
    private Client client;
    private final int NUM_USERS = 100000;
    private final Random rnd = new Random();

    public Benchmark() {
        String server = "localhost";
        System.out.printf("Connecting to server: %s\n", server);
        this.client = ClientFactory.createClient();
        int sleep = 1000;
        while(true) {
            try {
                client.createConnection(server);
                break;
            }
            catch (Exception e) {
                System.err.printf("Connection failed - retrying in %d second(s).\n", sleep/1000);
                try {Thread.sleep(sleep);} catch(Exception tie){}
                if (sleep < 8000)
                    sleep += sleep;
            }
        }
    }

    public void warmup() {
        for (int i=0; i < NUM_USERS; i++) {
            String username = "johndoe" + rnd.nextInt();
            String email = username + "@mit.edu";
            String photo = "photo-" + username + ".jpg";
            try {
                VoltTable result = this.client.callProcedure("DoInsertUser", i, username, email, photo).getResults()[0];
            } catch (Exception e) {
                System.out.println(e);
            }
        }
        System.out.printf("Inserted %d users\n", NUM_USERS);
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        int u_id = rnd.nextInt(NUM_USERS);
        System.out.println("Getting "+ u_id);
        VoltTable result;
        try {
            result = this.client.callProcedure("DoGetUser", u_id).getResults()[0];
            // System.out.println("Result: " + );
            result.advanceRow();
        } catch (Exception e) {
            System.out.println(e);
            result = null;
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("============================== BENCHMARK RESULTS ==============================");
        System.out.printf("Time: %d ms\n", elapsedTime);
        System.out.printf("Returned: %s\n", result);
        System.out.println("===============================================================================\n");
    }
    
    /**
     * Main routine creates a benchmark instance and kicks off the run method.
     *
     * @param args Command line arguments.
     * @throws Exception if anything goes wrong.
     * @see {@link VoterConfig}
     */
    public static void main(String[] args) throws Exception {
        String arg = args[0];
        Benchmark benchmark = new Benchmark();
        if (arg.equals("warmup"))
            benchmark.warmup();
        else
            benchmark.run();
    }
}
