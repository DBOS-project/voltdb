/* This file is part of VoltDB.
 * Copyright (C) 2008-2022 Volt Active Data Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.voltcore.logging.VoltLogger;
import org.voltdb.jni.ExecutionEngine;
import org.voltdb.utils.InMemoryJarfile;
import org.voltdb.utils.SerializationHelper;
import java.util.Arrays;

class ProcedureRunnerProxy{
    VoltVMProcedure procedure;
    private List<String> queuedSQLStmtVarNames;
    private List<Object[]> queuedSQLParams;
    private Map<SQLStmt, String> stmtToNames;
    
    // track how long each query takes on average to execute, in microseconds
    private Map<String, Long> stmtNameToTotalExecutionTimes;
    private Map<String, Long> stmtNameToExecutionTimeCount;
    private long runTime = 0;
    private long runTimeMin = 10000000;
    private long runTimeMax = 0;

    private long printCount = 0;
    private Map<String, Long> sqlStatementIterationCount = new HashMap<>();
    private Map<String, Long> sqlStatementInterruptionRuntime = new HashMap<>();
    private Map<String, List<Long>> sqlStatementRuntimeTracker = new HashMap<>();
    private Map<String, Long> sqlStatementRuntimeTrackerTotal = new HashMap<>();
    private Map<String, List<Long>> sqlStatementInterruptRuntimeTracker = new HashMap<>();
    private Map<String, Long> sqlStatementInterruptRuntimeTrackerTotal = new HashMap<>();
    private Map<String, Long> sqlStatementActualDelayCounter = new HashMap<>();
    private Map<String, Long> sqlStatementMin = new HashMap<>();
    private Map<String, Long> sqlStatementMax = new HashMap<>();

    InterVMMessagingProtocol protocol;
    org.nustaq.serialization.FSTConfiguration fstConf;
    ByteBuffer buffer = null;
    ArrayDeque<VMProcedureCall> queuedCalls = null;
    InterVMMessage oldMessage = null;
    ProcedureRunnerProxy(VoltVMProcedure procedure, InterVMMessagingProtocol protocol, org.nustaq.serialization.FSTConfiguration fstConf, ArrayDeque<VMProcedureCall> queuedCalls) {
        this.queuedSQLStmtVarNames = new ArrayList<>();
        this.queuedSQLParams = new ArrayList<>();
        this.procedure = procedure;
        this.protocol = protocol;
        this.queuedCalls = queuedCalls;
        this.stmtToNames = new HashMap<>();
        this.fstConf = fstConf;

        this.stmtNameToTotalExecutionTimes = new HashMap<>();
        this.stmtNameToExecutionTimeCount = new HashMap<>();

        Field[] fields = this.procedure.getClass().getDeclaredFields();
        for (Field f : fields) {
            // skip non SQL fields
            if (f.getType() != SQLStmt.class) {
                continue;
            }

            int modifiers = f.getModifiers();

            // skip private fields if asked (usually a superclass)
            if (java.lang.reflect.Modifier.isPrivate(modifiers)) {
                continue;
            }

            // don't allow non-final SQLStmts
            if (java.lang.reflect.Modifier.isFinal(modifiers) == false) {
                String msg = "Procedure " + procedure.getClass().getCanonicalName() + " contains a non-final SQLStmt field.";
                throw new RuntimeException(msg);
            }

            f.setAccessible(true);

            SQLStmt stmt = null;

            try {
                stmt = (SQLStmt) f.get(procedure);
            }
            // this exception handling here comes from other parts of the code
            // it's weird, but seems rather hard to hit
            catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            
            stmtToNames.put(stmt, f.getName());
            stmtNameToTotalExecutionTimes.put(f.getName(), 0L);
            stmtNameToExecutionTimeCount.put(f.getName(), 0L);
        }
    }

    public long getUniqueId() {
        // TODO
        return 0;
    }

    public int getClusterId() {
        // TODO
        return 0;
    }

    public Random getSeededRandomNumberGenerator() {
        // TODO
        return new Random();
    }

    public Date getTransactionTime() {
        return new Date();
    }    
    
    public void setAppStatusCode(byte statusCode) {
    }

    public void setAppStatusString(String statusString) {
    }

    public int getPartitionId() {
        // TODO
        return 0;
    }
    
    public void voltQueueSQL(final SQLStmt stmt, Expectation expectation, Object... args) {
        if (stmt == null) {
            throw new IllegalArgumentException("SQLStmt parameter to voltQueueSQL(..) was null.");
        }
        if (stmtToNames.containsKey(stmt) == false) {
            throw new IllegalArgumentException("SQLStmt not recognizable.");
        }

        queuedSQLStmtVarNames.add(stmtToNames.get(stmt));
        queuedSQLParams.add(args);
    }

    // public static int globalCount = 0;

    private double calculateStd(List<Long> datapoints, double mean) {
        double squaredVariance = 0;
        for(long time : datapoints) {
            squaredVariance += (double) (time - mean) * (time - mean);
        }
        return Math.sqrt((double) squaredVariance / datapoints.size());
    }
    
    public VoltTable[] voltExecuteSQL(boolean isFinalSQL, boolean ignoreResults) { // runs in SPVM
        // write the query(ies) to memory
        long t = System.nanoTime();
        try {
            org.nustaq.serialization.FSTObjectOutput objectOutput = fstConf.getObjectOutput();
            objectOutput.writeObject(isFinalSQL);
            objectOutput.writeObject(ignoreResults);
            objectOutput.writeObject(queuedSQLStmtVarNames);
            objectOutput.writeObject(queuedSQLParams);
            //return objectOutput.getCopyOfWrittenBuffer();
            protocol.writeExecuteQueryRequestMessage(objectOutput.getCopyOfWrittenBuffer());
            //objectOutput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String varNamesString = queuedSQLStmtVarNames.toString();

        // custom timer to sleep first for a bit
        int wakeup_delay_ns = 0;
        // if(false) {
        if(sqlStatementRuntimeTracker.containsKey(varNamesString) && sqlStatementInterruptRuntimeTracker.get(varNamesString).size() > 5) {
            if(!sqlStatementActualDelayCounter.containsKey(varNamesString)) {
                sqlStatementActualDelayCounter.put(varNamesString, 0L);
            }
            sqlStatementActualDelayCounter.put(varNamesString, sqlStatementActualDelayCounter.get(varNamesString) + 1);
            
            // run 2/3 times
            if(sqlStatementActualDelayCounter.get(varNamesString) % 3 != 0) {
                long totalRuntime = sqlStatementRuntimeTrackerTotal.get(varNamesString);
                long totalInterruptRuntime = sqlStatementInterruptRuntimeTrackerTotal.get(varNamesString);

                long iterationCount = sqlStatementRuntimeTracker.get(varNamesString).size();
                int MAX_MEAN_NANOSECONDS = 100 * 1000;
                int meanNanosecond = (int) ((double) totalRuntime / iterationCount);
                int meanInterruptNanosecond = (int) ((double) totalInterruptRuntime / iterationCount);
                int threshold = 4000; // nanoseconds
                wakeup_delay_ns = Math.min(meanNanosecond - meanInterruptNanosecond - threshold, MAX_MEAN_NANOSECONDS);
                if(printCount++ % 100000 == 0) {
                    System.out.println("WAIT:" + varNamesString + ": " + meanNanosecond + " - " + meanInterruptNanosecond + " - " + threshold + " = " + (wakeup_delay_ns));
                }
                
                if(wakeup_delay_ns > 2000) {
                    protocol.getChannel().runWaitTimer(wakeup_delay_ns);
                }
            }
        }

        queuedSQLStmtVarNames.clear();
        queuedSQLParams.clear();
        VoltTable[] result = null; 
        // read the queries from memory
        long estimatedInterruptionTime = 0;
        while (true) {
            long potentialTime = System.nanoTime();
            InterVMMessage msg = protocol.getNextMessage(oldMessage, null, varNamesString);
            if (msg.type == InterVMMessage.kProcedureCallReq) {
                VMProcedureCall call = null;
                try {
                    org.nustaq.serialization.FSTObjectInput objectsInput = fstConf.getObjectInput(msg.data.array(), msg.data.limit());
                    call = (VMProcedureCall)objectsInput.readObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                this.queuedCalls.offer(call);
                if (msg.data != null) {
                    if (buffer == null || msg.data.capacity() > buffer.capacity()) {
                        buffer = msg.data;
                    }
                }
                estimatedInterruptionTime += (System.nanoTime() - potentialTime);
            } else {
                assert msg.type == InterVMMessage.kProcedureCallSQLQueryResp;
                //System.out.println("msg type" + msg.type);
                try {
                    result = (VoltTable[])SerializationHelper.readArray(VoltTable.class, msg.data);
                    buffer = null; // reset buffer as it is held by the result
                } catch (Exception e) {
                    e.printStackTrace();
                }
                oldMessage = msg;
                break;
            }
        }
        long t2 = System.nanoTime();

        // logging
        if((!sqlStatementActualDelayCounter.containsKey(varNamesString) || sqlStatementActualDelayCounter.get(varNamesString) % 3 == 0)) {
            if(!sqlStatementRuntimeTracker.containsKey(varNamesString)) {
                // initialize counter
                sqlStatementRuntimeTracker.put(varNamesString, new ArrayList<>());
                sqlStatementRuntimeTracker.get(varNamesString).add(t2-t);
                sqlStatementRuntimeTrackerTotal.put(varNamesString, t2-t);
                
                // initialize interrupt counter
                sqlStatementInterruptRuntimeTracker.put(varNamesString, new ArrayList<>());
                sqlStatementInterruptRuntimeTracker.get(varNamesString).add(estimatedInterruptionTime);
                sqlStatementInterruptRuntimeTrackerTotal.put(varNamesString, estimatedInterruptionTime);
            } else {
                sqlStatementRuntimeTracker.get(varNamesString).add(t2-t);
                sqlStatementRuntimeTrackerTotal.put(varNamesString, sqlStatementRuntimeTrackerTotal.get(varNamesString) + t2-t);
                sqlStatementInterruptRuntimeTracker.get(varNamesString).add(estimatedInterruptionTime);
                sqlStatementInterruptRuntimeTrackerTotal.put(varNamesString, sqlStatementInterruptRuntimeTrackerTotal.get(varNamesString) + estimatedInterruptionTime);
                
                if(sqlStatementRuntimeTracker.get(varNamesString).size() > 10) {
                    sqlStatementRuntimeTrackerTotal.put(varNamesString, sqlStatementRuntimeTrackerTotal.get(varNamesString) - sqlStatementRuntimeTracker.get(varNamesString).get((int) 0));
                    sqlStatementRuntimeTracker.get(varNamesString).remove((int) 0);
                    sqlStatementInterruptRuntimeTrackerTotal.put(varNamesString, sqlStatementInterruptRuntimeTrackerTotal.get(varNamesString) - sqlStatementInterruptRuntimeTracker.get(varNamesString).get((int) 0));
                    sqlStatementInterruptRuntimeTracker.get(varNamesString).remove((int) 0);
                }
            }
        }

        if(false) {
        // if(printCount++ % 10000 == 0) {
            int count = 0;

            String minKey = "";
            String maxKey = "";
            double minKeyValue = 100000000;
            double maxKeyValue = 0;
            for(String key : sqlStatementRuntimeTracker.keySet()) {
                // only print frequent ones
                if(!sqlStatementRuntimeTracker.containsKey(key)) {
                    continue;
                }

                // calculate sum of squared variance
                long totalRuntime = sqlStatementRuntimeTrackerTotal.get(key);
                long iterationCount = sqlStatementRuntimeTracker.get(key).size();
                double runtimeMean = (double) totalRuntime / iterationCount;
                double std = calculateStd(sqlStatementRuntimeTracker.get(key), runtimeMean);

                if(runtimeMean < minKeyValue) {
                    minKey = key;
                    minKeyValue = runtimeMean;
                }
                if(runtimeMean > maxKeyValue) {
                    maxKey = key;
                    maxKeyValue = runtimeMean;
                }
                
                // compute min and max
                sqlStatementMin.put(key, sqlStatementRuntimeTracker.get(key).get(0));
                sqlStatementMax.put(key, sqlStatementRuntimeTracker.get(key).get(0));
                for(long delta : sqlStatementRuntimeTracker.get(key)) {
                    sqlStatementMin.put(key, Math.min(delta, sqlStatementMin.get(key)));
                    sqlStatementMax.put(key, Math.max(delta, sqlStatementMax.get(key)));
                }
            }

            for(int i = 0; i < 2; i++) {
                String key = i == 0 ? minKey : maxKey;
                
                if(key == "") {
                    continue;
                }

                long totalRuntime = sqlStatementRuntimeTrackerTotal.get(key);
                long totalInterruptRuntime = sqlStatementInterruptRuntimeTrackerTotal.get(key);

                long iterationCount = sqlStatementRuntimeTracker.get(key).size();
                double mean = (double) totalRuntime / iterationCount;
                double interruptMean = (double) totalInterruptRuntime / iterationCount;
                double std = calculateStd(sqlStatementRuntimeTracker.get(key), mean);
                double interruptStd = calculateStd(sqlStatementInterruptRuntimeTracker.get(key), interruptMean);

                if(i == 0) {
                    System.out.println("MIN SO FAR (n=" + iterationCount + ")");
                } else {
                    System.out.println("MAX SO FAR (n=" + iterationCount + ")");
                }

                System.out.println(key + "=" + iterationCount + " TOOK " + (mean / 1000.0) + " us (range:" + (sqlStatementMin.get(key) / 1000.0) + " - " + (sqlStatementMax.get(key) / 1000.0) + ", std: " + (std / 1000.0) + ") to execute. avg interrupt time: " + ((double) totalInterruptRuntime / 1000.0) + " (std: " + (interruptStd / 1000.0) + ")");
            }

            System.out.println();
        }

        return result;
    }
};

/**
 * VoltDB provides main() for the VoltDB server
 */
