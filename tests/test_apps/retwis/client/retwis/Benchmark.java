package retwis;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.voltdb.VoltTable;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;
import org.voltdb.client.exampleutils.PerfCounterMap;

import retwis.RetwisSimulation;

public class Benchmark {
    final RetwisSimulation simulator;
    private ClientConnection m_clientCon;
    private boolean async;
    private int numClients;
    public static final ReentrantLock counterLock = new ReentrantLock();
    public static final int totalSPCalls = 1000000;
    public static long totExecutions = 0;
    public static long totExecutionNanoseconds = 0;
    public static long minExecutionNanoseconds = 999999999l;
    public static long maxExecutionNanoseconds = 0;
    public static Map<String,Long> typeNumExecution = new HashMap<String, Long>();
    public static Map<String,Long> typeExecutionTime = new HashMap<String, Long>();

    public Benchmark(String[] args) {
        String servers = "18.26.2.124";
        System.out.printf("Connecting to %s\n", servers);
        int sleep = 1000;
        this.async = (args.length >= 1) ? (args[0].equals("async")) : false;
        this.numClients = (args.length == 2)? Integer.parseInt(args[1]) : 1;
        System.out.printf("Running %d clients\n", this.numClients);
        
        while(true) {
            try {
                this.m_clientCon = ClientConnectionPool.get(servers, 21212);
                break;
            }
            catch (Exception e) {
                System.err.printf("Connection failed - retrying in %d second(s).\n", sleep/1000);
                try {Thread.sleep(sleep);} catch(Exception tie){}
                if (sleep < 8000)
                    sleep += sleep;
            }
        }
        this.simulator = new RetwisSimulation(this.m_clientCon, this.async);
    }

    public void run() {
        long warmupDuration = 5000; // in ms
        long warmupEndTime = System.currentTimeMillis() + warmupDuration;
        long currentTime = System.currentTimeMillis();
        while (currentTime < warmupEndTime) {
            try {
                this.simulator.doWarmupOne(new RetwisCallback(true));
            }
            catch (IOException e) {}
            currentTime = System.currentTimeMillis();
        }


        long startTime = System.currentTimeMillis();
        ThreadGroup workerClients = new ThreadGroup("clients");
        for (int i = 1; i < this.numClients; i++) {
            SingleClientRunnable r = new SingleClientRunnable(this.simulator, totalSPCalls);
            Thread th = new Thread(workerClients, r);
            th.start();
        }
        // Run one in parent thread
        SingleClientRunnable r = new SingleClientRunnable(this.simulator, totalSPCalls);
        r.run();

        while (workerClients.activeCount() > 0) {} // Wait for all threads to join
        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("============================== BENCHMARK RESULTS ==============================");
        System.out.printf("Time: %d ms\n", elapsedTime);
        System.out.printf("Total transactions: %d\n", totExecutions);
        System.out.printf("Transactions per second: %.2f\n", (float)totExecutions / (elapsedTime / 1000));
        System.out.printf("Latency(us): %.2f < %.2f < %.2f\n",
                            (double) minExecutionNanoseconds / 1000,
                            ((double) totExecutionNanoseconds / ((double) totExecutions * 1000)),
                            (double) maxExecutionNanoseconds / 1000);

        // PerfCounterMap map = ClientConnectionPool.getStatistics(m_clientCon);
        // System.out.println(map);
        // System.out.print(m_clientCon.getStatistics(Constants.TRANS_PROCS).toString(false));
        // System.out.println("===============================================================================\n");

        Map<String, Double> execTimes = this.getServerStats();
        System.out.println("----------------------- Breakdown --------------------------");
        System.out.printf("%-15s%-20s%-15s%-20s\n", "Procedure", "Throughput(txns/s)", "Latency(us)", "Execution Time(us)");
        System.out.println("------------------------------------------------------------");
        for (String procedure: typeNumExecution.keySet()) {
            System.out.printf("%-15s%-20.2f%-15.2f%-20.2f\n", 
                                procedure,
                                (double) typeNumExecution.get(procedure) * 1000 / elapsedTime,
                                (double) typeExecutionTime.get(procedure) / (typeNumExecution.get(procedure) * 1000),
                                execTimes.get(procedure));
        }
    }

    private Map<String, Double> getServerStats() {
        String query = "SELECT *" +
            " from statistics(PROCEDUREPROFILE,0);";
        VoltTable[] results = null;
        try {
            results = this.m_clientCon.execute("@QueryStats", query).getResults();
        } catch (Exception e) {
            e.printStackTrace();
        }
        VoltTable result = results[0];
        Map<String, Double> execTimes = new HashMap<>();
        while (result.advanceRow()) {
            String[] procedure = result.getString(1).split("\\.");
            String procedureName = procedure[procedure.length - 1];
            execTimes.put(procedureName, (double) result.getLong(4) / 1000);
        }
        return execTimes;
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
        private RetwisSimulation sim;
        private int totalSPCalls;
        SingleClientRunnable(RetwisSimulation sim, int totalSPCalls) {
            this.sim = sim;
            this.totalSPCalls = totalSPCalls;
        }

        public void run() {
            for (int i = 0; i < this.totalSPCalls / numClients; i++) {
                try {
                    //
                    this.sim.doGetPosts(new RetwisCallback(false));
                    // this.sim.doOne(new RetwisCallback(false));
                }
                catch (IOException e) {}
            }
        }
    }
    
    /**
     * Main routine creates a benchmark instance and kicks off the run method.
     *
     * @param args Command line arguments.
     * @throws Exception if anything goes wrong.
     * @see {@link VoterConfig}
     */
    public static void main(String[] args) throws Exception {
        assert args.length >= 1;
        System.out.println("Running " + args[0] + " benchmark");
        Benchmark benchmark = new Benchmark(args);
        benchmark.run();
    }
}
