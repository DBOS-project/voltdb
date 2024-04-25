package retwis;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.IllegalArgumentException;

import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.client.ClientFactory;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;
import org.voltdb.client.exampleutils.PerfCounterMap;

import retwis.RetwisSimulation;

public class Benchmark {
    private static class BenchArgs {
        public String type;
        public int numClients;
        public int totalSPCalls;
        public String servers;

        @Override
        public String toString() {
            return String.format("Type: %s, NumClients: %d, TotalSPCalls: %d, Servers: %s", type, numClients, totalSPCalls, servers);
        }
    }
    private class RunStats {
        public String setup;
        public long elapsedTime;
        public long totalTxns;
        public long totalExecutionTime;
        public long minExecutionTime;
        public long maxExecutionTime;
    }

    final String servers;
    final RetwisSimulation simulator;
    private Client client;
    private final boolean async;
    private final int numClients;
    public int totalSPCalls = 1_000_000_00;
    public static final ReentrantLock counterLock = new ReentrantLock();
    public long totExecutions = 0;
    public long totExecutionNanoseconds = 0;
    public long minExecutionNanoseconds = 999999999l;
    public long maxExecutionNanoseconds = 0;
    public Map<String,Long> typeNumExecution = new HashMap<String, Long>();
    public Map<String,Long> typeExecutionTime = new HashMap<String, Long>();

    public Benchmark(BenchArgs args) {
        System.out.println();
        System.out.println(args);
        this.servers = args.servers;
        // System.out.printf("Connecting to %s\n", servers);
        this.async = args.type.equals("async");
        this.numClients = args.numClients;
        this.totalSPCalls = args.totalSPCalls;
        // System.out.printf("Running %d clients\n", this.numClients);
        // System.out.println("Total Exec ms: " + this.totalSPCalls / 1000_000);

        // System.out.printf("async %b, totalSPCalls %d \n", this.async, totalSPCalls);
        
        this.client = Benchmark.getClient(this.servers);
        // System.out.println("Connected to server. About to create simulator");
        this.simulator = new RetwisSimulation(this.client, this.async);
    }

    private static Client getClient(String servers) {
        int sleep = 1000;
        while(true) {
            try {
                final Client client = ClientFactory.createClient();
                client.createConnection(servers, Client.VOLTDB_SERVER_PORT);
                // ClientConnection m_clientCon = ClientConnectionPool.get(servers, 21212);
                // System.out.println("Got Client Connection from pool");
                return client;
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
        int iters = 1_000_000;
        int unsuccessful = 0;
        // Insert ~30,000 users, ~850,000 posts, ~120,000 follows
        for (int i = 0; i < iters; i++) {
            if ((i * 10) % iters == 0 && i != 0)
                System.out.printf("Iteration %d\n", i);
            try {
                this.simulator.doInsertOne(new RetwisCallback(this, true));
            } catch (IOException e) {
                unsuccessful += 1;
            }
        }
        System.out.printf("Unsuccessful: %d\n", unsuccessful);
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
                this.simulator.doGetPosts(new RetwisCallback(this, true));
            }
            catch (IOException e) {}
            currentTime = System.currentTimeMillis();
            i += 1;
        }
    }

    public static void runAll(Map<String, List<String>> args) {
        BenchArgs thisArgs = new BenchArgs();
        List<RunStats> allStats = new ArrayList<>();
        for (String type: args.get("t")) {
            thisArgs.type = type;
            for (String numClients: args.get("c")) {
                thisArgs.numClients = Integer.parseInt(numClients);
                for (String totalSPCalls: args.get("n")) {
                    thisArgs.totalSPCalls = Integer.parseInt(totalSPCalls);
                    for (String servers: args.get("s")) {
                        thisArgs.servers = servers;
                        Benchmark benchmark = new Benchmark(thisArgs);
                        RunStats stats = benchmark.run();
                        allStats.add(stats);
                    }
                }
            }
        }

        System.out.println();
        System.out.println();
        System.out.println("============================== BENCHMARK RESULTS ==============================");
        System.out.printf("%-20s%-15s%-15s%-15s%-15s\n", "Setup", "Txns", "Time (ms)", "Txns/s", "Latency(us)");
        System.out.println("----------------------------------------------------------------------");
        for (RunStats runStat: allStats) {
            System.out.printf("%-20s%-15d%-15.2f%-15.2f%-15.2f\n", 
                                runStat.setup,
                                runStat.totalTxns,
                                (double) runStat.elapsedTime,
                                (double) runStat.totalTxns * 1000 / (double) runStat.elapsedTime,
                                (double) runStat.totalExecutionTime / (runStat.totalTxns * 1000));
        }
    }

    public RunStats run() {
        this.simulator.set_next_ids(700_000, 20_000);
        // this.setStatDeltaFlag();

        long startTime = System.currentTimeMillis();
        ThreadGroup workerClients = new ThreadGroup("clients");
        for (int i = 1; i < this.numClients; i++) {
            SingleClientRunnable r = new SingleClientRunnable(i, this);
            Thread th = new Thread(workerClients, r);
            th.start();
        }
        // Run one in parent thread
        SingleClientRunnable r = new SingleClientRunnable(0, this);
        r.run();

        while (workerClients.activeCount() > 0) {} // Wait for all threads to join
        long elapsedTime = System.currentTimeMillis() - startTime;
        RunStats stats = new RunStats();
        stats.setup = String.format("%s, %d clients", this.async ? "async" : "sync", this.numClients);
        stats.totalTxns = totExecutions;
        stats.elapsedTime = elapsedTime;
        stats.totalExecutionTime = totExecutionNanoseconds;
        stats.minExecutionTime = minExecutionNanoseconds;
        stats.maxExecutionTime = maxExecutionNanoseconds;
        return stats;
        // Map<String, ProcStats> procStats = this.getServerStats();

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
        // String query = "SELECT *" +
        //     " from statistics(PROCEDUREPROFILE,1);";
        // VoltTable[] results = null;
        // try {
        //     results = this.client.execute("@QueryStats", query).getResults();
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
    }