public class VoltDBProcedureProcess {
    static InMemoryJarfile currentJar;
    static org.nustaq.serialization.FSTConfiguration fstConf;
    static {
        fstConf = org.nustaq.serialization.FSTConfiguration.createUnsafeBinaryConfiguration();
        fstConf.registerClass(ArrayList.class);
        fstConf.registerClass(VMProcedureCall.class);
        fstConf.registerClass(VMInformation.class);
        fstConf.registerClass(org.voltdb.types.TimestampType.class);
        fstConf.setShareReferences(false);
    } 
    private static final VoltLogger logger = new VoltLogger("VoltDBProcedureProcess");
    static class ProcedureContext {
        public VoltVMProcedure procedure;
        public Method runMethod;
        public ProcedureRunnerProxy runner;
    }
    static Map<String, ProcedureContext> procedures = new HashMap<>();
    static int coreIdBound = 0;
    static int hypervisorFd = 0;
    static int VMPid = 0;
    static ArrayDeque<VMProcedureCall> queuedCalls = new ArrayDeque<>();
    static ProcedureContext getProcedureContext(Class<?> procedureClass, InterVMMessagingProtocol protocol, ArrayDeque<VMProcedureCall> queuedCalls) throws InstantiationException , IllegalAccessException {
        VoltVMProcedure procedure = (VoltVMProcedure)procedureClass.newInstance();
        Method runMethod = null;
        for (final Method m : procedure.getClass().getDeclaredMethods()) {
            String name = m.getName();
            if (name.equals("run")) {
                if (java.lang.reflect.Modifier.isPublic(m.getModifiers()) == false) {
                    continue;
                }
                runMethod = m;
                break;
            }
        }

        if (runMethod == null) {
            throw new RuntimeException("No \"run\" method found in: " + procedure.getClass().getCanonicalName());
        }
        ProcedureContext context = new ProcedureContext();
        context.procedure = procedure;
        context.runMethod = runMethod;
        context.runner = new ProcedureRunnerProxy(procedure, protocol, fstConf, queuedCalls);
        context.procedure.init(context.runner);
        return context;
    }

