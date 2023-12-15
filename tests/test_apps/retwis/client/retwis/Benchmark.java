package retwis;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.IllegalArgumentException;

import org.voltdb.VoltTable;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;
import org.voltdb.client.exampleutils.PerfCounterMap;

import retwis.RetwisSimulation;

public class Benchmark {
    final String servers;
    final RetwisSimulation simulator;
    private ClientConnection m_clientCon;
    private boolean async;
    private int numClients;
    private static int totalSPCalls = 1_000_000;
    public static final ReentrantLock counterLock = new ReentrantLock();
    public static long totExecutions = 0;
    public static long totExecutionNanoseconds = 0;
    public static long minExecutionNanoseconds = 999999999l;
    public static long maxExecutionNanoseconds = 0;
    public static Map<String,Long> typeNumExecution = new HashMap<String, Long>();
    public static Map<String,Long> typeExecutionTime = new HashMap<String, Long>();

    public Benchmark(Map<String, List<String>> args) {
        this.servers = args.get("s").get(0);
        System.out.printf("Connecting to %s\n", servers);
        this.async = args.get("t").get(0).equals("async");
        this.numClients = Integer.parseInt(args.get("c").get(0));
        Benchmark.totalSPCalls = Integer.parseInt(args.get("n").get(0));
        System.out.printf("Running %d clients\n", this.numClients);
        
        this.m_clientCon = Benchmark.getClient(this.servers);
        this.simulator = new RetwisSimulation(this.m_clientCon, this.async);
    }

    private static ClientConnection getClient(String servers) {
        int sleep = 1000;
        while(true) {
            try {
                ClientConnection m_clientCon = ClientConnectionPool.get(servers, 21212);
                return m_clientCon;
            }
            catch (Exception e) {
                System.err.printf("Connection failed - retrying in %d second(s).\n", sleep/1000);
                try {Thread.sleep(sleep);} catch(Exception tie){}
                if (sleep < 8000)
                    sleep += sleep;
            }
        }
    }

    public void init_data() {
        System.out.println("Initializing data in db");
        // Insert ~30,000 users, ~850,000 posts, ~120,000 follows
        for (int i = 0; i < 1_000_000; i++) {
            try {
                this.simulator.doInsertOne(new RetwisCallback(true));
            }
            catch (IOException e) {}
        }
    }

    public void warmup_db(int warmupDuration) {
        this.simulator.set_next_ids(700_000, 20_000);
        System.out.println("Warming up the db");
        long warmupEndTime = System.currentTimeMillis() + (warmupDuration - 5) * 1000; // Buffer of 5 second to cooldown
        long currentTime = System.currentTimeMillis();
        int i = 0;
        while (currentTime < warmupEndTime) {
            if (i % 100_000 == 0 && i != 0)
                System.out.printf("Iteration %d\n", i);
            try {
                this.simulator.doGetPosts(new RetwisCallback(true));
            }
            catch (IOException e) {}
            currentTime = System.currentTimeMillis();
            i += 1;
        }
    }