    private Map<String, ProcStats> getServerStats() {
        return null;
        // String query = "SELECT *" +
        //     " from statistics(PROCEDURE,1);";
        // VoltTable[] results = null;
        // try {
        //     results = this.m_clientCon.execute("@QueryStats", query).getResults();
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
        // VoltTable result = results[0];
        // Map<String, List<ProcStats>> procDetails = new HashMap<>();
        // while (result.advanceRow()) {
        //     String[] procedure = result.getString("PROCEDURE").split("\\.");
        //     String procedureName = procedure[procedure.length - 1];
        //     ProcStats stats = new ProcStats();
        //     stats.name = procedureName;
        //     stats.execTime = (double) result.getLong("AVG_EXECUTION_TIME") / 1000;
        //     stats.invocations = (int) result.getLong("INVOCATIONS");
        //     stats.resultSize = (double) result.getLong("AVG_RESULT_SIZE") / 1024;
        //     if (!procDetails.containsKey(procedureName))
        //         procDetails.put(procedureName, new ArrayList<>());
        //     procDetails.get(procedureName).add(stats);
        // }

        // Map<String, ProcStats> procSummary = new HashMap<>();
        // for (String proc: procDetails.keySet()) {
        //     double totalExecTime = 0;
        //     int totalInvocations = 0;
        //     double totalResSize = 0;
        //     for (ProcStats stat: procDetails.get(proc)) {
        //         totalExecTime += stat.execTime * stat.invocations;
        //         totalResSize += stat.resultSize * stat.invocations;
        //         totalInvocations += stat.invocations;
        //     }
        //     ProcStats thisStat = new ProcStats();
        //     thisStat.name = proc;
        //     thisStat.invocations = totalInvocations;
        //     thisStat.execTime = totalExecTime / totalInvocations;
        //     thisStat.resultSize = totalResSize / totalInvocations;
            
        //     procSummary.put(proc, thisStat);
        // }
        // return procSummary;
    }

    class ProcStats {
        String name;
        Double execTime;
        double resultSize;
        int invocations;
    }

    class RetwisCallback implements ProcedureCallback {
        Benchmark benchmark;
        boolean warmup;
        String procedure;
        public RetwisCallback(Benchmark benchmark, boolean warmup) {
            this.benchmark = benchmark;
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
                benchmark.totExecutionNanoseconds += executionTime;
                benchmark.totExecutions++;

                if (10 * benchmark.totExecutions % benchmark.totalSPCalls == 0) // Print 10 times
                    // System.out.printf("Iteration %d\n", benchmark.totExecutions);
                    System.out.printf("=");

                if (executionTime < benchmark.minExecutionNanoseconds) {
                    benchmark.minExecutionNanoseconds = executionTime;
                }

                if (executionTime > benchmark.maxExecutionNanoseconds) {
                    benchmark.maxExecutionNanoseconds = executionTime;
                }
                // System.out.println("Procedure:"+ typeNumExecution);

                benchmark.typeNumExecution.put(this.procedure, benchmark.typeNumExecution.getOrDefault(this.procedure, 0l) + 1);
                benchmark.typeExecutionTime.put(this.procedure, benchmark.typeExecutionTime.getOrDefault(this.procedure, 0l) + executionTime);
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
        private Benchmark benchmark;
        private RetwisSimulation sim;
        SingleClientRunnable(int id, Benchmark benchmark) {
            this.id = id;
            this.benchmark = benchmark;
            Client client = Benchmark.getClient(benchmark.servers);
            this.sim = new RetwisSimulation(client, benchmark.async);
            this.sim.set_next_ids(51200, 8192);
        }

        public void run() {
            // System.out.println("Running client " + this.id);
            for (int i = 0; i < this.benchmark.totalSPCalls / this.benchmark.numClients; i++) {
                try {
                    //
                    this.sim.doGetPosts(new RetwisCallback(this.benchmark, false));
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

    public static List<BenchArgs> getBenchArgs(Map<String, List<String>> args) {
        List<BenchArgs> benchArgs = new ArrayList<>();
        for (String type: args.get("t")) {
            for (String numClients: args.get("c")) {
                for (String totalSPCalls: args.get("n")) {
                    for (String servers: args.get("s")) {
                        BenchArgs thisArgs = new BenchArgs();
                        thisArgs.type = type;
                        thisArgs.numClients = Integer.parseInt(numClients);
                        thisArgs.totalSPCalls = Integer.parseInt(totalSPCalls);
                        thisArgs.servers = servers;
                        benchArgs.add(thisArgs);
                    }
                }
            }
        }
        return benchArgs;
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
        List<BenchArgs> benchArgs = getBenchArgs(parsedArgs);
        Benchmark benchmark = new Benchmark(benchArgs.get(0));
        String action = parsedArgs.get("a").get(0);
        if (action.equals("init"))
            benchmark.init_data();
        else if (action.equals("warmup"))
            benchmark.warmup_db(Integer.parseInt(parsedArgs.get("d").get(0)));
        else
            // benchmark.run();
            Benchmark.runAll(parsedArgs);
    }
}