    public static int getVMPid() {return VMPid;}
    public static int getCoreIdBound() {return coreIdBound;}

    public static void exchangeVMInfo(InterVMMessagingProtocol protocol, int hypervisorFd, int vmPid, int coreId) {
        protocol.sendVMInformation(fstConf.asByteArray(new VMInformation(vmPid, coreId)));
        InterVMMessage msg = protocol.getNextMessage(null, null);
        assert msg.type == InterVMMessage.kVMInfoUpdateReq;
        VMInformation i = (VMInformation)fstConf.asObject(msg.data.array());
        protocol.getChannel().hypervisorPVSupport = true;
        protocol.getChannel().hypervisor_fd = hypervisorFd;
        protocol.getChannel().this_core_id = coreId;
        protocol.getChannel().dual_qemu_pid = i.VMPid;
        protocol.getChannel().dual_qemu_lapic_id = i.VMCoreId;
        System.out.printf("This core %d received dual_qemu_pid %d, lapic id %d\n", coreId, i.VMPid, i.VMCoreId);
    }

    public static void processOneProcedureCall(InterVMMessagingProtocol protocol) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException{
        if (queuedCalls.isEmpty())
            return;
        VMProcedureCall call = queuedCalls.poll();
        String procedureClassName = call.procedureName;
        Object[] paramList = call.paramList;
        ProcedureContext context = (ProcedureContext) procedures.get(procedureClassName);
        if (context == null) {
            context = getProcedureContext(CatalogContext.classForProcedureOrUDF(procedureClassName,
            currentJar.getLoader()), protocol, queuedCalls);
            procedures.put(procedureClassName, context);
        }
        Object ret = null;
        try {
            ret = context.runMethod.invoke(context.procedure, paramList);
        } catch (Exception e) {
            // System.out.println("THIS IS AN EXCEPTION FROM THE PROCESS, AND IT SHOULD BE RETHROWN!");
            // System.out.println(e.getCause().toString()); // exception and message
            // System.out.println(e.getCause().getMessage()); // just message
            // System.out.println(e.getCause().getClass().getCanonicalName()); // package, just dots
            // System.out.println(e.getCause().getClass().getSimpleName()); // just the class

            protocol.writeProcedureCallResponseReturnErrorMessage(fstConf.asByteArray(e.getCause().getClass().getCanonicalName()), queuedCalls.isEmpty());
            return;
        }
        boolean notify = queuedCalls.isEmpty();
        if (ret == null) {
            protocol.writeProcedureCallResponseReturnVoidMessage(notify);    
        } else if (ret instanceof VoltTable[]) {
            protocol.writeProcedureCallResponseReturnVoltTablesMessage((VoltTable[])ret, notify);
        } else if (ret instanceof VoltTable){
            protocol.writeProcedureCallResponseReturnVoltTableMessage((VoltTable)ret, notify);
        } else {
            protocol.writeProcedureCallResponseReturnObjectMessage(fstConf.asByteArray(ret), notify);
        }
    }
    static ByteBuffer buffer = null;
    static InterVMMessage oldMessage = null;
    