    public void run() {
        this.simulator.set_next_ids(700_000, 20_000);
        // this.setStatDeltaFlag();

        long startTime = System.currentTimeMillis();
        ThreadGroup workerClients = new ThreadGroup("clients");
        for (int i = 1; i < this.numClients; i++) {
            SingleClientRunnable r = new SingleClientRunnable(i, Benchmark.totalSPCalls/numClients, this.servers, this.async);
            Thread th = new Thread(workerClients, r);
            th.start();
        }
        // Run one in parent thread
        SingleClientRunnable r = new SingleClientRunnable(0, Benchmark.totalSPCalls/numClients, this.servers, this.async);
        r.run();

        while (workerClients.activeCount() > 0) {} // Wait for all threads to join
        long elapsedTime = System.currentTimeMillis() - startTime;
        // Map<String, ProcStats> procStats = this.getServerStats();

        System.out.println("============================== BENCHMARK RESULTS ==============================");
        System.out.printf("Time: %d ms\n", elapsedTime);
        System.out.printf("Total transactions: %d\n", totExecutions);
        System.out.printf("Transactions per second: %.2f\n", (float)totExecutions * 1000 / elapsedTime);
        System.out.printf("Latency(us): %.2f < %.2f < %.2f\n",
                            (double) minExecutionNanoseconds / 1000,
                            ((double) totExecutionNanoseconds / ((double) totExecutions * 1000)),
                            (double) maxExecutionNanoseconds / 1000);

        // PerfCounterMap map = ClientConnectionPool.getStatistics(m_clientCon);
        // System.out.println(map);
        // System.out.print(m_clientCon.getStatistics(Constants.TRANS_PROCS).toString(false));
        // System.out.println("===============================================================================\n");

        // System.out.println("----------------------- Breakdown --------------------------");
        // System.out.printf("%-15s%-20s%-15s%-20s%-20s\n", "Procedure", "Throughput(txns/s)", "Latency(us)", "Execution Time(us)", "Result size(KB)");
        // System.out.println("------------------------------------------------------------");
        // for (String procedure: typeNumExecution.keySet()) {
        //     ProcStats thisStat = procStats.get(procedure);
        //     System.out.printf("%-15s%-20.2f%-15.2f%-20.2f%-20.2f\n", 
        //                         procedure,
        //                         (double) typeNumExecution.get(procedure) * 1000 / elapsedTime,
        //                         (double) typeExecutionTime.get(procedure) / (typeNumExecution.get(procedure) * 1000),
        //                         thisStat.execTime,
        //                         thisStat.resultSize);
        // }
    }

