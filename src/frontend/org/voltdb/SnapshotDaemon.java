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

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper_voltpatches.CreateMode;
import org.apache.zookeeper_voltpatches.KeeperException;
import org.apache.zookeeper_voltpatches.KeeperException.NodeExistsException;
import org.apache.zookeeper_voltpatches.WatchedEvent;
import org.apache.zookeeper_voltpatches.Watcher;
import org.apache.zookeeper_voltpatches.Watcher.Event.EventType;
import org.apache.zookeeper_voltpatches.Watcher.Event.KeeperState;
import org.apache.zookeeper_voltpatches.ZooDefs.Ids;
import org.apache.zookeeper_voltpatches.ZooKeeper;
import org.apache.zookeeper_voltpatches.data.Stat;
import org.json_voltpatches.JSONArray;
import org.json_voltpatches.JSONException;
import org.json_voltpatches.JSONObject;

import org.voltcore.logging.Level;
import org.voltcore.logging.VoltLogger;
import org.voltcore.messaging.HostMessenger;
import org.voltcore.messaging.Mailbox;
import org.voltcore.messaging.SiteMailbox;
import org.voltcore.messaging.VoltMessage;
import org.voltcore.network.Connection;
import org.voltcore.utils.CoreUtils;
import org.voltcore.utils.Pair;
import org.voltcore.zk.ZKUtil;
import org.voltdb.catalog.SnapshotSchedule;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.ProcedureCallback;
import org.voltdb.iv2.TxnEgo;
import org.voltdb.messaging.SnapshotCheckRequestMessage;
import org.voltdb.messaging.SnapshotCheckResponseMessage;
import org.voltdb.sysprocs.saverestore.SnapshotPathType;
import org.voltdb.sysprocs.saverestore.SnapshotUtil;
import org.voltdb.utils.TimeUtils;
import org.voltdb.utils.VoltTableUtil;

import com.google_voltpatches.common.base.Throwables;
import com.google_voltpatches.common.collect.Maps;
import com.google_voltpatches.common.util.concurrent.Callables;
import com.google_voltpatches.common.util.concurrent.ListenableFuture;
import com.google_voltpatches.common.util.concurrent.ListeningScheduledExecutorService;
import com.google_voltpatches.common.util.concurrent.MoreExecutors;

/**
 * A scheduler of automated snapshots and manager of archived and retained snapshots.
 * The new functionality for handling truncation snapshots operates separately from
 * the old automated snapshots. They just share the same event processing threads. Future work
 * should merge them.
 *
 * Note: comments that start with TRAIL [TruncSnap] TruncationSnapshot legend
 *       are way to document code trails which may be perused via the code trails
 *       eclipse plugin https://marketplace.eclipse.org/content/code-trails
 */
public class SnapshotDaemon implements SnapshotCompletionInterest {
    private static final VoltLogger SNAP_LOG = new VoltLogger("SNAPSHOT");
    private static final VoltLogger loggingLog = new VoltLogger("LOGGING");

    // Periodic work (method doPeriodicWork) schedule interval.
    // Milliseconds. Modified by unit tests
    static int m_periodicWorkInterval = 2000;

    // Retry delay (in seconds) for user snapshot requests issued
    // when there is a snapshot in progress. Public because a unit
    // test (TestSaveRestoreSysProcSuite) modifies it.
    public static volatile int m_userSnapshotRetryInterval =
            Integer.getInteger("USER_SNAPSHOT_RETRY_INTERVAL", 30);

    // Interface, implemented in ClientInterface, which the snapshot
    // daemon calls to initiate system procedures
    public interface DaemonInitiator {
        public void initiateSnapshotDaemonWork(final String procedureName, long clientData, Object params[]);
    }

    // Tracks when the last @SnapshotSave call was issued.
    // Prevents two @SnapshotSave calls being issued back to back.
    // This is reset when a response is received for the initiation.
    private static final long INITIATION_RESPONSE_TIMEOUT_MS = 20 * 60 * 1000;
    private Pair<Long, Boolean> m_lastInitiationTs;