    static void processMessage(InterVMMessage msg, InterVMMessagingProtocol protocol, int vmId) {
        try {
            assert (msg != null);
            if (msg.type == InterVMMessage.kUpdateCatalogReq) {
                int len = msg.data.remaining();
                byte[] jarFileBytes = new byte[len];
                msg.data.get(jarFileBytes);
                currentJar = new InMemoryJarfile(jarFileBytes);
                assert currentJar.getLoader() != null;
                procedures.clear();
                System.out.printf("VM %d received update to catalog, jar size %d\n", vmId, jarFileBytes.length);
                protocol.writeCatalogUpdateResponseMessage();
            } else if (msg.type == InterVMMessage.kProcedureCallReq) {
                VMProcedureCall call = null;
                try {
                    org.nustaq.serialization.FSTObjectInput objectsInput = fstConf.getObjectInput(msg.data.array(), msg.data.limit());
                    call = (VMProcedureCall)objectsInput.readObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                queuedCalls.offer(call);
            } else if (msg.type == InterVMMessage.kPingPongReq) {
                protocol.pong(msg.data.array());
            }

            if (msg.data != null) { 
                if (buffer == null || msg.data.capacity() > buffer.capacity()) {
                    buffer = msg.data;
                }
            }
            oldMessage = msg;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void run(int vmId, InterVMMessagingProtocol protocol) {
        org.voltdb.NativeLibraryLoader.loadVoltDB();
        System.out.printf("VM %d pid %d pv_accel=%b started to sync with VoltDB\n", vmId, VMPid, protocol.PVAccelerationenabled());
        if (protocol.PVAccelerationenabled()) {
            coreIdBound = 0;
            int res = ExecutionEngine.DBOSBindCurrentThreadToCore(coreIdBound);
            assert res == 0;
            int coreId = ExecutionEngine.DBOSGetCPUId();
            assert coreId == coreIdBound;
            coreIdBound = coreId;
            final String DBOS_PV_DEV_PATH = "/dev/etx_device";
            hypervisorFd = ExecutionEngine.DBOSPVOpen(DBOS_PV_DEV_PATH.getBytes());
            VMPid = ExecutionEngine.DBOSPVGetVMId(hypervisorFd);
            exchangeVMInfo(protocol, hypervisorFd, VMPid, coreIdBound);
        }
        protocol.pongpingTest();
        System.out.printf("VM %d synced with VoltDB\n", vmId);
        byte[] procedureNameBuf = null;
        long queueLengthSum = 0;
        long queueLengthCnt = 0;
        long lastRecordingTime = System.nanoTime();
        long lastPrintTime = System.nanoTime();
        while (true) {
            while (protocol.hasMessage()) {
                InterVMMessage msg = null;
                try {
                    msg = protocol.getNextMessage(oldMessage, null);
                    assert (msg != null);
                    processMessage(msg, protocol, vmId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // if (System.nanoTime() >= lastRecordingTime + 10000) {
            //     queueLengthSum += queuedCalls.size();
            //     queueLengthCnt++;
            //     lastRecordingTime =  System.nanoTime();
            //     if (lastRecordingTime >= lastPrintTime + 5000000000l) {
            //         System.out.printf("average queue length %f\n", ((double)queueLengthSum / queueLengthCnt));
            //         queueLengthCnt = queueLengthSum = 0;
            //         lastPrintTime = System.nanoTime();
            //     }
            // }
            try{
                while(queuedCalls.isEmpty() == false) { // while there are queuedCalls
                    if (System.nanoTime() >= lastRecordingTime + 10000) {
                        queueLengthSum += queuedCalls.size();
                        queueLengthCnt++;
                        lastRecordingTime =  System.nanoTime();
                        if (lastRecordingTime >= lastPrintTime + 5000000000l) {
                            System.out.printf("average queue length %f\n", ((double)queueLengthSum / queueLengthCnt));
                            queueLengthCnt = queueLengthSum = 0;
                            lastPrintTime = System.nanoTime();
                        }
                    }
                    processOneProcedureCall(protocol);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (protocol.hasMessage() == false) {
                InterVMMessage msg = null;
                try {
                    msg = protocol.getNextMessage(oldMessage, null);
                    assert (msg != null);
                    processMessage(msg, protocol, vmId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}