    private void setStatDeltaFlag() {
        String query = "SELECT *" +
            " from statistics(PROCEDUREPROFILE,1);";
        VoltTable[] results = null;
        try {
            results = this.m_clientCon.execute("@QueryStats", query).getResults();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, ProcStats> getServerStats() {
        String query = "SELECT *" +
            " from statistics(PROCEDURE,1);";
        VoltTable[] results = null;
        try {
            results = this.m_clientCon.execute("@QueryStats", query).getResults();
        } catch (Exception e) {
            e.printStackTrace();
        }
        VoltTable result = results[0];
        Map<String, List<ProcStats>> procDetails = new HashMap<>();
        while (result.advanceRow()) {
            String[] procedure = result.getString("PROCEDURE").split("\\.");
            String procedureName = procedure[procedure.length - 1];
            ProcStats stats = new ProcStats();
            stats.name = procedureName;
            stats.execTime = (double) result.getLong("AVG_EXECUTION_TIME") / 1000;
            stats.invocations = (int) result.getLong("INVOCATIONS");
            stats.resultSize = (double) result.getLong("AVG_RESULT_SIZE") / 1024;
            if (!procDetails.containsKey(procedureName))
                procDetails.put(procedureName, new ArrayList<>());
            procDetails.get(procedureName).add(stats);
        }

        Map<String, ProcStats> procSummary = new HashMap<>();
        for (String proc: procDetails.keySet()) {
            double totalExecTime = 0;
            int totalInvocations = 0;
            double totalResSize = 0;
            for (ProcStats stat: procDetails.get(proc)) {
                totalExecTime += stat.execTime * stat.invocations;
                totalResSize += stat.resultSize * stat.invocations;
                totalInvocations += stat.invocations;
            }
            ProcStats thisStat = new ProcStats();
            thisStat.name = proc;
            thisStat.invocations = totalInvocations;
            thisStat.execTime = totalExecTime / totalInvocations;
            thisStat.resultSize = totalResSize / totalInvocations;
            
            procSummary.put(proc, thisStat);
        }
        return procSummary;
    }

    class ProcStats {
        String name;
        Double execTime;
        double resultSize;
        int invocations;
    }

    class RetwisCallback implements ProcedureCallback {
        boolean warmup;
        String procedure;
        public RetwisCallback(boolean warmup) {
            this.warmup = warmup;
        }

        public void setProcedure(String procedure) {
            this.procedure = procedure;
        }

        @Override
        public void clientCallback(ClientResponse clientResponse)
        {
            assert clientResponse.getStatus() == ClientResponse.SUCCESS;
            if (warmup) return;
            counterLock.lock();
            try {
                long executionTime =  clientResponse.getClientRoundtripNanos();
                totExecutionNanoseconds += executionTime;
                totExecutions++;

                if (10 * totExecutions % Benchmark.totalSPCalls == 0) // Print 10 times
                    System.out.printf("Iteration %d\n", totExecutions);

                if (executionTime < minExecutionNanoseconds) {
                    minExecutionNanoseconds = executionTime;
                }

                if (executionTime > maxExecutionNanoseconds) {
                    maxExecutionNanoseconds = executionTime;
                }
                // System.out.println("Procedure:"+ typeNumExecution);

                typeNumExecution.put(this.procedure, typeNumExecution.getOrDefault(this.procedure, 0l) + 1);
                typeExecutionTime.put(this.procedure, typeExecutionTime.getOrDefault(this.procedure, 0l) + executionTime);
                // System.out.println("Nums:"+ typeNumExecution);
            } catch (Exception e) {
                System.out.println(e);
            }
            finally
            {
                
                counterLock.unlock();
            }
        }
    } 

    class SingleClientRunnable implements Runnable {
        private int id;
        private int totalSPCalls;
        private RetwisSimulation sim;
        SingleClientRunnable(int id, int totalSPCalls, String servers, boolean async) {
            this.id = id;
            this.totalSPCalls = totalSPCalls;
            ClientConnection client = Benchmark.getClient(servers);
            this.sim = new RetwisSimulation(client, async);
            this.sim.set_next_ids(51200, 8192);
        }

        public void run() {
            for (int i = 0; i < this.totalSPCalls; i++) {
                try {
                    //
                    this.sim.doGetPosts(new RetwisCallback(false));
                    // this.sim.doOne(new RetwisCallback(false));
                }
                catch (IOException e) {}
            }
        }
    }

    private static Map<String, List<String>> getDefaultArgs() {
        final Map<String, List<String>> args = new HashMap<>();
        args.put("t", Arrays.asList("async")); // Type of operations
        args.put("c", Arrays.asList("1")); // Number of clients
        args.put("n", Arrays.asList("1000000")); // Number of transactions
        args.put("s", Arrays.asList("localhost")); // Host IP
        args.put("a", Arrays.asList("run")); // Action: one of init, warmup, run
        args.put("d", Arrays.asList("20")); // Run duration in case of warmup
        return args;
    }

    private static Map<String, List<String>> parseArgs(String[] args) throws IllegalArgumentException {
        final Map<String, List<String>> params = Benchmark.getDefaultArgs();

        List<String> options = null;
        for (int i = 0; i < args.length; i++) {
            final String a = args[i];

            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    throw new IllegalArgumentException();
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            }
            else if (options != null) {
                options.add(a);
            }
            else {
                System.err.println("Illegal parameter usage");
                throw new IllegalArgumentException();
            }
        }

        return params;
    }
    
    /**
     * Main routine creates a benchmark instance and kicks off the run method.
     *
     * @param args Command line arguments.
     * @throws Exception if anything goes wrong.
     * @see {@link VoterConfig}
     */
    public static void main(String[] args) throws Exception {
        Map<String, List<String>> parsedArgs = parseArgs(args);
        System.out.println("Parsed Args:" + parsedArgs.entrySet());
        Benchmark benchmark = new Benchmark(parsedArgs);
        String action = parsedArgs.get("a").get(0);
        if (action.equals("init"))
            benchmark.init_data();
        else if (action.equals("warmup"))
            benchmark.warmup_db(Integer.parseInt(parsedArgs.get("d").get(0)));
        else
            benchmark.run();
    }
}