    // Single-thread executor for snapshot tasks
    private final ScheduledThreadPoolExecutor m_esBase =
            new ScheduledThreadPoolExecutor(1,
                                            CoreUtils.getThreadFactory(null, "SnapshotDaemon", CoreUtils.SMALL_STACK_SIZE, false, null),
                                            new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    private final ListeningScheduledExecutorService m_es = MoreExecutors.listeningDecorator(m_esBase);

    // Used to generate handles for async procedure callbacks
    private long m_nextCallbackHandle;

    // Configuration and similar data for truncation snapshots
    private ZooKeeper m_zk;
    private Mailbox m_mb;
    private DaemonInitiator m_initiator;
    private String m_truncationSnapshotPath;

    // Structure used to track truncation snapshot attempts
    private static class TruncationSnapshotAttempt {
        private String path;
        private String pathType;
        private String nonce;
        private boolean finished;
    }

    // Operational data for truncation snapshots
    private final TreeMap<Long, TruncationSnapshotAttempt> m_truncationSnapshotAttempts = new TreeMap<>();
    private Future<?> m_truncationSnapshotScanTask;

    // Configuration data for automatic snapshot operation
    private boolean m_autoEnabled;
    private long m_frequencyInMillis;
    private int m_retain;
    private String m_path;
    private String m_autoPrefix;

    // Used with prefix to generate unique nonce for an auto snapshot
    private final SimpleDateFormat m_dateFormat = new SimpleDateFormat("'_'yyyy.MM.dd.HH.mm.ss");

    // Operational data for automatic snapshots
    private SnapshotSchedule m_lastKnownSchedule;
    private final HashMap<Long, ProcedureCallback> m_procedureCallbacks = new HashMap<>();
    private boolean m_isAutoSnapshotLeader;
    private Future<?> m_autoSnapshotTask;
    private long m_nextSnapshotTime;

    // Data for grooming terminus snapshots
    private String m_terminusPrefix = "SHUTDOWN";
    private boolean m_groomTermini;
    private int m_terminusRetention;
    private final LinkedList<Snapshot> m_terminusSnapshots = new LinkedList<>();

    // Don't invoke sysprocs too close together; require a minimum
    // time between invocations. Used by unit tests.
    static long m_minTimeBetweenSysprocs = 3000;
    private long m_lastSysprocInvocation = System.currentTimeMillis();

    // List of snapshots on disk sorted by creation time
    private final LinkedList<Snapshot> m_autoSnapshots = new LinkedList<>();

    // States the daemon can be in (also used by unit tests)
    enum State {
        STARTUP, // initial state
        SCANNING, // @SnapshotScan executing
        WAITING, // paused in between snapshots
        DELETING, // deleting snapshots that are no longer going to be retained.
        SNAPSHOTTING, // @SnapshotSave executing
        STOPPED // task is not running
    }
    private State m_state = State.STOPPED;

    // Identification of the single thread on which most work is executed
    private long m_snapshotThreadId;

    /**
     * SnapshotDaemon constructor
     */
    SnapshotDaemon(CatalogContext catalogContext) {
        m_esBase.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        m_esBase.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        m_esBase.execute(new Runnable() {
            @Override
            public void run() {
                m_snapshotThreadId = Thread.currentThread().getId();
            }
        });

        // Register the snapshot status to the StatsAgent
        SnapshotStatus snapshotStatus = new SnapshotStatus(VoltDB.instance().getCommandLogSnapshotPath(),
                VoltDB.instance().getSnapshotPath());
        VoltDB.instance().getStatsAgent().registerStatsSource(StatsSelector.SNAPSHOTSTATUS,
                                                              0,
                                                              snapshotStatus);
        // Snapshot status summary is a one-row-per-snapshot stats
        VoltDB.instance().getStatsAgent().registerStatsSource(StatsSelector.SNAPSHOTSUMMARY,
                                                              0,
                                                              new SnapshotSummary(
                                                                      VoltDB.instance().getCommandLogSnapshotPath(),
                                                                      VoltDB.instance().getSnapshotPath()));
        VoltDB.instance().getSnapshotCompletionMonitor().addInterest(this);
    }

    /**
     * Once-only initialization
     */
    public void init(DaemonInitiator initiator, HostMessenger messenger, Runnable threadLocalInit, GlobalServiceElector gse) {
        m_initiator = initiator;
        m_zk = messenger.getZK();
        m_mb = new SiteMailbox(messenger, messenger.getHSIdForLocalSite(HostMessenger.SNAPSHOT_DAEMON_ID));
        messenger.createMailbox(m_mb.getHSId(), m_mb);

        try {
            m_zk.create(VoltZK.nodes_currently_snapshotting, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (Exception e) {}
        try {
            m_zk.create(VoltZK.completed_snapshots, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (Exception e) {}

        if (threadLocalInit != null) {
            m_es.execute(threadLocalInit);
        }

        /*
         *  Really shouldn't leak this from a constructor, and twice to boot
         *  If IV2 is enabled leader election for the snapshot daemon is always tied to
         *  leader election for the MP coordinator so that they can't be partitioned
         *  from each other.
         */
        if (gse == null) {
            m_es.execute(new Runnable() {
                @Override
                public void run() {
                    leaderElection();
                }
            });
        } else {
            gse.registerService(new Promotable() {
                @Override
                public void acceptPromotion() throws InterruptedException,
                        ExecutionException, KeeperException {
                    m_es.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                m_isAutoSnapshotLeader = true;
                                if (m_lastKnownSchedule != null) {
                                    makeActivePrivate(m_lastKnownSchedule);
                                }
                                electedTruncationLeader();
                            } catch (Exception e) {
                                VoltDB.crashLocalVoltDB("Exception in snapshot daemon electing master via ZK", true, e);
                            }
                        }
                    });
                }

            });
        }
    }

    /**
     * General routine for initiating an execution of @SnapshotSave.
     * Used for all cases: user-initiated snapshots, automatic snapshots,
     * and truncation snapshots.
     */
    private void initiateSnapshotSave(final long handle, final Object[] params, boolean blocking){
        boolean success = true;
        VoltTable checkResult = SnapshotUtil.constructNodeResultsTable();
        final String jsString = String.class.cast(params[0]);

        // Check on any in-progress snapshot.
        if (m_lastInitiationTs != null) {
            final long elapsedMs = System.currentTimeMillis() - m_lastInitiationTs.getFirst();
            // Blocking snapshot may take a long time to finish, don't time it out if it's blocking
            if (!m_lastInitiationTs.getSecond() && elapsedMs > INITIATION_RESPONSE_TIMEOUT_MS) {
                SNAP_LOG.warn(String.format("A snapshot was initiated %d minutes ago and hasn't received a response yet.",
                        TimeUnit.MILLISECONDS.toMinutes(elapsedMs)));
                m_lastInitiationTs = null;
            } else {
                checkResult.addRow(CoreUtils.getHostIdFromHSId(m_mb.getHSId()), CoreUtils.getHostnameOrAddress(), null,
                        "FAILURE", "SNAPSHOT IN PROGRESS");
                success = false;
            }
        }

        if (success) {
            try {
                final JSONObject jsObj = new JSONObject(jsString);
                boolean initiateSnapshot;

                // Do scan work on all known live hosts
                VoltMessage msg = new SnapshotCheckRequestMessage(jsString);
                SnapshotPathType pathType = SnapshotPathType.valueOf(jsObj.getString(SnapshotUtil.JSON_PATH_TYPE));
                Set<Integer> liveHosts = VoltDB.instance().getHostMessenger().getLiveHostIds();
                for (int hostId : liveHosts) {
                    m_mb.send(CoreUtils.getHSIdFromHostAndSite(hostId, HostMessenger.SNAPSHOT_IO_AGENT_ID), msg);
                }

                // Wait for responses from all hosts for a certain amount of time
                Map<Integer, VoltTable> responses = Maps.newHashMap();
                final long timeoutMs = 10 * 1000; // 10s timeout
                final long endTime = System.currentTimeMillis() + timeoutMs;
                SnapshotCheckResponseMessage response;
                while ((response = (SnapshotCheckResponseMessage) m_mb.recvBlocking(timeoutMs)) != null) {
                    final String nonce = jsObj.getString(SnapshotUtil.JSON_NONCE);
                    boolean nonceFound = false;
                    if (pathType == SnapshotPathType.SNAP_PATH) {
                        // If request was explicitely PATH check path too.
                        if (nonce.equals(response.getNonce()) && response.getPath().equals(jsObj.getString(SnapshotUtil.JSON_PATH))) {
                            nonceFound = true;
                        }
                    } else {
                        // If request is with type other than path just check type.
                        if (nonce.equals(response.getNonce()) && response.getSnapshotPathType() == pathType) {
                            nonceFound = true;
                        }
                    }
                    if (nonceFound) {
                        responses.put(CoreUtils.getHostIdFromHSId(response.m_sourceHSId), response.getResponse());
                    }

                    if (responses.size() == liveHosts.size() || System.currentTimeMillis() > endTime) {
                        break;
                    }
                }

                if (responses.size() != liveHosts.size()) {
                    checkResult.addRow(CoreUtils.getHostIdFromHSId(m_mb.getHSId()), CoreUtils.getHostnameOrAddress(), null,
                            "FAILURE", "TIMED OUT CHECKING SNAPSHOT FEASIBILITY");
                    success = false;
                }

                if (success) {
                    // TRAIL [TruncSnap:12] all participating nodes have initiated successfully
                    // Call @SnapshotSave if check passed, return the failure otherwise
                    checkResult = VoltTableUtil.unionTables(responses.values());
                    initiateSnapshot = SnapshotUtil.didSnapshotRequestSucceed(new VoltTable[]{checkResult});

                    if (initiateSnapshot) {
                        m_lastInitiationTs = Pair.of(System.currentTimeMillis(), blocking);
                        m_initiator.initiateSnapshotDaemonWork("@SnapshotSave", handle, params);
                    } else {
                        success = false;
                    }
                }
            } catch (JSONException e) {
                success = false;
                checkResult.addRow(CoreUtils.getHostIdFromHSId(m_mb.getHSId()), CoreUtils.getHostnameOrAddress(), null, "FAILURE", "ERROR PARSING JSON");
                SNAP_LOG.warn("Error parsing JSON string: " + jsString, e);
            }
        }

        if (!success) {
            final ClientResponseImpl failureResponse =
                    new ClientResponseImpl(ClientResponseImpl.SUCCESS, new VoltTable[]{checkResult}, null);
            failureResponse.setClientHandle(handle);
            processClientResponse(Callables.returning(failureResponse));
        }
    }

    private void saveResponseToZKAndReset(String requestId, ClientResponseImpl response) throws Exception
    {
        ByteBuffer buf = ByteBuffer.allocate(response.getSerializedSize());
        m_zk.create(VoltZK.user_snapshot_response + requestId,
                response.flattenToBuffer(buf).array(),
                Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT);
        userSnapshotRequestExistenceCheck(true);
    }

    /////////////////////////////////////////////////
    // TRUNCATION SNAPSHOT WORK
    ////////////////////////////////////////////////

    /**
     * Search for truncation snapshots. This executes only on the
     * elected truncation leader. We do this because after a failure,
     * there may be snapshots we don't know about, there may be ones
     * from a previous instance, etc.
     *
     * Repeated hourly as a scheduled task.
     *
     * This work is distinct from the scan that we do to discover
     * all automatic snapshots at daemon startup.
     */
    private void scanTruncationSnapshots() {
        assert Thread.currentThread().getId() == m_snapshotThreadId;

        if (m_truncationSnapshotPath == null) {
            try {
                m_truncationSnapshotPath = new String(m_zk.getData(VoltZK.test_scan_path, false, null), "UTF-8");
            } catch (Exception e) {
                return;
            }
        }

        Object[] params = new Object[1];
        params[0] = m_truncationSnapshotPath;
        long handle = m_nextCallbackHandle++;

        m_procedureCallbacks.put(handle, new ProcedureCallback() {
            @Override
            public void clientCallback(final ClientResponse response)
                    throws Exception {

                final VoltTable snapshots = checkSnapshotScanResponse(response);
                if (snapshots == null) {
                    return;
                }

                TreeMap<Long, TruncationSnapshotAttempt> foundSnapshots = new TreeMap<>();
                while (snapshots.advanceRow()) {
                    final String path = snapshots.getString("PATH");
                    final String pathType = snapshots.getString("PATHTYPE");
                    final String nonce = snapshots.getString("NONCE");
                    final Long txnId = snapshots.getLong("TXNID");
                    TruncationSnapshotAttempt snapshotAttempt = new TruncationSnapshotAttempt();
                    snapshotAttempt.path = path;
                    snapshotAttempt.pathType = pathType;
                    snapshotAttempt.nonce = nonce;
                    foundSnapshots.put(txnId, snapshotAttempt);
                }

                for (Map.Entry<Long, TruncationSnapshotAttempt> entry : foundSnapshots.entrySet()) {
                    if (!m_truncationSnapshotAttempts.containsKey(entry.getKey())) {
                        loggingLog.info("Truncation snapshot scan discovered new snapshot txnid " + entry.getKey() +
                                " path " + entry.getValue().path + " nonce " + entry.getValue().nonce);
                        m_truncationSnapshotAttempts.put(entry.getKey(), entry.getValue());
                    }
                }
            }

        });

        m_initiator.initiateSnapshotDaemonWork("@SnapshotScan", handle, params);
    }

    /**
     * Delete all truncation snapshots older then the last successful
     * truncation snapshot. This only affects snapshots used for log
     * truncation.
     *
     * The map uses transaction ids as a key, and therefore iteration
     * in descending key order produces the most recent snapshot first.
     */
    private void groomTruncationSnapshots() {
        assert Thread.currentThread().getId() == m_snapshotThreadId;

        ArrayList<TruncationSnapshotAttempt> toDelete = new ArrayList<>();
        boolean foundMostRecentSuccess = false;
        Iterator<Map.Entry<Long, TruncationSnapshotAttempt>> iter =
            m_truncationSnapshotAttempts.descendingMap().entrySet().iterator();
        loggingLog.info("Snapshot daemon grooming truncation snapshots");
        while (iter.hasNext()) {
            Map.Entry<Long, TruncationSnapshotAttempt> entry = iter.next();
            TruncationSnapshotAttempt attempt = entry.getValue();
            String descr = String.format("txnid %s, path %s, nonce %s", TxnEgo.txnIdToString(entry.getKey()),
                                         attempt.path, attempt.nonce);
            if (!foundMostRecentSuccess) {
                if (attempt.finished) {
                    loggingLog.info("Found most recent successful snapshot, " + descr);
                    foundMostRecentSuccess = true;
                } else {
                    loggingLog.info("Retaining possible partial snapshot, " + descr);
                }
            } else {
                loggingLog.info("Deleting old unnecessary snapshot txnid, " + descr);
                toDelete.add(attempt);
                iter.remove();
            }
        }

        String[] paths = new String[toDelete.size()];
        String[] nonces = new String[toDelete.size()];

        int ii = 0;
        for (TruncationSnapshotAttempt attempt : toDelete) {
            paths[ii] = SnapshotUtil.getRealPath(SnapshotPathType.valueOf(attempt.pathType), attempt.path);
            nonces[ii++] = attempt.nonce;
        }

        Object[] params = new Object[] { paths,
                                         nonces,
                                         SnapshotPathType.SNAP_CL.toString() };
        long handle = m_nextCallbackHandle++;
        m_procedureCallbacks.put(handle, new ProcedureCallback() {
            @Override
            public void clientCallback(ClientResponse clientResponse)
                    throws Exception {
                if (clientResponse.getStatus() != ClientResponse.SUCCESS) {
                    SNAP_LOG.error(clientResponse.getStatusString());
                }
            }
        });

        m_initiator.initiateSnapshotDaemonWork("@SnapshotDelete", handle, params);
    }

    /**
     * If this cluster has per-partition transactions ids carried over from
     * previous instances, retrieve them from ZK and pass them to snapshot save
     * so that it can  include them in the snapshot
     */
    private JSONArray retrievePerPartitionTransactionIds() {
        JSONArray retval = new JSONArray();
        try {
            ByteBuffer values = ByteBuffer.wrap(m_zk.getData(VoltZK.perPartitionTxnIds, false, null));
            int numKeys = values.getInt();
            for (int ii = 0; ii < numKeys; ii++) {
                retval.put(values.getLong());
            }
        } catch (KeeperException.NoNodeException e) {
            // doesn't have to exist
        } catch (Exception e) {
            VoltDB.crashLocalVoltDB("Failed to retrieve per-partition transaction ids for snapshot", false, e);
        }
        return retval;
    }

    /**
     * Leader election for snapshots.
     * Leader will watch for truncation and user snapshot requests.
     */
    private void leaderElection() {
        loggingLog.info("Starting leader election for snapshot truncation daemon");
        try {
            while (true) {
                Stat stat = m_zk.exists(VoltZK.snapshot_truncation_master, new Watcher() {
                    @Override
                    public void process(WatchedEvent event) {
                        switch(event.getType()) {
                        case NodeDeleted:
                            loggingLog.info("Detected the snapshot truncation leader's ephemeral node deletion");
                            m_es.execute(new Runnable() {
                                @Override
                                public void run() {
                                    leaderElection();
                                }
                            });
                            break;
                        default:
                            break;
                        }
                    }
                });
                if (stat == null) {
                    try {
                        m_zk.create(VoltZK.snapshot_truncation_master, null, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                        m_isAutoSnapshotLeader = true;
                        if (m_lastKnownSchedule != null) {
                            makeActivePrivate(m_lastKnownSchedule);
                        }
                        electedTruncationLeader();
                        return;
                    } catch (NodeExistsException e) {
                        // ok, race
                    }
                } else {
                    loggingLog.info("Leader election concluded, a leader already exists");
                    break;
                }
            }
        } catch (Exception e) {
            VoltDB.crashLocalVoltDB("Exception in snapshot daemon electing master via ZK", true, e);
        }
    }

    /*
     * Invoked when this snapshot daemon has been elected as leader.
     * Schedule a periodic scan.
     */
    private void electedTruncationLeader() throws Exception {
        loggingLog.info("This node was selected as the leader for snapshot truncation");
        m_truncationSnapshotScanTask = m_es.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    scanTruncationSnapshots();
                } catch (Exception e) {
                    loggingLog.error("Error during scan and group of truncation snapshots");
                }
            }
        }, 0, 1, TimeUnit.HOURS);
        try {
            // TRAIL [TruncSnap:1] elected as leader
            truncationRequestExistenceCheck();
            userSnapshotRequestExistenceCheck(false);
        } catch (Exception e) {
            VoltDB.crashLocalVoltDB("Error while accepting snapshot daemon leadership", true, e);
        }
    }

    /*
     * Process the event generated when the node for a truncation request
     * is created, reschedules it for a few seconds later
     */
    private void processTruncationRequestEvent(final WatchedEvent event) {
        if (event.getType() == EventType.NodeChildrenChanged) {
            /*
             * TRAIL [TruncSnap:5] Process truncation request
             */
            m_es.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        processSnapshotTruncationRequestCreated(event);
                    } catch (Throwable e) {
                        VoltDB.crashLocalVoltDB("Error processing snapshot truncation request creation", true, e);
                    }
                }
            });
            return;
        } else {
            /*
             * We are very careful to cancel the watch if we find that a truncation requests exists. We are
             * the only thread and daemon that should delete the node or change the data and the watch
             * isn't set when that happens because it is part of processing the request and the watch should
             * either be canceled or have already fired.
             */
            VoltDB.crashLocalVoltDB(
                    "Trunction request watcher fired with event type other than created: " + event.getType(),
                    true,
                    null);
        }
    }

    /*
     * A ZK event occured requesting a truncation snapshot be taken
     */
    private void processSnapshotTruncationRequestCreated(final WatchedEvent event) {
        loggingLog.info("Snapshot truncation leader received snapshot truncation request");
        // Get the truncation request ID which is the truncation request node path.
        final String truncReqId;
        try {
            List<String> children = m_zk.getChildren(event.getPath(), false);
            if (children.isEmpty()) {
                loggingLog.error("Unable to retrieve truncation snapshot request id from ZK, log can't be truncated");
                return;
            }
            truncReqId = ZKUtil.joinZKPath(event.getPath(), Collections.max(children));

        } catch (Exception e) {
            loggingLog.error("Unable to retrieve truncation snapshot request ID from ZK, log can't be truncated");
            return;
        }
        final long now = System.currentTimeMillis();
        final String nonce = Long.toString(now);
        //Allow nodes to check and see if the nonce incoming for a snapshot is
        //for a truncation snapshot. In that case they will mark the completion node
        //to be for a truncation snapshot. SnapshotCompletionMonitor notices the mark.
        // TRAIL [TruncSnap:7] write current ts to request zk node data
        try {
            ByteBuffer payload = ByteBuffer.allocate(8);
            payload.putLong(0, now);
            m_zk.setData(VoltZK.request_truncation_snapshot, payload.array(), -1);
        } catch (Exception e) {
            //Cause a cascading failure?
            VoltDB.crashLocalVoltDB("Setting data on the truncation snapshot request in ZK should never fail", true, e);
        }
        // for the snapshot save invocations
        JSONObject jsObj = new JSONObject();
        try {
            assert truncReqId != null;
            String sData = "";
            JSONObject jsData = new JSONObject();
            jsData.put(SnapshotUtil.JSON_TRUNCATION_REQUEST_ID, truncReqId);
            sData = jsData.toString();
            jsObj.put(SnapshotUtil.JSON_PATH, VoltDB.instance().getCommandLogSnapshotPath() );
            jsObj.put(SnapshotUtil.JSON_NONCE, nonce);
            jsObj.put(SnapshotUtil.JSON_PATH_TYPE, SnapshotPathType.SNAP_CL);
            jsObj.put("perPartitionTxnIds", retrievePerPartitionTransactionIds());
            jsObj.put("data", sData);
        } catch (JSONException e) {
            /*
             * Should never happen, so fail fast
             */
            VoltDB.crashLocalVoltDB("", true, e);
        }

        // for the snapshot save invocations
        assert Thread.currentThread().getId() == m_snapshotThreadId;
        long handle = m_nextCallbackHandle++;

        // for the snapshot save invocation
        m_procedureCallbacks.put(handle, new ProcedureCallback() {

            @Override
            public void clientCallback(ClientResponse clientResponse)
                    throws Exception {
                m_lastInitiationTs = null;
                if (clientResponse.getStatus() != ClientResponse.SUCCESS){
                    loggingLog.warn(
                            "Attempt to initiate a truncation snapshot was not successful: " +
                            clientResponse.getStatusString());
                    loggingLog.warn("Retrying log truncation snapshot in 5 minutes");
                    /*
                     * TRAIL [TruncSnap:8] (callback) on failed response try again in a few minute
                     */
                    m_es.schedule(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                processTruncationRequestEvent(event);
                            } catch (Exception e) {
                                VoltDB.crashLocalVoltDB("Error processing snapshot truncation request event", true, e);
                            }
                        }
                    }, 5, TimeUnit.MINUTES);
                    return;
                }

                final VoltTable results[] = clientResponse.getResults();
                final VoltTable result = results[0];
                boolean success = true;

                final String err = SnapshotUtil.didSnapshotRequestFailWithErr(results);
                if (err != null) {
                    if (err.trim().equalsIgnoreCase("SNAPSHOT IN PROGRESS")) {
                        loggingLog.info("Snapshot is in progress");
                    } else {
                        loggingLog.warn("Snapshot failed with failure response: " + err);
                    }
                    success = false;
                }

                //assert(result.getColumnName(1).equals("TABLE"));
                if (success) {
                    while (result.advanceRow()) {
                        if (!result.getString("RESULT").equals("SUCCESS")) {
                            success = false;
                            loggingLog.warn("Snapshot save feasibility test failed for host "
                                    + result.getLong("HOST_ID") + " table " + result.getString("TABLE") +
                                    " with error message " + result.getString("ERR_MSG"));
                        }
                    }
                }

                if (success) {
                    loggingLog.info("Snapshot initiation for log truncation was successful");

                    JSONObject obj = new JSONObject(clientResponse.getAppStatusString());
                    final long snapshotTxnId = Long.valueOf(obj.getLong("txnId"));
                    try {
                        boolean found = false;
                        ZKUtil.VoidCallback lastCallback = null;

                        for (String child: m_zk.getChildren(event.getPath(),false)) {
                            String requestId = ZKUtil.joinZKPath(event.getPath(), child);
                            found = found || requestId.equals(truncReqId);

                            lastCallback = new ZKUtil.VoidCallback();
                            m_zk.delete(requestId, -1, lastCallback, null);
                        }

                        if (lastCallback != null) {
                            try {
                                lastCallback.get();
                            } catch (KeeperException.NoNodeException ignoreIt) {
                            }
                        }
                        if (!found) {
                            VoltDB.crashLocalVoltDB(
                                    "Could not match truncations snapshot request id while atepting its removal", true, null);
                        }
                    } catch (Exception e) {
                        VoltDB.crashLocalVoltDB(
                                "Unexpected error deleting truncation snapshot request", true, e);
                    }

                    try {
                        TruncationSnapshotAttempt snapshotAttempt =
                            m_truncationSnapshotAttempts.get(snapshotTxnId);
                        if (snapshotAttempt == null) {
                            snapshotAttempt = new TruncationSnapshotAttempt();
                            m_truncationSnapshotAttempts.put(snapshotTxnId, snapshotAttempt);
                            snapshotAttempt.pathType = SnapshotPathType.SNAP_CL.toString();
                        }
                        snapshotAttempt.nonce = nonce;
                        snapshotAttempt.path = VoltDB.instance().getCommandLogSnapshotPath();
                    } finally {
                        // TRAIL [TruncSnap:9] (callback) restart the whole request check cycle
                        try {
                            truncationRequestExistenceCheck();
                        } catch (Exception e) {
                            VoltDB.crashLocalVoltDB(
                                    "Unexpected error checking for existence of truncation snapshot request"
                                    , true, e);
                        }
                    }
                } else {
                    loggingLog.info("Retrying log truncation snapshot in 60 seconds");
                    /*
                     * TRAIL [TruncSnap:10] (callback) on table reported failure try again in a few minutes
                     */
                    m_es.schedule(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                processTruncationRequestEvent(event);
                            } catch (Exception e) {
                                VoltDB.crashLocalVoltDB("Exception processing truncation request event", true, e);
                            }
                        }
                    }, 1, TimeUnit.MINUTES);
                }
            }

        });
        try {
            loggingLog.info("Initiating @SnapshotSave for log truncation");
            initiateSnapshotSave(handle, new Object[]{jsObj.toString(4)}, false);
        } catch (JSONException e) {
            /*
             * Should never happen, so fail fast
             */
            VoltDB.crashLocalVoltDB("", true, e);
        }
        return;
    }

    private TruncationRequestExistenceWatcher m_currentTruncationWatcher = new TruncationRequestExistenceWatcher();

    /*
     * Watcher that handles changes to the ZK node for
     * internal truncation snapshot requests
     */
    private class TruncationRequestExistenceWatcher extends ZKUtil.CancellableWatcher {

        public TruncationRequestExistenceWatcher() {
            super(m_es);
        }

        @Override
        public void pProcess(final WatchedEvent event) {
            if (event.getState() == KeeperState.Disconnected) return;
            try {
                // TRAIL [TruncSnap:4] watch event on zk node fires
                processTruncationRequestEvent(event);
            } catch (Exception e) {
                VoltDB.crashLocalVoltDB("Error procesing truncation request event", true, e);
            }
        }
    };

    /////////////////////////////////////////////////
    // USER SNAPSHOT REQUESTS
    ////////////////////////////////////////////////

    /*
     * Watcher that handles events to the user snapshot request node
     * in ZK
     */
    private final Watcher m_userSnapshotRequestExistenceWatcher = new Watcher() {

        @Override
        public void process(final WatchedEvent event) {
            if (event.getState() == KeeperState.Disconnected) return;

            m_es.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        processUserSnapshotRequestEvent(event);
                    } catch (Exception e) {
                        VoltDB.crashLocalVoltDB("Error processing user snapshot request event", true, e);
                    }
                }
            });
        }
    };

    /*
     * Process the event generated when the node for a user snapshot request
     * is created.
     */
    private void processUserSnapshotRequestEvent(final WatchedEvent event) throws Exception {
        if (event.getType() == EventType.NodeCreated) {
            byte data[] = m_zk.getData(event.getPath(), false, null);
            String jsonString = new String(data, "UTF-8");
            final JSONObject jsObj = new JSONObject(jsonString);
            final String requestId = jsObj.getString("requestId");
            final boolean blocking = jsObj.getBoolean(SnapshotUtil.JSON_BLOCK);
            /*
             * Going to reuse the request object, remove the requestId
             * field now that it is consumed
             */
            jsObj.remove("requestId");
            jsObj.put("perPartitionTxnIds", retrievePerPartitionTransactionIds());
            String nonce = jsObj.getString(SnapshotUtil.JSON_NONCE);
            assert(Thread.currentThread().getId() == m_snapshotThreadId);
            final long handle = m_nextCallbackHandle++;
            m_procedureCallbacks.put(handle, new ProcedureCallback() {

                @Override
                public void clientCallback(ClientResponse clientResponse) {
                    m_lastInitiationTs = null;
                    try {
                        /*
                         * If there is an error then we are done.
                         */
                        if (clientResponse.getStatus() != ClientResponse.SUCCESS) {
                            ClientResponseImpl rimpl = (ClientResponseImpl)clientResponse;
                            saveResponseToZKAndReset(requestId, rimpl);
                            return;
                        }

                        /*
                         * Now analyze the response. If a snapshot was in progress
                         * we have to reattempt it later, and send a response to the client
                         * saying it was queued. Otherwise, forward the response
                         * failure/success to the client.
                         */
                        if (isSnapshotInProgressResponse(clientResponse)) {
                            loggingLog.info("Deferring user snapshot with nonce " + nonce + " until after in-progress snapshot completes");
                            scheduleSnapshotForLater(jsObj.toString(4), requestId, true);
                        } else {
                            ClientResponseImpl rimpl = (ClientResponseImpl)clientResponse;
                            saveResponseToZKAndReset(requestId, rimpl);
                            return;
                        }
                    } catch (Exception e) {
                        SNAP_LOG.error("Error processing user snapshot request", e);
                        try {
                            userSnapshotRequestExistenceCheck(true);
                        } catch (Exception e2) {
                            VoltDB.crashLocalVoltDB("Error resetting watch for user snapshots", true, e2);
                        }
                    }
                }
            });
            loggingLog.info("Initiating user snapshot with nonce " + nonce);
            initiateSnapshotSave(handle, new Object[]{jsObj.toString(4)}, blocking);
            return;
        }
    }

    /*
     * Schedule a user snapshot request for later since the database was busy.
     * Continue doing this as long as the error response returned by the DB is snapshot in progress.
     * Since the snapshot is being scheduled for later we will send an immediate response to the client
     * via ZK relay.
     */
    private void scheduleSnapshotForLater(final String requestObj,
                                          final String requestId,
                                          final boolean isFirstAttempt
                                          ) throws Exception {
        /*
         * Only need to send the queue response the first time we attempt to schedule the snapshot
         * for later. It may be necessary to reschedule via this function multiple times.
         */
        if (isFirstAttempt) {
            SNAP_LOG.info("A user snapshot request could not be immediately fulfilled and will be reattempted later");
            /*
             * Construct a result to send to the client right now via ZK
             * saying we queued it to run later
             */
            VoltTable result = SnapshotUtil.constructNodeResultsTable();
            result.addRow(-1,
                    CoreUtils.getHostnameOrAddress(),
                    "",
                    "SUCCESS",
                    "SNAPSHOT REQUEST QUEUED");
            final ClientResponseImpl queuedResponse =
                new ClientResponseImpl(ClientResponseImpl.SUCCESS,
                                       new VoltTable[] { result },
                                       "Snapshot request could not be fulfilled because a snapshot " +
                                         "is in progress. It was queued for execution",
                                       0);
            ByteBuffer buf = ByteBuffer.allocate(queuedResponse.getSerializedSize());
            m_zk.create(VoltZK.user_snapshot_response + requestId,
                        queuedResponse.flattenToBuffer(buf).array(),
                        Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
        }

        /*
         * Now queue the request for later
         */
        final Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    /*
                     * Construct a callback to handle the response to the
                     * @SnapshotSave invocation that will reattempt the user snapshot
                     */
                    assert(Thread.currentThread().getId() == m_snapshotThreadId);
                    final long handle = m_nextCallbackHandle++;
                    m_procedureCallbacks.put(handle, new ProcedureCallback() {
                        @Override
                        public void clientCallback(ClientResponse clientResponse) {
                            m_lastInitiationTs = null;
                            try {
                                /*
                                 * If there is an error then we are done
                                 * attempting this user snapshot. The params must be bad
                                 * or things are broken.
                                 */
                                if (clientResponse.getStatus() != ClientResponse.SUCCESS) {
                                    SNAP_LOG.error(clientResponse.getStatusString());
                                    //Reset the watch, in case this is recoverable
                                    userSnapshotRequestExistenceCheck(true);
                                    return;
                                }

                                VoltTable results[] = clientResponse.getResults();
                                //Do this check to avoid an NPE
                                if (results == null || results.length == 0 || results[0].getRowCount() < 1) {
                                    SNAP_LOG.error("Queued user snapshot request reattempt received an unexpected response" +
                                            " and will not be reattempted. The client response is (status: " +
                                            clientResponse.getStatus() + " " + clientResponse.getStatusString() +
                                            " result: " + (results != null && results.length > 0 ? results[0] : "null") +
                                            ")");
                                    /*
                                     * Don't think this should happen, reset the watch to allow later requests
                                     */
                                    userSnapshotRequestExistenceCheck(true);
                                    return;
                                }

                                VoltTable result = results[0];
                                boolean snapshotInProgress = false;
                                boolean haveFailure = false;
                                while (result.advanceRow()) {
                                    if (result.getString("RESULT").equals("FAILURE")) {
                                        if (result.getString("ERR_MSG").equals("SNAPSHOT IN PROGRESS")) {
                                            snapshotInProgress = true;
                                        } else {
                                            haveFailure = true;
                                        }
                                    }
                                }

                                /*
                                 * If a snapshot was in progress, reattempt later, otherwise,
                                 * if there was a failure, abort the attempt and log.
                                 */
                                if (snapshotInProgress) {
                                    SNAP_LOG.info("Queued user snapshot was reattempted, but a snapshot was " +
                                            " still in progress. It will be reattempted.");
                                    //Turtles all the way down
                                    scheduleSnapshotForLater(
                                            requestObj,
                                            requestId,
                                            false);
                                } else if (haveFailure) {
                                    SNAP_LOG.info("Queued user snapshot was attempted, but there was a failure.");
                                    try {
                                        ClientResponseImpl rimpl = (ClientResponseImpl)clientResponse;
                                        saveResponseToZKAndReset(requestId, rimpl);
                                    }
                                    catch (NodeExistsException e) {
                                        // used to pass null as request ID to avoid this check if the request ID
                                        // already existed, this gives us the same behavior with a pre-existing
                                        // request ID
                                    }
                                    //Log the details of the failure, after resetting the watch in case of some odd NPE
                                    result.resetRowPosition();
                                    SNAP_LOG.info(result);
                                } else {
                                    try {
                                        SNAP_LOG.debug("Queued user snapshot was successfully requested, saving to path " +
                                                VoltZK.user_snapshot_response + requestId);
                                        /*
                                         * Snapshot was started no problem, reset the watch for new requests
                                         */
                                        ClientResponseImpl rimpl = (ClientResponseImpl)clientResponse;
                                        saveResponseToZKAndReset(requestId, rimpl);
                                    }
                                    catch (NodeExistsException e) {
                                        // used to pass null as request ID to avoid this check if the request ID
                                        // already existed, this gives us the same behavior with a pre-existing
                                        // request ID
                                    }
                                    return;
                                }
                            } catch (Exception e) {
                                SNAP_LOG.error("Error processing procedure callback for user snapshot", e);
                                try {
                                    userSnapshotRequestExistenceCheck(true);
                                } catch (Exception e1) {
                                    VoltDB.crashLocalVoltDB(
                                            "Error resetting watch for user snapshot requests", true, e1);
                                }
                            }
                        }
                    });

                    initiateSnapshotSave(handle, new Object[]{requestObj}, false);
                } catch (Exception e) {
                    try {
                        userSnapshotRequestExistenceCheck(true);
                    } catch (Exception e1) {
                        VoltDB.crashLocalVoltDB("Error checking for existence of user snapshots", true, e1);
                    }
                }
            }
        };
        m_es.schedule(r, m_userSnapshotRetryInterval, TimeUnit.SECONDS);
    }

    /*
     * Check a client response to and determine if it is a snapshot in progress response
     * to a snapshot request
     */
    private boolean isSnapshotInProgressResponse(
            ClientResponse response) {
        if (response.getStatus() != ClientResponse.SUCCESS) {
            return false;
        }

        if (response.getResults() == null) {
            return false;
        }

        if (response.getResults().length < 1) {
            return false;
        }

        VoltTable results = response.getResults()[0];
        if (results.getRowCount() < 1) {
            return false;
        }

        boolean snapshotInProgress = false;
        while (results.advanceRow()) {
            if (results.getString("RESULT").equals("FAILURE")) {
                if (results.getString("ERR_MSG").equals("SNAPSHOT IN PROGRESS")) {
                    snapshotInProgress = true;
                }
            }
        }
        return snapshotInProgress;
    }

    /*
     * Set the watch in ZK on the node that represents an internal request
     * for a truncation snapshot
     */
    void truncationRequestExistenceCheck() throws KeeperException, InterruptedException {
        loggingLog.info("Checking for existence of snapshot truncation request");
        m_currentTruncationWatcher.cancel();
        m_currentTruncationWatcher = new TruncationRequestExistenceWatcher();
        // TRAIL [TruncSnap:2] checking for zk node existence
        List<String> requests = m_zk.getChildren(VoltZK.request_truncation_snapshot, m_currentTruncationWatcher);
        if (!requests.isEmpty()) {
            loggingLog.info("A truncation request node already existed, processing truncation request event");
            m_currentTruncationWatcher.cancel();
            // TRAIL [TruncSnap:3] fake a node created event (req ZK node already there)
            processTruncationRequestEvent(new WatchedEvent(
                    EventType.NodeChildrenChanged,
                    KeeperState.SyncConnected,
                    VoltZK.request_truncation_snapshot));
        }
    }

    /*
     * Set the watch in ZK on the node that represents a user
     * request for a snapshot
     */
    void userSnapshotRequestExistenceCheck(boolean deleteExistingRequest) throws Exception {
        if (deleteExistingRequest) {
            m_zk.delete(VoltZK.user_snapshot_request, -1, null, null);
        }
        if (m_zk.exists(VoltZK.user_snapshot_request, m_userSnapshotRequestExistenceWatcher) != null) {
            processUserSnapshotRequestEvent(new WatchedEvent(
                    EventType.NodeCreated,
                    KeeperState.SyncConnected,
                    VoltZK.user_snapshot_request));
        }
    }

    /**
     * This method has two purposes: (1) to provide available scheduling information,
     * and (2) to enable or disable snapshot daemon activity. The latter is only
     * meaningful if we are the elected auto snapshot leader.
     *
     * Called from ClientInterface or RealVoltDB on events that affect eligibility
     * to run.
     */
    public ListenableFuture<Void> mayGoActiveOrInactive(final SnapshotSchedule schedule)
    {
        return m_es.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                makeActivePrivate(schedule);
                return null;
            }
        });
    }

    private void makeActivePrivate(final SnapshotSchedule schedule) {
        m_lastKnownSchedule = schedule;
        m_autoEnabled = schedule.getEnabled();
        m_path = VoltDB.instance().getSnapshotPath();

        // Automatic snapshot scheduling parameters
        if (m_autoEnabled) {
            m_retain = schedule.getRetain();
            m_autoPrefix = schedule.getPrefix();

            int frequency = schedule.getFrequencyvalue();
            TimeUnit timeUnit = TimeUtils.convertTimeUnit(schedule.getFrequencyunit());
            m_frequencyInMillis = timeUnit.toMillis(frequency);

            m_nextSnapshotTime = System.currentTimeMillis() + m_frequencyInMillis;
        }

        // One-time grooming of terminus snapshots
        m_terminusRetention = Integer.getInteger("SHUTDOWN_SNAPSHOT_RETENTION_COUNT", 2);
        m_groomTermini = true; // always

        // Schedule the automatic snapshot task periodically. We need this
        // even if not intending to do automatic snapshots but we do want
        // to groom terminus snapshots. In this case, the task will eventually
        // self-cancel.
        if (m_isAutoSnapshotLeader && (m_autoEnabled || m_groomTermini)) {
            scheduleAutoSnapshotTask();
        }

        // No reason to be running the auto snapshot task, so cancel it
        else {
            cancelAutoSnapshotTask();
        }
    }

    private void scheduleAutoSnapshotTask() {
        if (m_autoSnapshotTask == null) {
            SNAP_LOG.info("Starting periodic snapshot management task");
            m_state = State.STARTUP;
            m_autoSnapshotTask = m_es.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    try {
                        doPeriodicWork(System.currentTimeMillis());
                    } catch (Exception e) {
                        SNAP_LOG.warn("Error doing periodic snapshot management work", e);
                    }
                }
            }, 0, m_periodicWorkInterval, TimeUnit.MILLISECONDS);
        }
    }

    private void cancelAutoSnapshotTask() {
        if (m_autoSnapshotTask != null) {
            SNAP_LOG.info("Terminating periodic snapshot management task");
            m_autoSnapshotTask.cancel(false);
            m_autoSnapshotTask = null;
            m_autoSnapshots.clear();
            m_terminusSnapshots.clear();
            m_state = State.STOPPED;
        }
    }

    private class Snapshot implements Comparable<Snapshot> {
        private final String path;
        private final String nonce;
        private final Long txnId;

        private Snapshot(String path, String nonce, Long txnId) {
            this.path = path;
            this.nonce = nonce;
            this.txnId = txnId;
        }

        @Override
        public int compareTo(Snapshot o) {
            return txnId.compareTo(o.txnId);
        }

        @Override
        public String toString() {
            return path + "/" + nonce;
        }
    }

    /////////////////////////////////////////////////
    // AUTOMATIC SNAPSHOTTING WORK
    ////////////////////////////////////////////////

    /**
     * Periodically invoked by a task that executes at a rate determined by
     * m_frequencyInMillis. Dispatches based on the current daemon state.
     */
    private void doPeriodicWork(long now) {
        assert m_lastKnownSchedule != null;

        switch (m_state) {
        case STARTUP:
            initiateSnapshotScan();
            break;
        case SCANNING:
            SNAP_LOG.rateLimitedInfo(5*60, "Blocked in scanning");
            break;
        case WAITING:
            processWaitingPeriodicWork(now);
            break;
        default:
            break;
        }
    }

    /**
     * Do periodic work when the daemon is in the waiting state. The
     * daemon paces out sysproc invocations over time to avoid
     * disrupting regular work.
     *
     * If the number of retained snapshots exceeds the retention
     * count for auto snapshots, we fire off a grooming cycle.
     *
     * If the time for the next snapshot has passed, we will
     * initiate a new snapshot.
     */
    private void processWaitingPeriodicWork(long now) {

        // If auto snapshots are not enabled, then we should
        // cancel further execution. This is the case when we're
        // only wanting to groom terminus snapshots at startup.
        if (!m_autoEnabled) {
            cancelAutoSnapshotTask();
            return;
        }

        // Fast return if it's too soon to do anything
        if (now - m_lastSysprocInvocation < m_minTimeBetweenSysprocs) {
            return;
        }

        if (m_autoSnapshots.size() > m_retain) {
            // Quick hack to make sure we don't delete while a snapshot is running.
            // Deletes work really badly during a snapshot because the FS is occupied
            if (!SnapshotSiteProcessor.ExecutionSitesCurrentlySnapshotting.isEmpty()) {
                m_lastSysprocInvocation = System.currentTimeMillis() + 3000;
                return;
            }
            deleteExtraSnapshots();
        }

        else if (m_nextSnapshotTime < now) {
            initiateNextSnapshot(now);
        }
    }

    /**
     * Initiates the next 'automatic' snapshot, on a periodic basis.
     */
    private void initiateNextSnapshot(long now) {
        assert Thread.currentThread().getId() == m_snapshotThreadId;

        setState(State.SNAPSHOTTING);
        m_lastSysprocInvocation = now;
        final Date nowDate = new Date(now);
        final String dateString = m_dateFormat.format(nowDate);
        final String nonce = m_autoPrefix + dateString;

        JSONObject jsObj = new JSONObject();
        try {
            jsObj.put(SnapshotUtil.JSON_PATH, m_path);
            jsObj.put(SnapshotUtil.JSON_PATH_TYPE, SnapshotPathType.SNAP_AUTO.toString());
            jsObj.put(SnapshotUtil.JSON_NONCE, nonce);
            jsObj.put("perPartitionTxnIds", retrievePerPartitionTransactionIds());

            m_autoSnapshots.add(new Snapshot(m_path, nonce, now));
            long handle = m_nextCallbackHandle++;

            m_procedureCallbacks.put(handle, new ProcedureCallback() {
                @Override
                public void clientCallback(final ClientResponse clientResponse)
                        throws Exception {
                    m_lastInitiationTs = null;
                    processSnapshotResponse(clientResponse);
                }
            });

            SNAP_LOG.info("Requesting auto snapshot to path " + m_path + " nonce " + nonce);
            initiateSnapshotSave(handle, new Object[] { jsObj.toString(4) }, false);
        } catch (JSONException e) {
            // Should never happen, so fail fast
            VoltDB.crashLocalVoltDB("JSON exception in SnapshotDaemon", false, e);
        }
    }

    /**
     * Invoke the @SnapshotScan system procedure to discover
     * snapshots on disk that are managed by this daemon.
     * Used at daemon startup only; after that we track
     * creations.
     */
    private void initiateSnapshotScan() {
        assert Thread.currentThread().getId() == m_snapshotThreadId;

        m_lastSysprocInvocation = System.currentTimeMillis();
        Object params[] = new Object[1];
        params[0] = m_path;
        setState(State.SCANNING);
        long handle = m_nextCallbackHandle++;

        m_procedureCallbacks.put(handle, new ProcedureCallback() {
            @Override
            public void clientCallback(final ClientResponse clientResponse)
                    throws Exception {
                processScanResponse(clientResponse);
            }
        });

        SNAP_LOG.info("Initiating snapshot scan of " + m_path);
        m_initiator.initiateSnapshotDaemonWork("@SnapshotScan", handle, params);
    }

    /**
     * Process responses to sysproc invocations generated by this
     * daemon (generated in one of the state-processing routines
     * that executes during a call to doPeriodicWork).
     *
     * Dispatches to the appropriate response processor based on
     * looking up the request handle in the callback map.
     */
    public Future<Void> processClientResponse(final Callable<ClientResponseImpl> response) {
        return m_es.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    ClientResponseImpl resp = response.call();
                    long handle = resp.getClientHandle();
                    m_procedureCallbacks.remove(handle).clientCallback(resp);
                } catch (Exception e) {
                    // Here we just consume the exception without propagating; there's no-one to
                    // tell about it. We get here when the procedure callback fails to handle its
                    // own exception.
                    SNAP_LOG.warn("Error when SnapshotDaemon invoked callback for a procedure invocation", e);
                }
                return null;
            }
        });
    }

    /**
     * Process a response to a request to create an auto snapshot.
     * Confirm and log that the snapshot was a success
     */
    private void processSnapshotResponse(ClientResponse response) {
        changeState(State.SNAPSHOTTING, State.WAITING, "snapshot");

        final long now = System.currentTimeMillis();
        m_nextSnapshotTime += m_frequencyInMillis;
        if (m_nextSnapshotTime < now) {
            m_nextSnapshotTime = now - 1;
        }

        if (response.getStatus() != ClientResponse.SUCCESS){
            logFailureResponse("Snapshot failed", response);
            return;
        }

        final VoltTable[] results = response.getResults();
        final VoltTable result = results[0];

        final String err = SnapshotUtil.didSnapshotRequestFailWithErr(results);
        if (err != null) {
            SNAP_LOG.warn("Snapshot failed with failure response: " +  err);
            m_autoSnapshots.removeLast();
            return;
        }

        boolean success = true;
        while (result.advanceRow()) {
            if (!result.getString("RESULT").equals("SUCCESS")) {
                success = false;
                SNAP_LOG.warn("Snapshot save feasibility test failed for host "
                        + result.getLong("HOST_ID") + " table " + result.getString("TABLE") +
                        " with error message " + result.getString("ERR_MSG"));
            }
        }
        if (!success) {
            m_autoSnapshots.removeLast();
        }
    }

    /**
     * Process a response to a request to delete snapshots.
     * Always transitions to the waiting state even if the delete
     * fails. This ensures the system will continue to snapshot
     * until the disk is full in the event that there is an administration
     * error or a bug.
     */
    private void processDeleteResponse(ClientResponse response) {
        changeState(State.DELETING, State.WAITING, "delete");

        if (response.getStatus() != ClientResponse.SUCCESS){
            logFailureResponse("Deletion of snapshots failed", response);
            return;
        }

        final VoltTable[] results = response.getResults();
        final String err = SnapshotUtil.didSnapshotRequestFailWithErr(results);
        if (err != null) {
            SNAP_LOG.warn("Snapshot delete failed with failure response: " + err);
        }
    }

    /**
     * Process the response to a snapshot scan. Find the snapshots
     * that are managed by this daemon, by path and nonce, and
     * add each to the list. Initiate a delete of any that should
     * not be retained.
     */
    private void processScanResponse(ClientResponse response) {
        changeState(State.SCANNING, State.WAITING, "scan");

        final VoltTable snapshots = checkSnapshotScanResponse(response);
        if (snapshots == null) {
            return;
        }

        // Build snapshot lists as needed
        final File myPath = new File(m_path);
        final String autoNonce = m_autoPrefix + '_';
        final String termNonce = m_terminusPrefix + "_";
        while (snapshots.advanceRow()) {
            final String path = snapshots.getString("PATH");
            final File pathFile = new File(path);
            if (pathFile.equals(myPath)) {
                final String nonce = snapshots.getString("NONCE");
                final Long txnId = snapshots.getLong("TXNID");
                if (m_autoEnabled && nonce.startsWith(autoNonce)) {
                    m_autoSnapshots.add(new Snapshot(path, nonce, txnId));
                }
                else if (m_groomTermini && nonce.startsWith(termNonce)) {
                    m_terminusSnapshots.add(new Snapshot(path, nonce, txnId));
                }
            }
        }
        Collections.sort(m_autoSnapshots);
        Collections.sort(m_terminusSnapshots);

        // Immediately groom if needed. Do terminus snapshots first;
        // the WAITING state will pick up the auto snapshots. Otherwise
        // do auto snapshots immediately.
        boolean groomAutoNow = false;
        if (m_autoEnabled) {
            SNAP_LOG.infoFmt("Scan found %d automatic snapshots, retaining %d",
                             m_autoSnapshots.size(), m_retain);
            groomAutoNow = true;
        }
        if (m_groomTermini) {
            SNAP_LOG.infoFmt("Scan found %d shutdown snapshots, retaining %d",
                             m_terminusSnapshots.size(), m_terminusRetention);
            groomAutoNow &= !deleteExtraSnapshots(m_terminusSnapshots, m_terminusRetention);
        }
        if (groomAutoNow) {
            deleteExtraSnapshots(m_autoSnapshots, m_retain);
        }
    }

    /**
     * Utility routine to check results of @SnapshotScan and extract
     * the table of snapshots.
     */
    private VoltTable checkSnapshotScanResponse(ClientResponse response) {
        if (response.getStatus() != ClientResponse.SUCCESS) {
            logFailureResponse("Snapshot scan failed", response);
            return null;
        }

        final VoltTable[] results = response.getResults();
        if (results.length == 1) {
            final VoltTable result = results[0];
            boolean advanced = result.advanceRow();
            assert advanced;
            assert result.getColumnCount() == 1;
            assert result.getColumnType(0) == VoltType.STRING;
            SNAP_LOG.warn("Snapshot scan failed with failure response: " + result.getString("ERR_MSG"));
            return null;
        }
        assert results.length == 3;

        final VoltTable snapshots = results[0];
        assert snapshots.getColumnCount() == 10;
        return snapshots;
    }

    /**
     * Check if there are extra snapshots and initiate deletion.
     * Snapshot list is sorted in ascending order of transaction
     * id, and therefore in order of age.
     */
    private void deleteExtraSnapshots() {
        deleteExtraSnapshots(m_autoSnapshots, m_retain);
    }

    /*
     * Given a sorted list of candidate snapshots, conditionally
     * initiates a request to @SnapshotDelete to delete sufficient
     * number of snapshots to get the list down to the desired
     * length.
     *
     * Generally this is processing automatic snapshots, but
     * during startup it may prune terminus snapshots.
     *
     * Returns false if there's no need to delete anything (and
     * state will be WAITING).  Returns true if we have initiated
     * deletion (and state will now be DELETING).
     */
    private boolean deleteExtraSnapshots(LinkedList<Snapshot> candidates, int retainCount) {
        assert Thread.currentThread().getId() == m_snapshotThreadId;

        int deleteCount = candidates.size() - retainCount;
        if (deleteCount <= 0) {
            setState(State.WAITING);
            return false;
        }

        m_lastSysprocInvocation = System.currentTimeMillis();
        setState(State.DELETING);

        String[] pathsToDelete = new String[deleteCount];
        String[] noncesToDelete = new String[deleteCount];
        for (int ii=0; ii<deleteCount; ii++) {
            Snapshot snap = candidates.poll();
            pathsToDelete[ii] = snap.path;
            noncesToDelete[ii] = snap.nonce;
            SNAP_LOG.info("Snapshot daemon deleting " + snap.nonce);
        }

        Object[] params = new Object[] { pathsToDelete,
                                         noncesToDelete,
                                         SnapshotPathType.SNAP_AUTO.toString() };
        long handle = m_nextCallbackHandle++;
        m_procedureCallbacks.put(handle, new ProcedureCallback() {
            @Override
            public void clientCallback(final ClientResponse clientResponse)
                throws Exception {
                processDeleteResponse(clientResponse);
            }
        });

        m_initiator.initiateSnapshotDaemonWork("@SnapshotDelete", handle, params);
        return true;
    }

    /*
     * Utility to log a failure for any failed response.
     */
    private void logFailureResponse(String message, ClientResponse response) {
        String status = response.getStatusString();
        if (status != null && !status.isEmpty()) {
            message += "\n\t" + status;
        }
        SNAP_LOG.warn(message);
    }

    /////////////////////////////////////////////////
    // MISCELLANEOUS ROUTINES
    ////////////////////////////////////////////////

    State getState() {
        return m_state;
    }

    private void setState(State state) {
        m_state = state;
    }

    private void changeState(State from, State to, String what) {
        if (m_state != from) {
            String err = String.format("Response to %s request being processed in state %s, expected state %s",
                                       what, m_state, from);
            SNAP_LOG.warn(err);
            throw new RuntimeException(err);
        }
        m_state = to;
    }

    public void shutdown() throws InterruptedException {
        if (m_truncationSnapshotScanTask != null) {
            m_truncationSnapshotScanTask.cancel(false);
        }

        m_es.shutdown();
        m_es.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    /*
     * If we are the leader, go ahead an create the procedure invocation to do the work.
     * We aren't going to journal this in ZK. if the leader dies there will be no
     * one to try and complete the work. C'est la vie.
     */
    public void requestUserSnapshot(final StoredProcedureInvocation invocation, final Connection c) {
        m_es.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    submitUserSnapshotRequest(invocation, c);
                } catch (Exception e) {
                    VoltDB.crashLocalVoltDB("Exception submitting user snapshot request", true, e);
                }
            }
        });
    }

    public static final class ForwardClientException extends Exception {
        /**
         *
         */
        private static final long serialVersionUID = 1L;
        private final VoltTable m_table;
        public ForwardClientException(String msg, VoltTable table) {
            super(msg);
            m_table = table;
        }

    }

    private void submitUserSnapshotRequest(final StoredProcedureInvocation invocation, final Connection c) {
        Object params[] = invocation.getParams().toArray();

        try {
            /*
             * Dang it, have to parse the params here to validate
             */
            SnapshotInitiationInfo snapInfo = new SnapshotInitiationInfo(params);

            createAndWatchRequestNode(invocation.clientHandle, c, snapInfo,
                    false);
        } catch (Exception e) {
            VoltTable tables[] = new VoltTable[0];
            byte status = ClientResponseImpl.GRACEFUL_FAILURE;
            if (e instanceof ForwardClientException && ((ForwardClientException)e).m_table != null) {
                tables = new VoltTable[] { ((ForwardClientException)e).m_table };
                status = ClientResponseImpl.SUCCESS;
            }
            final ClientResponseImpl errorResponse =
                    new ClientResponseImpl(status,
                                           tables,
                                           Throwables.getStackTraceAsString(e),
                                           invocation.clientHandle);
            ByteBuffer buf = ByteBuffer.allocate(errorResponse.getSerializedSize() + 4);
            buf.putInt(buf.capacity() - 4);
            errorResponse.flattenToBuffer(buf).flip();
            c.writeStream().enqueue(buf);
            return;
        }
    }

    /**
     * Try to create the ZK request node and watch it if created successfully.
     */
    public void createAndWatchRequestNode(final long clientHandle,
                                          final Connection c,
                                          SnapshotInitiationInfo snapInfo,
                                          boolean notifyChanges) throws ForwardClientException {
        boolean requestExists = false;
        final String requestId = createRequestNode(snapInfo);
        if (requestId == null) {
            requestExists = true;
        } else {
            if (!snapInfo.isTruncationRequest()) {
                try {
                    registerUserSnapshotResponseWatch(requestId, clientHandle, c, notifyChanges);
                } catch (Exception e) {
                    VoltDB.crashLocalVoltDB("Failed to register ZK watch on snapshot response", true, e);
                }
            }
            else {
                // need to construct a success response of some sort here to indicate the truncation attempt
                // was successfully attempted
                VoltTable result = SnapshotUtil.constructNodeResultsTable();
                result.addRow(-1,
                        CoreUtils.getHostnameOrAddress(),
                        "",
                        "SUCCESS",
                        "SNAPSHOT REQUEST QUEUED");
                final ClientResponseImpl resp =
                    new ClientResponseImpl(ClientResponseImpl.SUCCESS,
                            new VoltTable[] {result},
                            "User-requested truncation snapshot successfully queued for execution.",
                            clientHandle);
                ByteBuffer buf = ByteBuffer.allocate(resp.getSerializedSize() + 4);
                buf.putInt(buf.capacity() - 4);
                resp.flattenToBuffer(buf).flip();
                c.writeStream().enqueue(buf);
            }
        }

        if (requestExists) {
            VoltTable result = SnapshotUtil.constructNodeResultsTable();
            result.addRow(-1,
                    CoreUtils.getHostnameOrAddress(),
                    "",
                    "FAILURE",
                    "SNAPSHOT IN PROGRESS");
            throw new ForwardClientException("A request to perform a user snapshot already exists", result);
        }
    }

    /**
     * Try to create the ZK node to request the snapshot.
     *
     * @param snapInfo SnapshotInitiationInfo object with the requested snapshot initiation settings
     * @return The request ID if succeeded, otherwise null.
     */
    private String createRequestNode(SnapshotInitiationInfo snapInfo)
    {
        String requestId = null;

        try {
            requestId = java.util.UUID.randomUUID().toString();
            if (!snapInfo.isTruncationRequest()) {
                final JSONObject jsObj = snapInfo.getJSONObjectForZK();
                jsObj.put("requestId", requestId);
                String zkString = jsObj.toString(4);
                byte zkBytes[] = zkString.getBytes("UTF-8");

                m_zk.create(VoltZK.user_snapshot_request, zkBytes, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            else {
                m_zk.create(VoltZK.request_truncation_snapshot_node, null,
                        Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
            }
        } catch (KeeperException.NodeExistsException e) {
            return null;
        } catch (Exception e) {
            VoltDB.crashLocalVoltDB("Exception while attempting to create user snapshot request in ZK", true, e);
        }

        return requestId;
    }

    private void registerUserSnapshotResponseWatch(
            final String requestId,
            final long clientHandle,
            final Connection c,
            final boolean notifyChanges) throws Exception {
        final String responseNode = VoltZK.user_snapshot_response + requestId;
        Stat exists = m_zk.exists(responseNode, new Watcher() {
            @Override
            public void process(final WatchedEvent event) {
                if (event.getState() == KeeperState.Disconnected) return;
                switch (event.getType()) {
                case NodeCreated:
                    m_es.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                processUserSnapshotRequestResponse(
                                            event,
                                            clientHandle,
                                            c,
                                            notifyChanges);
                            } catch (Exception e) {
                                VoltDB.crashLocalVoltDB(
                                        "Error retrieving user snapshot request response from ZK",
                                        true,
                                        e);
                            }
                        }
                    });
                    break;
                default:
                }
            }
        });

        if (exists != null) {
            processUserSnapshotRequestResponse(
                    new WatchedEvent(
                        EventType.NodeCreated,
                        KeeperState.SyncConnected,
                        responseNode),
                        clientHandle,
                        c,
                        notifyChanges);
        }
    }

    void processUserSnapshotRequestResponse(
            final WatchedEvent event,
            final long clientHandle,
            final Connection c,
            final boolean notifyChanges) throws Exception {
        byte responseBytes[] = m_zk.getData(event.getPath(), false, null);
        try {
            m_zk.delete(event.getPath(), -1, null, null);
        } catch (Exception e) {
            SNAP_LOG.error("Error cleaning up user snapshot request response in ZK", e);
        }
        ByteBuffer buf = ByteBuffer.wrap(responseBytes);
        ClientResponseImpl response = new ClientResponseImpl();
        response.initFromBuffer(buf);
        response.setClientHandle(clientHandle);

        // Not sure if we need to preserve the original byte buffer here, playing it safe
        ByteBuffer buf2 = ByteBuffer.allocate(response.getSerializedSize() + 4);
        buf2.putInt(buf2.capacity() - 4);
        response.flattenToBuffer(buf2).flip();
        c.writeStream().enqueue(buf2);

        /*
         * If the caller wants to be notified of final results for the snapshot
         * request, set up a watcher only if the snapshot is queued.
         */
        if (notifyChanges && (response.getStatus() == ClientResponse.SUCCESS) &&
            SnapshotUtil.isSnapshotQueued(response.getResults())) {
            Watcher watcher = new Watcher() {
                @Override
                public void process(final WatchedEvent event) {
                    if (event.getState() == KeeperState.Disconnected) return;
                    switch (event.getType()) {
                    case NodeCreated:
                        m_es.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    processUserSnapshotRequestResponse(
                                                event,
                                                clientHandle,
                                                c,
                                                false);
                                } catch (Exception e) {
                                    VoltDB.crashLocalVoltDB(
                                                "Error retrieving user snapshot request response from ZK",
                                                true,
                                                e);
                                }
                            }
                        });
                        break;
                    default:
                    }
                }
            };

            // Set the watcher
            if (m_zk.exists(event.getPath(), watcher) != null) {
                processUserSnapshotRequestResponse(event, clientHandle, c, false);
            }
        }
    }

    /////////////////////////////////////////////////
    // SNAPSHOT COMPLETION INTERFACE
    ////////////////////////////////////////////////

    /*
     * Called on completion of any snapshot request
     * in the VoltDB server.
     *
     * For successful completion of truncation snapshots, request
     * execution of the truncation snapshot groomer.
     */
    @Override
    public CountDownLatch snapshotCompleted(final SnapshotCompletionEvent event) {
        if (!event.truncationSnapshot || !event.didSucceed) {
            return new CountDownLatch(0);
        }
        final CountDownLatch latch = new CountDownLatch(1);
        m_es.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    TruncationSnapshotAttempt snapshotAttempt = m_truncationSnapshotAttempts.get(event.multipartTxnId);
                    if (snapshotAttempt == null) {
                        snapshotAttempt = new TruncationSnapshotAttempt();
                        snapshotAttempt.path = event.path;
                        snapshotAttempt.nonce = event.nonce;
                        snapshotAttempt.pathType = SnapshotPathType.SNAP_CL.toString();
                        m_truncationSnapshotAttempts.put(event.multipartTxnId, snapshotAttempt);
                    }
                    snapshotAttempt.finished = true;
                    groomTruncationSnapshots();
                } finally {
                    latch.countDown();
                }
            }
        });
        return latch;
    }

}
