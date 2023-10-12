package retwis;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.client.exampleutils.ClientConnection;
import org.voltdb.client.exampleutils.ClientConnectionPool;
import org.voltdb.client.exampleutils.PerfCounterMap;

public class Benchmark {
    final RetwisSimulation simulator;
    private ClientConnection m_clientCon;
    private boolean async;
    public static final ReentrantLock counterLock = new ReentrantLock();
    public static long totExecutions = 0;
    public static long totExecutionNanoseconds = 0;
    public static long minExecutionNanoseconds = 999999999l;
    public static long maxExecutionNanoseconds = 0;
    public static Map<String,Long> typeNumExecution = new HashMap<String, Long>();
    public static Map<String,Long> typeExecutionTime = new HashMap<String, Long>();

    public Benchmark() {
        String servers = "localhost";
        System.out.printf("Connecting to servers: %s\n", servers);
        int sleep = 1000;
        this.async = false;
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
        long warmupDuration = 2000; // in ms
        long testDuration = 15000; // in ms
        long warmupEndTime = System.currentTimeMillis() + warmupDuration;
        long currentTime = System.currentTimeMillis();
        while (currentTime < warmupEndTime) {
            try {
                this.simulator.doOne(new RetwisCallback(true));
            }
            catch (IOException e) {}
            currentTime = System.currentTimeMillis();
        }

        long startTime = System.currentTimeMillis();
        long testEndTime = System.currentTimeMillis() + testDuration;
        int numSPCalls = 0;
        int totalSPCalls = 500000;

        while (numSPCalls < totalSPCalls) {
            numSPCalls += 1;
            try {
                this.simulator.doOne(new RetwisCallback(false));
            }
            catch (IOException e) {}
            currentTime = System.currentTimeMillis();
        }

        long elapsedTime = System.currentTimeMillis() - startTime;

        System.out.println("============================== BENCHMARK RESULTS ==============================");
        System.out.printf("Time: %d ms\n", elapsedTime);
        System.out.printf("Total transactions: %d\n", numSPCalls);
        System.out.printf("Transactions per second: %.2f\n", (float)numSPCalls / (elapsedTime / 1000));
        System.out.println("===============================================================================\n");
        System.out.println("============================== SYSTEM STATISTICS ==============================");
        System.out.printf(" - Average Latency = %.2f us\n", ((double) totExecutionNanoseconds / ((double) totExecutions * 1000)));
        System.out.printf(" - Min Latency = %.2f us\n", (double) minExecutionNanoseconds / 1000);
        System.out.printf(" - Max Latency = %.2f us\n\n", (double) maxExecutionNanoseconds / 1000);

        PerfCounterMap map = ClientConnectionPool.getStatistics(m_clientCon);
        System.out.println(map);
        System.out.print(m_clientCon.getStatistics(Constants.TRANS_PROCS).toString(false));
        System.out.println("===============================================================================\n");

        System.out.println("Breakdown stats");
        for (String procedure: typeNumExecution.keySet()) {
            System.out.printf("%s: %.2f us\n", procedure, (double) typeExecutionTime.get(procedure) / (typeNumExecution.get(procedure) * 1000));
        }
    }

    class RetwisCallback
        implements ProcedureCallback
    {
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
    
    /**
     * Main routine creates a benchmark instance and kicks off the run method.
     *
     * @param args Command line arguments.
     * @throws Exception if anything goes wrong.
     * @see {@link VoterConfig}
     */
    public static void main(String[] args) throws Exception {
        // create a configuration from the arguments
        // VoterConfig config = new VoterConfig();
        // config.parse(AsyncBenchmark.class.getName(), args);

        Benchmark benchmark = new Benchmark();
        benchmark.run();
    }
}
