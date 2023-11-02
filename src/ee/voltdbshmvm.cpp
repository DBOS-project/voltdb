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

/*
 Implement the Java ExecutionEngine interface using IPC to a standalone EE
 process. This allows the backend to run without a JVM - useful for many
 debugging tasks.  Represents a single EE in a single process. Accepts
 and executes commands from Java synchronously.
 */

#include <signal.h>
#include <sys/socket.h>
#include <netinet/tcp.h> // for TCP_NODELAY
#define DISABLE_BOUNDS_CHECKS
//#include <Image.h>
#include <vector>
#include <memory>
#include <thread>

#include "common/LargeTempTableBlockId.hpp"
#include "common/Topend.h"

#include "execution/VoltDBEngine.h"
#include "logging/StdoutLogProxy.h"
#include "storage/table.h"

#include "common/debuglog.h"
#include "common/ElasticHashinator.h"
#include "common/serializeio.h"
#include "common/SegvException.hpp"
#include "common/SynchronizedThreadLock.h"
#include "common/types.h"

#include "aeron/AtomicBuffer.h"
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <string.h>

// Please don't make this different from the JNI result buffer size.
// This determines the size of the EE results buffer and it's nice
// if IPC and JNI are matched.
#define MAX_MSG_SZ (1024*1024*10)

static int g_cleanUpCountdownLatch = -1;
static pthread_mutex_t g_cleanUpMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cleanUpCondition = PTHREAD_COND_INITIALIZER;

namespace voltdb {
class Pool;
class StreamBlock;
class Table;
}

class AeronConnectionPair;

#define RING_BUFFER_CAP (512*1024)
struct MetaData {
    uint64_t read_pos;
    char pad_1[64 - sizeof(read_pos)];
    uint64_t write_pos;
    char pad_2[128 - sizeof(write_pos) - sizeof(pad_1) - sizeof(read_pos)];
    uint64_t is_halted;
    char pad_3[4096 - sizeof(write_pos) - sizeof(pad_1) - sizeof(read_pos) - sizeof(pad_2) - sizeof(write_pos)];
};
class RingByteBuffer {
public:

    static constexpr int kReadPosOffset = 0;
    static constexpr int kWritePosOffset = 64;
    static constexpr int kIsHaltedOffset = 128;
    static constexpr int kIsUnhaltPostedOffset = 128 + 64;
    static constexpr int kDataStartOffset = sizeof(MetaData);
    static_assert(sizeof(MetaData) == 4096, "sizeof(MetaData) != 4096");
    char * byte_array;
    uint64_t capacity; // including metadata
    uint64_t effective_capacity; // capacity excluding metadata
    aeron::concurrent::AtomicBuffer metadata_buffer;
    aeron::concurrent::AtomicBuffer data_buffer;

    uint64_t read_pos_cached = 0;
    uint64_t write_pos_cached = 0;
    void clear() {
        memset(byte_array, 0, capacity);
    }
    RingByteBuffer(char * byte_array, long capacity) :
         byte_array(byte_array), capacity(capacity) {
        effective_capacity = capacity - sizeof(MetaData);
        assert(capacity >= sizeof(Metadata));
        metadata_buffer = aeron::concurrent::AtomicBuffer((unsigned char *)byte_array, sizeof(MetaData));
        data_buffer = aeron::concurrent::AtomicBuffer((unsigned char *)byte_array + sizeof(MetaData), effective_capacity);
    }

    std::size_t get_read_pos() {
        return metadata_buffer.getInt64(kReadPosOffset);
    }

    inline std::size_t get_write_pos() {
        return metadata_buffer.getInt64(kWritePosOffset);
    }

    inline void set_read_pos(std::size_t new_read_pos) {
        return metadata_buffer.putInt64(kReadPosOffset, new_read_pos);
    }
    
    inline void set_write_pos(std::size_t new_write_pos) {
        return metadata_buffer.putInt64(kWritePosOffset, new_write_pos);
    }

    inline void set_halted() {
        metadata_buffer.putInt64(kIsHaltedOffset, 1);
    }

    inline void clear_halted() {
        metadata_buffer.putInt64(kIsHaltedOffset, 0);
    }

    inline std::size_t get_is_halted() {
        return metadata_buffer.getInt64(kIsHaltedOffset);
    }


    // inline std::size_t writable_bytes() {
    //     std::size_t read_pos = get_read_pos();
    //     std::size_t write_pos = get_write_pos();
    //     assert(write_pos - read_pos <= effective_capacity);
    //     return effective_capacity - (write_pos - read_pos);
    // }

    // inline std::size_t empty() {
    //     return readable_bytes() == 0;
    // }

    inline std::size_t readable_bytes() {
        return get_write_pos() - get_read_pos();
    }

    bool read_bytes(void * buf, std::size_t n) {
        //assert(readble_bytes() >= n);
        std::size_t read_pos = get_read_pos();
        if (write_pos_cached - read_pos < n) {
            write_pos_cached = get_write_pos();
            if (write_pos_cached - read_pos < n) {
                return false;
            }
        }
        
        std::size_t real_read_pos = read_pos % effective_capacity;

        if (real_read_pos + n <= effective_capacity) {
            data_buffer.getBytes(real_read_pos, (uint8_t*)buf, n);
        } else {
            std::size_t first_part_length = effective_capacity - real_read_pos;
            std::size_t second_part_length = real_read_pos + n - effective_capacity;
            data_buffer.getBytes(real_read_pos, (uint8_t*)buf, first_part_length);
            data_buffer.getBytes(0, (uint8_t*)buf + first_part_length, second_part_length);
            assert(first_part_length + second_part_length == n);
        }

        set_read_pos(read_pos + n);
        return true;
    }

    bool write_bytes(const void * buf, std::size_t n) {
        //assert(writable_bytes() >= n);
        std::size_t write_pos = get_write_pos();
        if (effective_capacity - (write_pos - read_pos_cached) < n) {
            read_pos_cached = get_read_pos();
            if (effective_capacity - (write_pos - read_pos_cached) < n) {
                return false;
            }
        }

        std::size_t real_write_pos = write_pos % effective_capacity;

        if (real_write_pos + n <= effective_capacity) {
            data_buffer.putBytes(real_write_pos, (const uint8_t*)buf, n);
        } else {
            std::size_t first_part_length = effective_capacity - real_write_pos;
            std::size_t second_part_length = real_write_pos + n - effective_capacity;
            data_buffer.putBytes(real_write_pos, (const uint8_t*)buf, first_part_length);
            data_buffer.putBytes(0, (const uint8_t*)buf + first_part_length, second_part_length);
        }
        set_write_pos(write_pos + n);
        return true;
    }
};

class VoltDBSHM : public voltdb::Topend {
public:

    // must match ERRORCODE_SUCCESS|ERROR in ExecutionEngine.java
    enum {
        kErrorCode_None = -1, // not in the java
        kErrorCode_Success = 0,
        kErrorCode_Error = 1,
        /*
         * The following are not error codes but requests for information or functionality
         * from Java. These do not exist in ExecutionEngine.java since they are IPC specific.
         * These constants are mirrored in ExecutionEngine.java.
         */
        kErrorCode_RetrieveDependency = 100,           // Request for dependency
        kErrorCode_DependencyFound = 101,              // Response to 100
        kErrorCode_DependencyNotFound = 102,           // Also response to 100
        kErrorCode_pushExportBuffer = 103,             // Indication that export buffer is next
        kErrorCode_CrashVoltDB = 104,                  // Crash with reason string
        //kErrorCode_getQueuedExportBytes = 105,         // Retrieve value for stats (DEPRECATED)
        kErrorCode_pushPerFragmentStatsBuffer = 106,   // Indication that per-fragment statistics buffer is next
        kErrorCode_callJavaUserDefinedFunction = 107,  // Notify the frontend to call a Java user-defined function.
        kErrorCode_needPlan = 110,                     // fetch a plan from java for a fragment
        kErrorCode_progressUpdate = 111,               // Update Java on execution progress
        kErrorCode_decodeBase64AndDecompress = 112,    // Decode base64, compressed data
        kErrorCode_callJavaUserDefinedAggregateStart = 114,  // Notify the frontend to call a Java user-defined aggregate function start method.
        kErrorCode_callJavaUserDefinedAggregateAssemble = 115,  // Notify the frontend to call a Java user-defined aggregate function assemble method.
        kErrorCode_callJavaUserDefinedAggregateCombine = 116,  // Notify the frontend to call a Java user-defined aggregate function combine method.
        kErrorCode_callJavaUserDefinedAggregateWorkerEnd = 117,  // Notify the frontend to call a Java user-defined aggregate function worker end method.
        kErrorCode_callJavaUserDefinedAggregateCoordinatorEnd = 118  // Notify the frontend to call a Java user-defined aggregate function coordinator end method.
    };

    VoltDBSHM(AeronConnectionPair * cp);

    ~VoltDBSHM();

    const voltdb::VoltDBEngine* getEngine() const {
        return m_engine;
    }

    int loadNextDependency(int32_t dependencyId, voltdb::Pool *stringPool, voltdb::Table* destination);
    void fallbackToEEAllocatedBuffer(char *buffer, size_t length) { }

    /**
     * Retrieve a dependency from Java via the IPC connection.
     * This method returns null if there are no more dependency tables. Otherwise
     * it returns a pointer to a buffer containing the dependency. The first four bytes
     * of the buffer is an int32_t length prefix.
     *
     * The returned allocated memory must be freed by the caller.
     * Returns dependency size with out parameter.
     */
    char *retrieveDependency(int32_t dependencyId, size_t *dependencySz);

    int64_t fragmentProgressUpdate(
            int32_t batchIndex,
            voltdb::PlanNodeType planNodeType,
            int64_t tuplesProcessed,
            int64_t currMemoryInBytes,
            int64_t peakMemoryInBytes);

    std::string decodeBase64AndDecompress(const std::string& base64Data);

    /**
     * Retrieve a plan from Java via the IPC connection for a fragment id.
     * Plan is JSON. Returns the empty string on failure, but failure is
     * probably going to be detected somewhere else.
     */
    std::string planForFragmentId(int64_t fragmentId);

    bool execute(struct ipc_command *cmd);

    int64_t pushDRBuffer(int32_t partitionId, voltdb::DrStreamBlock *block);

    void reportDRBuffer(int32_t partitionId, const char *reason, const char *buffer, size_t length);

    void pushPoisonPill(int32_t partitionId, std::string& reason, voltdb::DrStreamBlock *block);

    /**
     * Log a statement on behalf of the IPC log proxy at the specified log level
     * @param LoggerId ID of the logger that received this statement
     * @param level Log level of the statement
     * @param statement null terminated UTF-8 string containing the statement to log
     */
    void log(voltdb::LoggerId loggerId, voltdb::LogLevel level, const char *statement) const;

    void crashVoltDB(voltdb::FatalException e);

    /*
     * Cause the engine to terminate gracefully after finishing execution of the current command.
     * Useful when running Valgrind because you can terminate at the point where you think memory has leaked
     * and this method will make sure that the VoltDBEngine is deleted and that the program will attempt
     * to free all memory allocated on the heap.
     */
    void shutDown();

    int64_t getQueuedExportBytes(int32_t partitionId, std::string signature);
    void pushExportBuffer(int32_t partitionId, std::string signature, voltdb::ExportStreamBlock *block);

    int reportDRConflict(int32_t partitionId, int32_t remoteClusterId, int64_t remoteTimestamp,
            std::string tableName, bool isReplicatedTable, voltdb::DRRecordType action,
            voltdb::DRConflictType deleteConflict, voltdb::Table *existingMetaTableForDelete, voltdb::Table *existingTupleTableForDelete,
            voltdb::Table *expectedMetaTableForDelete, voltdb::Table *expectedTupleTableForDelete,
            voltdb::DRConflictType insertConflict, voltdb::Table *existingMetaTableForInsert, voltdb::Table *existingTupleTableForInsert,
            voltdb::Table *newMetaTableForInsert, voltdb::Table *newTupleTableForInsert);

    bool storeLargeTempTableBlock(voltdb::LargeTempTableBlock* block);

    bool loadLargeTempTableBlock(voltdb::LargeTempTableBlock* block);

    bool releaseLargeTempTableBlock(voltdb::LargeTempTableBlockId blockId);


private:

    int8_t stub(struct ipc_command *cmd);

    int8_t loadCatalog(struct ipc_command *cmd);

    int8_t updateCatalog(struct ipc_command *cmd);

    int8_t initialize(struct ipc_command *cmd);

    int8_t toggleProfiler(struct ipc_command *cmd);

    int8_t releaseUndoToken(struct ipc_command *cmd);

    int8_t undoUndoToken(struct ipc_command *cmd);

    int8_t tick(struct ipc_command *cmd);

    int8_t quiesce(struct ipc_command *cmd);

    int8_t setLogLevels(struct ipc_command *cmd);

    void executePlanFragments(struct ipc_command *cmd);

    void getStats(struct ipc_command *cmd);

    int8_t loadTable(struct ipc_command *cmd);

    void tableHashCode( struct ipc_command *cmd);

    void hashinate(struct ipc_command* cmd);

    void updateHashinator(struct ipc_command *cmd);

    void threadLocalPoolAllocations();

    void applyBinaryLog(struct ipc_command*);

    void executeTask(struct ipc_command*);

    void sendPerFragmentStatsBuffer();

    int callJavaUserDefinedHelper(int kErrorCode);

    int callJavaUserDefinedFunction();

    int callJavaUserDefinedAggregateStart(int functionId);

    int callJavaUserDefinedAggregateAssemble();

    int callJavaUserDefinedAggregateCombine();

    int callJavaUserDefinedAggregateWorkerEnd();

    int callJavaUserDefinedAggregateCoordinatorEnd();

    void setViewsEnabled(struct ipc_command*);

    // We do not adjust the UDF buffer size in the IPC mode.
    // The buffer sizes are always MAX_MSG_SZ (10M)
    void resizeUDFBuffer(int32_t size) {
        return;
    }

    void sendException( int8_t errorCode);

    /*
     * If errorCode is 0 only the errorCode will be sent otherwise the errorCode and exception will be sent
     * return true if an exception was sent
     */
    bool sendResponseOrException(uint8_t errorCode);

    int8_t activateTableStream(struct ipc_command *cmd);
    void tableStreamSerializeMore(struct ipc_command *cmd);
    void setExportStreamPositions(struct ipc_command *cmd);
    void deleteMigratedRows(struct ipc_command *cmd);
    void getUSOForExportTable(struct ipc_command *cmd);

    // ------------------------------------------------------
    // Methods for topics interface
    // ------------------------------------------------------
    void storeTopicsGroup(struct ipc_command *cmd);
    void deleteTopicsGroup(struct ipc_command *cmd);
    void fetchTopicsGroups(struct ipc_command *cmd);
    void commitTopicsGroupOffsets(struct ipc_command *cmd);
    void fetchTopicsGroupOffsets(struct ipc_command *cmd);
    void deleteExpiredTopicsOffsets(struct ipc_command *cmd);

    // Used to set which tables can be used for DR
    void setReplicableTables(struct ipc_command *cmd);
    void clearReplicableTables(struct ipc_command *cmd);
    void clearAllReplicableTables(struct ipc_command *cmd);

    void signalHandler(int signum, siginfo_t *info, void *context);
    static void signalDispatcher(int signum, siginfo_t *info, void *context);
    void setupSigHandler(void) const;

    voltdb::VoltDBEngine *m_engine;
    long int m_counter;

    AeronConnectionPair * m_cp;
    char *m_perFragmentStatsBuffer;
    char *m_reusedResultBuffer;
    char *m_exceptionBuffer;
    char *m_udfBuffer;
    bool m_terminate;

    // The tuple buffer gets expanded (doubled) as needed, but never compacted.
    char *m_tupleBuffer;
    size_t m_tupleBufferSize;
};

/* java sends all data with this header */
struct ipc_command {
    int32_t msgsize;
    int32_t command;
    char data[0];
}__attribute__((packed));

/*
 * Structure describing an executePlanFragments message header.
 */
typedef struct {
    struct ipc_command cmd;
    int64_t txnId;
    int64_t spHandle;
    int64_t lastCommittedSpHandle;
    int64_t uniqueId;
    int64_t undoToken;
    int8_t perFragmentTimingEnabled;
    int32_t numFragmentIds;
    char data[0];
}__attribute__((packed)) querypfs;

typedef struct {
    struct ipc_command cmd;
    int32_t planFragLength;
    char data[0];
}__attribute__((packed)) loadfrag;

/*
 * Header for a load table request.
 */
typedef struct {
    struct ipc_command cmd;
    int32_t tableId;
    int64_t txnId;
    int64_t spHandle;
    int64_t lastCommittedSpHandle;
    int64_t uniqueId;
    int64_t undoToken;
    int8_t callerId;
    char data[0];
}__attribute__((packed)) load_table_cmd;

/*
 * Header for a stats table request.
 */
typedef struct {
    struct ipc_command cmd;
    int32_t selector;
    int8_t  interval;
    int64_t now;
    int32_t num_locators;
    int32_t locators[0];
}__attribute__((packed)) get_stats_cmd;

struct undo_token {
    struct ipc_command cmd;
    int64_t token;
    char isEmptyDRTxn;
}__attribute__((packed));

/*
 * Header for a ActivateCopyOnWrite request
 */
typedef struct {
    struct ipc_command cmd;
    voltdb::CatalogId tableId;
    voltdb::TableStreamType streamType;
    voltdb::HiddenColumnFilter::Type hiddenColumnFilterType;
    int64_t undoToken;
    char data[0];
}__attribute__((packed)) activate_tablestream;

/*
 * Header for a Copy On Write Serialize More request
 */
typedef struct {
    struct ipc_command cmd;
    voltdb::CatalogId tableId;
    voltdb::TableStreamType streamType;
    int bufferCount;
    char data[0];
}__attribute__((packed)) tablestream_serialize_more;

/*
 * Header for an incoming recovery message
 */
typedef struct {
    struct ipc_command cmd;
    int32_t messageLength;
    char message[0];
}__attribute__((packed)) recovery_message;

/*
 * Header for a request for a table hash code
 */
typedef struct {
    struct ipc_command cmd;
    int32_t tableId;
}__attribute__((packed)) table_hash_code;

typedef struct {
    struct ipc_command cmd;
    int32_t configLength;
    char data[0];
}__attribute__((packed)) hashinate_msg;

/*
 * Header for an Export action.
 */
typedef struct {
    struct ipc_command cmd;
    int64_t offset;
    int64_t seqNo;
    int64_t genId;
    int32_t tableSignatureLength;
    char tableSignature[0];
}__attribute__((packed)) export_positions;

typedef struct {
    struct ipc_command cmd;
    int64_t txnId;
    int64_t spHandle;
    int64_t uniqueId;
    int64_t deletableTxnId;
    int64_t undoToken;
    int32_t maxRowCount;
    int32_t tableNameLength;
    char tableName[0];
}__attribute__((packed)) delete_migrated_rows;

typedef struct {
    struct ipc_command cmd;
    int64_t undoToken;
    int32_t groupDataLength;
    char groupData[0];
}__attribute__((packed)) store_topics_group;

typedef struct {
    struct ipc_command cmd;
    int64_t undoToken;
    int32_t groupIdLength;
    char groupId[0];
}__attribute__((packed)) delete_topics_group;

typedef struct {
    struct ipc_command cmd;
    int32_t maxResultSize;
    int32_t groupIdLength;
    char startGroupId[0];
}__attribute__((packed)) fetch_topics_groups;

typedef struct {
    struct ipc_command cmd;
    int64_t uniqueId;
    int64_t undoToken;
    int16_t requestVersion;
    int32_t groupIdLength;
    int32_t offsetsLength;
    char data[0]; // After groupId is offsets
}__attribute__((packed)) commit_topics_group_offsets;

typedef struct {
    struct ipc_command cmd;
    int16_t requestVersion;
    int32_t groupIdLength;
    int32_t offsetsLength;
    char data[0]; // After groupId is offsets
}__attribute__((packed)) fetch_topics_group_offsets;

typedef struct {
    struct ipc_command cmd;
    int64_t undoToken;
    int64_t deleteOlderThan;
}__attribute__((packed)) delete_expired_topics_offsets;

typedef struct {
    struct ipc_command cmd;
    int32_t streamNameLength;
    char streamName[0];
}__attribute__((packed)) get_uso;

typedef struct {
    struct ipc_command cmd;
    int64_t timestamp;
    char data[0];
}__attribute__((packed)) catalog_load;

typedef struct {
    struct ipc_command cmd;
    int64_t taskId;
    char task[0];
}__attribute__((packed)) execute_task;

typedef struct {
    struct ipc_command cmd;
    int64_t txnId;
    int64_t spHandle;
    int64_t lastCommittedSpHandle;
    int64_t uniqueId;
    int32_t remoteClusterId;
    int32_t remotePartitionId;
    int64_t undoToken;
    char log[0];
}__attribute__((packed)) apply_binary_log;

typedef struct {
    struct ipc_command cmd;
    int64_t timestamp;
    int32_t isStreamChange;
    char data[0];
}__attribute__((packed)) update_catalog_cmd;

typedef struct {
    struct ipc_command cmd;
    char enabled;
    char viewNameBytes[0];
}__attribute__((packed)) set_views_enabled;

typedef struct {
    struct ipc_command cmd;
    int32_t clusterId;
    int32_t tableCount;
    char tableNames[0];
}__attribute__((packed)) set_replicable_tables;

using namespace voltdb;

// This is used by the signal dispatcher
static VoltDBSHM *currentVolt = NULL;

static bool staticDebugVerbose = false;

static constexpr std::size_t bufferSize = 1024 * 1024;
class AeronConnectionPair {
public:
    int32_t dual_qemu_lapic_id = -1;
    int32_t core_id = -1;
    uint64_t pv_notify_count = 0;
    uint64_t pv_wait_count = 0;
    bool use_para_virt_notification = false;
    RingByteBuffer * outgoing_ringbuffer;
    RingByteBuffer * incoming_ringbuffer;
    AeronConnectionPair(RingByteBuffer * outgoing_ringbuffer, RingByteBuffer * incoming_ringbuffer): outgoing_ringbuffer(outgoing_ringbuffer), incoming_ringbuffer(incoming_ringbuffer) {
        
    }
};


static ssize_t read(AeronConnectionPair * cp, void *buf, size_t sz);
static void writeOrDie(AeronConnectionPair * cp, const unsigned char *data, ssize_t sz, bool no_notify = false);

/**
 * Utility used for deserializing ParameterSet passed from Java.
 */
void deserializeParameterSetCommon(int cnt, ReferenceSerializeInputBE &serialize_in,
                                   NValueArray &params, Pool *stringPool) {
    for (int i = 0; i < cnt; ++i) {
        params[i].deserializeFromAllocateForStorage(serialize_in, stringPool);
    }
}

VoltDBSHM::VoltDBSHM(AeronConnectionPair * cp)
    : m_engine(NULL)
    , m_counter(0)
    , m_cp(cp)
    , m_perFragmentStatsBuffer(NULL)
    , m_reusedResultBuffer(NULL)
    , m_exceptionBuffer(NULL)
    , m_udfBuffer(NULL)
    , m_terminate(false)
    , m_tupleBuffer(NULL)
    , m_tupleBufferSize(0)
{
    currentVolt = this;
    setupSigHandler();
}

VoltDBSHM::~VoltDBSHM() {
    if (m_engine != NULL) {
        delete m_engine;
        delete [] m_reusedResultBuffer;
        delete [] m_perFragmentStatsBuffer;
        delete [] m_udfBuffer;
        delete [] m_tupleBuffer;
        delete [] m_exceptionBuffer;
    }
}

bool VoltDBSHM::execute(struct ipc_command *cmd) {
    int8_t result = kErrorCode_None;

    if (staticDebugVerbose) {
        std::cout << "IPC client command: " << ntohl(cmd->command) << std::endl;
    }

    // commands must match java's ExecutionEngineIPC.Command
    // could enumerate but they're only used in this one place.
    switch (ntohl(cmd->command)) {
      case 0:
        result = initialize(cmd);
        break;
      case 2:
        result = loadCatalog(cmd);
        break;
      case 3:
        result = toggleProfiler(cmd);
        break;
      case 4:
        result = tick(cmd);
        break;
      case 5:
        getStats(cmd);
        result = kErrorCode_None;
        break;
      case 6:
        // also writes results directly
        executePlanFragments(cmd);
        result = kErrorCode_None;
        break;
      case 9:
        result = loadTable(cmd);
        break;
      case 10:
        result = releaseUndoToken(cmd);
        break;
      case 11:
        result = undoUndoToken(cmd);
        break;
      case 13:
        result = setLogLevels(cmd);
        break;
      case 16:
        result = quiesce(cmd);
        break;
      case 17:
        result = activateTableStream(cmd);
        break;
      case 18:
        tableStreamSerializeMore(cmd);
        result = kErrorCode_None;
        break;
      case 19:
        result = updateCatalog(cmd);
        break;
      case 20:
        setExportStreamPositions(cmd);
        result = kErrorCode_None;
        break;
      case 22:
          tableHashCode(cmd);
          result = kErrorCode_None;
          break;
      case 23:
          hashinate(cmd);
          result = kErrorCode_None;
          break;
      case 24:
          threadLocalPoolAllocations();
          result = kErrorCode_None;
          break;
      case 25:
          getUSOForExportTable(cmd);
          result = kErrorCode_None;
          break;
      case 27:
          updateHashinator(cmd);
          result = kErrorCode_None;
          break;
      case 28:
          executeTask(cmd);
          result = kErrorCode_None;
          break;
      case 29:
          applyBinaryLog(cmd);
          result = kErrorCode_None;
          break;
      case 30:
          shutDown();
          result = kErrorCode_Success;
          break;
      case 31:
          setViewsEnabled(cmd);
          result = kErrorCode_None;
          break;
      case 32:
          deleteMigratedRows(cmd);
          result = kErrorCode_None;
          break;
      case 35:
          storeTopicsGroup(cmd);
          break;
      case 36:
          deleteTopicsGroup(cmd);
          break;
      case 37:
          fetchTopicsGroups(cmd);
          break;
      case 38:
           commitTopicsGroupOffsets(cmd);
           break;
       case 39:
           fetchTopicsGroupOffsets(cmd);
           break;
       case 40:
           deleteExpiredTopicsOffsets(cmd);
           break;
       case 41:
           setReplicableTables(cmd);
           break;
       case 42:
           clearAllReplicableTables(cmd);
           break;
        case 43:
            clearReplicableTables(cmd);
            break;
      default:
        result = stub(cmd);
    }

    // write results for the simple commands. more
    // complex commands write directly in the command
    // implementation.
    if (result != kErrorCode_None) {
        if (result == kErrorCode_Error) {
            char msg[5];
            msg[0] = result;
            *reinterpret_cast<int32_t*>(&msg[1]) = 0;//exception length 0
            writeOrDie(m_cp, (unsigned char*)msg, sizeof(int8_t) + sizeof(int32_t));
        } else {
            writeOrDie(m_cp, (unsigned char*)&result, sizeof(int8_t));
        }
    }
    return m_terminate;
}

int8_t VoltDBSHM::stub(struct ipc_command *cmd) {
    printf("IPC command %d not implemented.\n", ntohl(cmd->command));
    fflush(stdout);
    return kErrorCode_Error;
}

int8_t VoltDBSHM::loadCatalog(struct ipc_command *cmd) {
    if (staticDebugVerbose) {
        std::cout << "loadCatalog" << std::endl;
    }
    vassert(m_engine);
    if (!m_engine)
        return kErrorCode_Error;

    catalog_load *msg = reinterpret_cast<catalog_load*>(cmd);
    try {
        if (m_engine->loadCatalog(ntohll(msg->timestamp), std::string(msg->data)) == true) {
            return kErrorCode_Success;
        }
    //TODO: FatalException and SerializableException should be universally caught and handled in "execute",
    // rather than in hard-to-maintain "execute method" boilerplate code like this.
    } catch (const FatalException& e) {
        crashVoltDB(e);
    } catch (const SerializableEEException &e) {} //TODO: We don't really want to quietly SQUASH non-fatal exceptions.

    return kErrorCode_Error;
}

int8_t VoltDBSHM::updateCatalog(struct ipc_command *cmd) {
    vassert(m_engine);
    update_catalog_cmd *uc = (update_catalog_cmd*) cmd;
    if (!m_engine) {
        return kErrorCode_Error;
    }

    try {
        if (m_engine->updateCatalog(ntohll(uc->timestamp), (uc->isStreamChange != 0), std::string(uc->data)) == true) {
            return kErrorCode_Success;
        }
    }
    catch (const SerializableEEException &e) {
        m_engine->resetReusedResultOutputBuffer();
        e.serialize(m_engine->getExceptionOutputSerializer());
    }
    catch (const FatalException &fe) {
        crashVoltDB(fe);
    }
    return kErrorCode_Error;
}

int8_t VoltDBSHM::initialize(struct ipc_command *cmd) {
    // expect a single initialization.
    vassert(!m_engine);
    delete m_engine;

    // voltdbengine::initialize expects catalogids.
    vassert(sizeof(CatalogId) == sizeof(int));

    struct initialize {
        struct ipc_command cmd;
        int clusterId;
        long siteId;
        int partitionId;
        int sitesPerHost;
        int hostId;
        int drClusterId;
        int defaultDrBufferSize;
        int drIgnoreConflicts;
        int32_t drCrcErrorIgnoreMax;
        int drCrcErrorIgnoreFatal;
        int64_t logLevels;
        int64_t tempTableMemory;
        int32_t isLowestSiteId;
        int32_t hostnameLength;
        char data[0];
    }__attribute__((packed));
    struct initialize * cs = (struct initialize*) cmd;

    if (staticDebugVerbose) {
        std::cout << "initialize: cluster=" << ntohl(cs->clusterId) <<
                ", site=" << ntohll(cs->siteId)  << std::endl;
    }
    cs->clusterId = ntohl(cs->clusterId);
    cs->siteId = ntohll(cs->siteId);
    cs->partitionId = ntohl(cs->partitionId);
    cs->sitesPerHost = ntohl(cs->sitesPerHost);
    cs->hostId = ntohl(cs->hostId);
    cs->drClusterId = ntohl(cs->drClusterId);
    cs->defaultDrBufferSize = ntohl(cs->defaultDrBufferSize);
    cs->drIgnoreConflicts = ntohl(cs->drIgnoreConflicts);
    cs->drCrcErrorIgnoreMax = ntohl(cs->drCrcErrorIgnoreMax);
    cs->logLevels = ntohll(cs->logLevels);
    cs->tempTableMemory = ntohll(cs->tempTableMemory);
    cs->isLowestSiteId = ntohl(cs->isLowestSiteId);
    bool isLowestSiteId = cs->isLowestSiteId != 0;
    bool drIgnoreConflicts = cs->drIgnoreConflicts != 0;
    bool drCrcErrorIgnoreFatal = cs->drCrcErrorIgnoreFatal != 0;
    cs->hostnameLength = ntohl(cs->hostnameLength);

    std::string hostname(cs->data, cs->hostnameLength);
    try {
        m_engine = new VoltDBEngine(this, new voltdb::StdoutLogProxy());
        m_engine->getLogManager()->setLogLevels(cs->logLevels);
        m_reusedResultBuffer = new char[MAX_MSG_SZ];
        m_perFragmentStatsBuffer = new char[MAX_MSG_SZ];
        m_udfBuffer = new char[MAX_MSG_SZ];
        std::memset(m_reusedResultBuffer, 0, MAX_MSG_SZ);
        m_exceptionBuffer = new char[MAX_MSG_SZ];
        m_engine->setBuffers(NULL, 0,
                             m_perFragmentStatsBuffer, MAX_MSG_SZ,
                             m_udfBuffer, MAX_MSG_SZ,
                             NULL, 0, // firstResultBuffer
                             m_reusedResultBuffer, MAX_MSG_SZ,
                             m_exceptionBuffer, MAX_MSG_SZ);
        // The tuple buffer gets expanded (doubled) as needed, but never compacted.
        m_tupleBufferSize = MAX_MSG_SZ;
        m_tupleBuffer = new char[m_tupleBufferSize];
        std::memset(m_tupleBuffer, 0, m_tupleBufferSize);
        m_engine->initialize(cs->clusterId,
                             cs->siteId,
                             cs->partitionId,
                             cs->sitesPerHost,
                             cs->hostId,
                             hostname,
                             cs->drClusterId,
                             cs->defaultDrBufferSize,
                             drIgnoreConflicts,
                             cs->drCrcErrorIgnoreMax,
                             drCrcErrorIgnoreFatal,
                             cs->tempTableMemory,
                             isLowestSiteId);
        return kErrorCode_Success;
    }
    catch (const FatalException &e) {
        crashVoltDB(e);
    }
    return kErrorCode_Error;
}

int8_t VoltDBSHM::toggleProfiler(struct ipc_command *cmd) {
    vassert(m_engine);
    if (!m_engine)
        return kErrorCode_Error;

    struct toggle {
        struct ipc_command cmd;
        int toggle;
    }__attribute__((packed));
    struct toggle * cs = (struct toggle*) cmd;

    if (staticDebugVerbose) {
        std::cout << "toggleProfiler: toggle=" << ntohl(cs->toggle) << std::endl;
    }

    // actually, the engine doesn't implement this now.
    // m_engine->ProfilerStart();
    return kErrorCode_Success;
}

int8_t VoltDBSHM::releaseUndoToken(struct ipc_command *cmd) {
    vassert(m_engine);
    if (!m_engine)
        return kErrorCode_Error;


    struct undo_token * cs = (struct undo_token*) cmd;
    bool isEmptyDRTxn = cs->isEmptyDRTxn > 0;

    try {
        m_engine->releaseUndoToken(ntohll(cs->token), isEmptyDRTxn);
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }

    return kErrorCode_Success;
}

int8_t VoltDBSHM::undoUndoToken(struct ipc_command *cmd) {
    vassert(m_engine);
    if (!m_engine)
        return kErrorCode_Error;


    struct undo_token * cs = (struct undo_token*) cmd;

    try {
        m_engine->undoUndoToken(ntohll(cs->token));
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }

    return kErrorCode_Success;
}

int8_t VoltDBSHM::tick(struct ipc_command *cmd) {
    vassert(m_engine);
    if (!m_engine)
        return kErrorCode_Error;

    struct tick {
        struct ipc_command cmd;
        int64_t time;
        int64_t lastSpHandle;
    }__attribute__((packed));

    struct tick * cs = (struct tick*) cmd;
    if (staticDebugVerbose) {
        std::cout << "tick: time=" << ntohll(cs->time) <<
                " sphandle=" << ntohll(cs->lastSpHandle) << std::endl;
    }
    try {
        // no return code. can't fail!
        m_engine->tick(ntohll(cs->time), ntohll(cs->lastSpHandle));
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }

    return kErrorCode_Success;
}

int8_t VoltDBSHM::quiesce(struct ipc_command *cmd) {
    struct quiesce {
        struct ipc_command cmd;
        int64_t lastSpHandle;
    }__attribute__((packed));

    struct quiesce *cs = (struct quiesce*)cmd;

    try {
        m_engine->quiesce(ntohll(cs->lastSpHandle));
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }

    return kErrorCode_Success;
}

void VoltDBSHM::executePlanFragments(struct ipc_command *cmd) {
    int errors = 0;

    querypfs *queryCommand = (querypfs*) cmd;

    int32_t numFrags = ntohl(queryCommand->numFragmentIds);

    if (staticDebugVerbose) {
        std::cout << "querypfs:"
                  << " spHandle=" << ntohll(queryCommand->spHandle)
                  << " lastCommittedSphandle=" << ntohll(queryCommand->lastCommittedSpHandle)
                  << " undoToken=" << ntohll(queryCommand->undoToken)
                  << " numFragIds=" << numFrags << std::endl;
    }

    // data has binary packed fragmentIds first
    int64_t *fragmentIds = (int64_t*) (&(queryCommand->data));
    int64_t *inputDepIds = fragmentIds + numFrags;

    // fix network byte order
    for (int i = 0; i < numFrags; ++i) {
        fragmentIds[i] = ntohll(fragmentIds[i]);
        inputDepIds[i] = ntohll(inputDepIds[i]);
    }

    // ...and fast serialized parameter sets last.
    void* offset = queryCommand->data + (sizeof(int64_t) * numFrags * 2);
    int sz = static_cast<int> (ntohl(cmd->msgsize) - sizeof(querypfs) - sizeof(int32_t) * ntohl(queryCommand->numFragmentIds));
    ReferenceSerializeInputBE serialize_in(offset, sz);

    // and reset to space for the results output
    m_engine->resetReusedResultOutputBuffer(1); // 1 byte to add status code
    // We can't update the result from getResultsBuffer (which may use the failoverBuffer)
    m_reusedResultBuffer[0] = kErrorCode_Success;
    m_engine->resetPerFragmentStatsOutputBuffer(queryCommand->perFragmentTimingEnabled);

    try {
        errors = m_engine->executePlanFragments(numFrags,
                                                fragmentIds,
                                                inputDepIds,
                                                serialize_in,
                                                ntohll(queryCommand->txnId),
                                                ntohll(queryCommand->spHandle),
                                                ntohll(queryCommand->lastCommittedSpHandle),
                                                ntohll(queryCommand->uniqueId),
                                                ntohll(queryCommand->undoToken),
                                                false);
    }
    catch (const FatalException &e) {
        crashVoltDB(e);
    }

    sendPerFragmentStatsBuffer();

    // write the results array back across the wire
    if (errors == 0) {
        // write the results array back across the wire
        const int32_t size = m_engine->getResultsSize();
        writeOrDie(m_cp, m_engine->getResultsBuffer(), size);
    } else {
        sendException(kErrorCode_Error);
    }
}

void VoltDBSHM::setViewsEnabled(struct ipc_command *cmd) {
    set_views_enabled* setViewsEnabledCommand = (set_views_enabled*) cmd;
    bool enabled = setViewsEnabledCommand->enabled > 0;
    m_engine->setViewsEnabled(std::string(setViewsEnabledCommand->viewNameBytes), enabled);
}

void VoltDBSHM::sendPerFragmentStatsBuffer() {
    int8_t statusCode = static_cast<int8_t>(kErrorCode_pushPerFragmentStatsBuffer);
    writeOrDie(m_cp, (unsigned char*)&statusCode, sizeof(int8_t), true);
    // write the per-fragment stats back across the wire
    char *perFragmentStatsBuffer = m_engine->getPerFragmentStatsBuffer();
    int32_t perFragmentStatsBufferSizeToSend = htonl(m_engine->getPerFragmentStatsSize());
    writeOrDie(m_cp, (unsigned char*)&perFragmentStatsBufferSizeToSend, sizeof(int32_t), true);
    writeOrDie(m_cp, (unsigned char*)perFragmentStatsBuffer, m_engine->getPerFragmentStatsSize());
}

void checkBytesRead(ssize_t byteCountExpected, ssize_t byteCountRead, std::string description) {
    if (byteCountRead != byteCountExpected) {
        printf("Error - blocking read of %s failed. %jd read %jd attempted",
                description.c_str(), (intmax_t)byteCountRead, (intmax_t)byteCountExpected);
        fflush(stdout);
        vassert(false);
        exit(-1);
    }
}

int VoltDBSHM::callJavaUserDefinedHelper(int kErrorCode) {
    // Send a special status code indicating that a UDF invocation request is coming on the wire.
    int8_t statusCode = static_cast<int8_t>(kErrorCode);
    writeOrDie(m_cp, (unsigned char*)&statusCode, sizeof(int8_t), true);

    // Get the UDF buffer size.
    int32_t* udfBufferInInt32 = reinterpret_cast<int32_t*>(m_udfBuffer);
    int32_t udfBufferSizeToSend = ntohl(*udfBufferInInt32);
    // Send the whole UDF buffer to the wire.
    // Note that the number of bytes we sent includes the bytes for storing the buffer size.
    writeOrDie(m_cp, (unsigned char*)m_udfBuffer, sizeof(udfBufferSizeToSend) + udfBufferSizeToSend);

    // Wait for the UDF result.

    int32_t retval, udfBufferSizeToRecv;
    // read buffer length
    ssize_t bytes = read(m_cp, &udfBufferSizeToRecv, sizeof(int32_t));
    checkBytesRead(sizeof(int32_t), bytes, "UDF return value buffer size");
    // The buffer size should exclude the size of the buffer size value
    // and the returning status code value (2 * sizeof(int32_t)).
    udfBufferSizeToRecv = ntohl(udfBufferSizeToRecv) - 2 * sizeof(int32_t);

    // read return value, 0 means success, failure otherwise.
    bytes = read(m_cp, &retval, sizeof(int32_t));
    checkBytesRead(sizeof(int32_t), bytes, "UDF execution return code");
    retval = ntohl(retval);

    // read buffer content, includes the return value of the UDF.
    bytes = read(m_cp, m_udfBuffer, udfBufferSizeToRecv);
    checkBytesRead(udfBufferSizeToRecv, bytes, "UDF return value buffer content");
    return retval;
}

int VoltDBSHM::callJavaUserDefinedFunction() {
    return callJavaUserDefinedHelper(kErrorCode_callJavaUserDefinedFunction);
}

int VoltDBSHM::callJavaUserDefinedAggregateStart(int functionId) {
    ReferenceSerializeOutput udfOutput(m_udfBuffer, MAX_MSG_SZ);
    udfOutput.writeInt(sizeof(functionId));
    udfOutput.writeInt(functionId);
    return callJavaUserDefinedHelper(kErrorCode_callJavaUserDefinedAggregateStart);
}

int VoltDBSHM::callJavaUserDefinedAggregateAssemble() {
    return callJavaUserDefinedHelper(kErrorCode_callJavaUserDefinedAggregateAssemble);
}

int VoltDBSHM::callJavaUserDefinedAggregateCombine() {
    return callJavaUserDefinedHelper(kErrorCode_callJavaUserDefinedAggregateCombine);
}

int VoltDBSHM::callJavaUserDefinedAggregateWorkerEnd() {
    return callJavaUserDefinedHelper(kErrorCode_callJavaUserDefinedAggregateWorkerEnd);
}

int VoltDBSHM::callJavaUserDefinedAggregateCoordinatorEnd() {
    return callJavaUserDefinedHelper(kErrorCode_callJavaUserDefinedAggregateCoordinatorEnd);
}

bool VoltDBSHM::sendResponseOrException(uint8_t errorCode) {
    if (errorCode) {
        sendException(errorCode);
        return true;
    }
    writeOrDie(m_cp, (unsigned char*)&errorCode, sizeof(int8_t));
    return false;
}

void VoltDBSHM::sendException(int8_t errorCode) {
    writeOrDie(m_cp, (unsigned char*)&errorCode, sizeof(int8_t));

    const void* exceptionData =
      m_engine->getExceptionOutputSerializer()->data();
    int32_t exceptionLength =
      static_cast<int32_t>(ntohl(*reinterpret_cast<const int32_t*>(exceptionData)));
    if (staticDebugVerbose) {
        std::cout << "Sending exception length " << exceptionLength << std::endl;
    }
    fflush(stdout);

    const std::size_t expectedSize = exceptionLength + sizeof(int32_t);
    writeOrDie(m_cp, (const unsigned char*)exceptionData, expectedSize);
}

int8_t VoltDBSHM::loadTable(struct ipc_command *cmd) {
    load_table_cmd *loadTableCommand = (load_table_cmd*) cmd;

    if (staticDebugVerbose) {
        std::cout << "loadTable:" << " tableId=" << ntohl(loadTableCommand->tableId)
                  << " spHandle=" << ntohll(loadTableCommand->spHandle) << " lastCommittedSpHandle="
                  << ntohll(loadTableCommand->lastCommittedSpHandle) << std::endl;
    }

    const int32_t tableId = ntohl(loadTableCommand->tableId);
    const int64_t txnId = ntohll(loadTableCommand->txnId);
    const int64_t spHandle = ntohll(loadTableCommand->spHandle);
    const int64_t lastCommittedSpHandle = ntohll(loadTableCommand->lastCommittedSpHandle);
    const int64_t uniqueId = ntohll(loadTableCommand->uniqueId);
    const int64_t undoToken = ntohll(loadTableCommand->undoToken);
    const LoadTableCaller &caller = LoadTableCaller::get(static_cast<LoadTableCaller::Id>(loadTableCommand->callerId));
    // ...and fast serialized table last.
    void* offset = loadTableCommand->data;
    int sz = static_cast<int> (ntohl(cmd->msgsize) - sizeof(load_table_cmd));
    try {
        ReferenceSerializeInputBE serialize_in(offset, sz);

        bool success = m_engine->loadTable(tableId, serialize_in,
                                           txnId, spHandle, lastCommittedSpHandle, uniqueId, undoToken, caller);
        if (success) {
            return kErrorCode_Success;
        } else {
            return kErrorCode_Error;
        }
    } catch (const SerializableEEException &see) {
        return kErrorCode_Error;
    }
    catch (const FatalException &e) {
        crashVoltDB(e);
    }
    return kErrorCode_Error;
}

int8_t VoltDBSHM::setLogLevels(struct ipc_command *cmd) {
    int64_t logLevels = *((int64_t*)&cmd->data[0]);
    try {
        m_engine->getLogManager()->setLogLevels(logLevels);
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
    return kErrorCode_Success;
}

void VoltDBSHM::shutDown() {
    m_terminate = true;
}

int VoltDBSHM::loadNextDependency(int32_t dependencyId, voltdb::Pool *stringPool, Table* destination) {
    if (staticDebugVerbose) {
        std::cout << "iterating java dependency for id " << dependencyId << std::endl;
    }
    size_t dependencySz;
    char* buf = retrieveDependency(dependencyId, &dependencySz);
    char *origBuf = buf;

    if (!buf) {
        return 0;
    }

    if (dependencySz > 0) {
        ReferenceSerializeInputBE serialize_in(buf, dependencySz);
        destination->loadTuplesFrom(serialize_in, stringPool);
        delete [] origBuf;
        return 1;
    }
    else {
        delete [] origBuf;
        return 0;
    }
}

/**
 * Retrieve a dependency from Java via the IPC connection.
 * This method returns null if there are no more dependency tables. Otherwise
 * it returns a pointer to a buffer containing the dependency. The first four bytes
 * of the buffer is an int32_t length prefix.
 *
 * The returned allocated memory must be freed by the caller.
 */
char *VoltDBSHM::retrieveDependency(int32_t dependencyId, size_t *dependencySz) {
    char message[5];
    *dependencySz = 0;

    // tell java to send the dependency over the socket
    message[0] = static_cast<int8_t>(kErrorCode_RetrieveDependency);
    *reinterpret_cast<int32_t*>(&message[1]) = htonl(dependencyId);
    writeOrDie(m_cp, (unsigned char*)message, sizeof(int8_t) + sizeof(int32_t));

    // read java's response code
    int8_t responseCode;
    ssize_t bytes = read(m_cp, &responseCode, sizeof(int8_t));
    if (bytes != sizeof(int8_t)) {
        printf("Error - blocking read failed. %jd read %jd attempted",
                (intmax_t)bytes, (intmax_t)sizeof(int8_t));
        fflush(stdout);
        vassert(false);
        exit(-1);
    }

    // deal with error response codes
    if (kErrorCode_DependencyNotFound == responseCode) {
        return NULL;
    } else if (kErrorCode_DependencyFound != responseCode) {
        printf("Received unexpected response code %d to retrieve dependency request\n",
                (int)responseCode);
        fflush(stdout);
        vassert(false);
        exit(-1);
    }

    // start reading the dependency. its length is first
    int32_t dependencyLength;
    bytes = read(m_cp, &dependencyLength, sizeof(int32_t));
    if (bytes != sizeof(int32_t)) {
        printf("Error - blocking read failed. %jd read %jd attempted",
                (intmax_t)bytes, (intmax_t)sizeof(int32_t));
        fflush(stdout);
        vassert(false);
        exit(-1);
    }

    bytes = 0;
    dependencyLength = ntohl(dependencyLength);
    *dependencySz = (size_t)dependencyLength;
    char *dependencyData = new char[dependencyLength];
    while (bytes != dependencyLength) {
        ssize_t oldBytes = bytes;
        bytes += read(m_cp, dependencyData + bytes, dependencyLength - bytes);
        if (oldBytes == bytes) {
            break;
        }
        if (oldBytes > bytes) {
            bytes++;
            break;
        }
    }

    if (bytes != dependencyLength) {
        printf("Error - blocking read failed. %jd read %jd attempted",
                (intmax_t)bytes, (intmax_t)dependencyLength);
        fflush(stdout);
        vassert(false);
        exit(-1);
    }
    return dependencyData;
}

// A file static helper function that
//   Reads a 4-byte integer from fd that is the length of the following string
//   Reads the bytes for the string
//   Returns those bytes as an std::string
static std::string readLengthPrefixedBytesToStdString(AeronConnectionPair * cp) {
    int32_t length;
    ssize_t numBytesRead = read(cp, &length, sizeof(int32_t));
    checkBytesRead(sizeof(int32_t), numBytesRead, "plan bytes length");
    length = static_cast<int32_t>(ntohl(length) - sizeof(int32_t));
    vassert(length > 0);

    boost::scoped_array<char> bytes(new char[length + 1]);
    numBytesRead = 0;
    while (numBytesRead != length) {
        ssize_t oldBytes = numBytesRead;
        numBytesRead += read(cp, bytes.get() + numBytesRead, length - numBytesRead);
        if (oldBytes == numBytesRead) {
            break;
        }
        if (oldBytes > numBytesRead) {
            numBytesRead++;
            break;
        }
    }

    checkBytesRead(length, numBytesRead, "plan bytes");

    // null terminate
    bytes[length] = '\0';

    // need to return a string
    return std::string(bytes.get());

}

std::string VoltDBSHM::decodeBase64AndDecompress(const std::string& base64Data) {
    const size_t messageSize = sizeof(int8_t) + sizeof(int32_t) + base64Data.size();
    unsigned char message[messageSize];
    size_t offset = 0;

    message[0] = static_cast<int8_t>(kErrorCode_decodeBase64AndDecompress);
    offset++;

    *reinterpret_cast<int32_t*>(&message[offset]) = htonl(static_cast<int32_t>(base64Data.size()));
    offset += sizeof(int32_t);

    ::memcpy(&message[offset], base64Data.c_str(), base64Data.size());

    writeOrDie(m_cp, message, messageSize);

    return readLengthPrefixedBytesToStdString(m_cp);
}

std::string VoltDBSHM::planForFragmentId(int64_t fragmentId) {
    char message[sizeof(int8_t) + sizeof(int64_t)];
    message[0] = static_cast<int8_t>(kErrorCode_needPlan);
    *reinterpret_cast<int64_t*>(&message[1]) = htonll(fragmentId);
    writeOrDie(m_cp, (unsigned char*)message, sizeof(int8_t) + sizeof(int64_t));
    return readLengthPrefixedBytesToStdString(m_cp);
}

static bool progressUpdateDisabled = true;

int64_t VoltDBSHM::fragmentProgressUpdate(
        int32_t batchIndex,
        voltdb::PlanNodeType planNodeType,
        int64_t tuplesProcessed,
        int64_t currMemoryInBytes,
        int64_t peakMemoryInBytes) {
    if (progressUpdateDisabled) {
        // Large value cuts down on future calls to this stub method.
        return 1000000;
    }

    int32_t nodeTypeAsInt32 = static_cast<int32_t>(planNodeType);
    char message[sizeof(int8_t) +
                 sizeof(batchIndex) +
                 sizeof(nodeTypeAsInt32) +
                 sizeof(tuplesProcessed) +
                 sizeof(currMemoryInBytes) +
                 sizeof(peakMemoryInBytes)];
    message[0] = static_cast<int8_t>(kErrorCode_progressUpdate);
    size_t offset = 1;

    *reinterpret_cast<int32_t*>(&message[offset]) = htonl(batchIndex);
    offset += sizeof(batchIndex);

    *reinterpret_cast<int32_t*>(&message[offset]) = htonl(nodeTypeAsInt32);
    offset += sizeof(nodeTypeAsInt32);

    *reinterpret_cast<int64_t*>(&message[offset]) = htonll(tuplesProcessed);
    offset += sizeof(tuplesProcessed);

    *reinterpret_cast<int64_t*>(&message[offset]) = htonll(currMemoryInBytes);
    offset += sizeof(currMemoryInBytes);

    *reinterpret_cast<int64_t*>(&message[offset]) = htonll(peakMemoryInBytes);
    offset += sizeof(peakMemoryInBytes);

    if (staticDebugVerbose) {
        std::cout << "Writing progress update " << (int)*message << std::endl;
    }
    writeOrDie(m_cp, (unsigned char*)message, offset);
    if (staticDebugVerbose) {
        std::cout << "Wrote progress update" << std::endl;
    }

    int64_t nextStep;
    ssize_t bytes = read(m_cp, &nextStep, sizeof(nextStep));
    if (bytes != sizeof(nextStep)) {
        printf("Error - blocking read after progress update failed. %jd read %jd attempted",
                (intmax_t)bytes, (intmax_t)sizeof(nextStep));
        fflush(stdout);
        vassert(false);
        exit(-1);
    }
    if (staticDebugVerbose) {
        std::cout << "Resuming after progress update nextStep = " << ntohll(nextStep) << std::endl;
    }
    nextStep = ntohll(nextStep);

    return nextStep;
}

void VoltDBSHM::crashVoltDB(voltdb::FatalException e) {
    const char *reasonBytes = e.m_reason.c_str();
    int32_t reasonLength = static_cast<int32_t>(strlen(reasonBytes));
    int32_t lineno = static_cast<int32_t>(e.m_lineno);
    int32_t filenameLength = static_cast<int32_t>(strlen(e.m_filename));
    int32_t numTraces = static_cast<int32_t>(e.m_traces.size());
    int32_t totalTracesLength = 0;
    for (int ii = 0; ii < static_cast<int>(e.m_traces.size()); ii++) {
        totalTracesLength += static_cast<int32_t>(strlen(e.m_traces[ii].c_str()));
    }
    //sizeof traces text + length prefix per trace, length prefix of reason string, number of traces count,
    //filename length, lineno
    int32_t messageLength =
            static_cast<int32_t>(
                    totalTracesLength +
                    (sizeof(int32_t) * numTraces) +
                    (sizeof(int32_t) * 4) +
                    reasonLength +
                    filenameLength);

    //status code
    m_reusedResultBuffer[0] = static_cast<char>(kErrorCode_CrashVoltDB);
    size_t position = 1;

    //overall message length, not included in messageLength
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[position]) = htonl(messageLength);
    position += sizeof(int32_t);

    //reason string
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[position]) = htonl(reasonLength);
    position += sizeof(int32_t);
    memcpy( &m_reusedResultBuffer[position], reasonBytes, reasonLength);
    position += reasonLength;

    //filename string
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[position]) = htonl(filenameLength);
    position += sizeof(int32_t);
    memcpy( &m_reusedResultBuffer[position], e.m_filename, filenameLength);
    position += filenameLength;

    //lineno
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[position]) = htonl(lineno);
    position += sizeof(int32_t);

    //number of traces
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[position]) = htonl(numTraces);
    position += sizeof(int32_t);

    for (int ii = 0; ii < static_cast<int>(e.m_traces.size()); ii++) {
        int32_t traceLength = static_cast<int32_t>(strlen(e.m_traces[ii].c_str()));
        *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[position]) = htonl(traceLength);
        position += sizeof(int32_t);
        memcpy( &m_reusedResultBuffer[position], e.m_traces[ii].c_str(), traceLength);
        position += traceLength;
    }

    writeOrDie(m_cp,  (unsigned char*)m_reusedResultBuffer, 5 + messageLength);
    exit(-1);
}

void VoltDBSHM::getStats(struct ipc_command *cmd) {
    get_stats_cmd *getStatsCommand = (get_stats_cmd*) cmd;

    const int32_t selector = ntohl(getStatsCommand->selector);
    const int32_t numLocators = ntohl(getStatsCommand->num_locators);
    bool interval = false;
    if (getStatsCommand->interval != 0) {
        interval = true;
    }
    const int64_t now = ntohll(getStatsCommand->now);
    int32_t *locators = new int32_t[numLocators];
    for (int ii = 0; ii < numLocators; ii++) {
        locators[ii] = ntohl(getStatsCommand->locators[ii]);
    }

    m_engine->resetReusedResultOutputBuffer();

    try {
        int result = m_engine->getStats(
                static_cast<int>(selector),
                locators,
                numLocators,
                interval,
                now);

        delete [] locators;

        // write the results array back across the wire
        const int8_t successResult = kErrorCode_Success;
        if (result == 0 || result == 1) {
            writeOrDie(m_cp, (const unsigned char*)&successResult, sizeof(int8_t), true);

            if (result == 1) {
                const int32_t size = m_engine->getResultsSize();
                // write the dependency tables back across the wire
                // the result set includes the total serialization size
                writeOrDie(m_cp, m_engine->getResultsBuffer(), size);
            }
            else {
                int32_t zero = 0;
                writeOrDie(m_cp, (const unsigned char*)&zero, sizeof(int32_t));
            }
        } else {
            sendException(kErrorCode_Error);
        }
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

int8_t VoltDBSHM::activateTableStream(struct ipc_command *cmd) {
    activate_tablestream *activateTableStreamCommand = (activate_tablestream*) cmd;
    const voltdb::CatalogId tableId = ntohl(activateTableStreamCommand->tableId);
    const voltdb::TableStreamType streamType =
            static_cast<voltdb::TableStreamType>(ntohl(activateTableStreamCommand->streamType));

    // Provide access to the serialized message data, i.e. the predicates.
    void* offset = activateTableStreamCommand->data;
    int sz = static_cast<int> (ntohl(cmd->msgsize) - sizeof(activate_tablestream));
    int64_t undoToken = ntohll(activateTableStreamCommand->undoToken);
    ReferenceSerializeInputBE serialize_in(offset, sz);

    try {
        if (m_engine->activateTableStream(tableId, streamType, activateTableStreamCommand->hiddenColumnFilterType,
                undoToken, serialize_in)) {
            return kErrorCode_Success;
        } else {
            return kErrorCode_Error;
        }
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
    return kErrorCode_Error;
}

void VoltDBSHM::tableStreamSerializeMore(struct ipc_command *cmd) {
    tablestream_serialize_more *tableStreamSerializeMore = (tablestream_serialize_more*) cmd;
    const voltdb::CatalogId tableId = ntohl(tableStreamSerializeMore->tableId);
    const voltdb::TableStreamType streamType =
            static_cast<voltdb::TableStreamType>(ntohl(tableStreamSerializeMore->streamType));
    // Need to adapt the simpler incoming data describing buffers to conform to
    // what VoltDBEngine::tableStreamSerializeMore() needs. The incoming data
    // is an array of buffer lengths. The outgoing data must be an array of
    // ptr/offset/length triplets referencing segments of m_tupleBuffer, which
    // is reallocated as needed.
    const int bufferCount = ntohl(tableStreamSerializeMore->bufferCount);
    try {

        if (bufferCount <= 0) {
            throwFatalException("Bad buffer count in tableStreamSerializeMore: %d", bufferCount);
        }

        // Need two passes, one to determine size, the other to populate buffer
        // data. Can't do this until the base buffer is properly allocated.
        // Note that m_reusedResultBuffer is used for input data and
        // m_tupleBuffer is used for output data.

        void *inptr = tableStreamSerializeMore->data;
        int sz = static_cast<int> (ntohl(cmd->msgsize) - sizeof(tablestream_serialize_more));
        ReferenceSerializeInputBE in1(inptr, sz);

        // Pass 1 - calculate size and allow for status code byte and count length integers.
        size_t outputSize = 1 + sizeof(int32_t) + sizeof(int64_t); // status code + buffercount + remaining
        for (size_t i = 0; i < bufferCount; i++) {
            in1.readLong(); in1.readInt(); // skip address and offset, used for jni only
            outputSize += in1.readInt() + 4;
        }

        // Reallocate buffer as needed.
        // Avoid excessive thrashing by over-allocating in powers of 2.
        if (outputSize > m_tupleBufferSize) {
            while (outputSize > m_tupleBufferSize) {
                m_tupleBufferSize *= 2;
            }
            delete [] m_tupleBuffer;
            m_tupleBuffer = new char[m_tupleBufferSize];
        }

        // Pass 2 - rescan input stream and generate final buffer data.
        ReferenceSerializeInputBE in2(inptr, sz);
        // 1 byte status and 4 byte count
        size_t offset = 5;
        ReferenceSerializeOutput out1(m_reusedResultBuffer, MAX_MSG_SZ);
        out1.writeInt(bufferCount);
        for (size_t i = 0; i < bufferCount; i++) {
            in2.readLong(); in2.readInt(); // skip address and offset, used for jni only
            int length = in2.readInt();
            out1.writeLong((long)m_tupleBuffer);
            // Allow for the length int written later.
            offset += sizeof(int);
            out1.writeInt(static_cast<int>(offset));
            out1.writeInt(length);
            offset += length;
        }

        // Perform table stream serialization.
        ReferenceSerializeInputBE out2(m_reusedResultBuffer, MAX_MSG_SZ);
        std::vector<int> positions;
        int64_t remaining = m_engine->tableStreamSerializeMore(tableId, streamType, out2, positions);

        // Finalize the tuple buffer by adding the status code, buffer count,
        // and remaining tuple count.
        // Inject positions (lengths) into previously skipped int-size gaps.
        m_tupleBuffer[0] = kErrorCode_Success;
        *reinterpret_cast<int32_t*>(&m_tupleBuffer[1]) = htonl(bufferCount);
        offset = 1 + sizeof(int32_t);
        *reinterpret_cast<int64_t*>(&m_tupleBuffer[offset]) = htonll(remaining);
        offset += sizeof(int64_t);
        // output position when success (including finished)
        if (remaining >= 0) {
            std::vector<int>::const_iterator ipos;
            for (ipos = positions.begin(); ipos != positions.end(); ++ipos) {
                int length = *ipos;
                *reinterpret_cast<int32_t*>(&m_tupleBuffer[offset]) = htonl(length);
                offset += length + sizeof(int32_t);
            }
        }
        if (remaining <= 0) {
            // If we failed or finished, we've set the count, so stop right there.
            outputSize = offset;
        }
        // Ship it.
        writeOrDie(m_cp, (unsigned char*)m_tupleBuffer, outputSize);

    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::tableHashCode( struct ipc_command *cmd) {
    table_hash_code *hashCodeRequest = (table_hash_code*) cmd;
    const int32_t tableId = ntohl(hashCodeRequest->tableId);
    int64_t tableHashCode = m_engine->tableHashCode(tableId);
    char response[9];
    response[0] = kErrorCode_Success;
    *reinterpret_cast<int64_t*>(&response[1]) = htonll(tableHashCode);
    writeOrDie(m_cp, (unsigned char*)response, 9);
}

void VoltDBSHM::setExportStreamPositions(struct ipc_command *cmd) {
    export_positions *action = (export_positions*)cmd;

    m_engine->resetReusedResultOutputBuffer();
    int32_t tableSignatureLength = ntohl(action->tableSignatureLength);
    std::string tableSignature(action->tableSignature, tableSignatureLength);
    m_engine->setExportStreamPositions(static_cast<int64_t>(ntohll(action->offset)),
                                       static_cast<int64_t>(ntohll(action->seqNo)),
                                       static_cast<int64_t>(ntohll(action->genId)),
                                       tableSignature);

    uint8_t success = 0;
    writeOrDie(m_cp, (unsigned char*)&success, sizeof(success));
}

void VoltDBSHM::deleteMigratedRows(struct ipc_command *cmd) {
    delete_migrated_rows *migrate_msg = (delete_migrated_rows *)cmd;
    m_engine->resetReusedResultOutputBuffer();
    int32_t tableNameLength = ntohl(migrate_msg->tableNameLength);
    std::string tableName(migrate_msg->tableName, tableNameLength);
    bool result = m_engine->deleteMigratedRows(static_cast<int64_t>(ntohll(migrate_msg->txnId)),
                                               static_cast<int64_t>(ntohll(migrate_msg->spHandle)),
                                               static_cast<int64_t>(ntohll(migrate_msg->uniqueId)),
                                               tableName,
                                               static_cast<int64_t>(ntohll(migrate_msg->deletableTxnId)),
                                               static_cast<int64_t>(ntohll(migrate_msg->undoToken)));
    char response[1];
    response[0] = result ? 1 : 0;
    writeOrDie(m_cp, (unsigned char*)response, sizeof(int8_t));
}

void VoltDBSHM::storeTopicsGroup(struct ipc_command *cmd) {
    store_topics_group *msg = (store_topics_group*) cmd;
    ReferenceSerializeInputBE in(msg->groupData, static_cast<int32_t>(ntohl(msg->groupDataLength)));
    try {
        sendResponseOrException((uint8_t) m_engine->storeTopicsGroup(ntohll(msg->undoToken), in));
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::deleteTopicsGroup(struct ipc_command *cmd) {
    delete_topics_group *msg = (delete_topics_group*) cmd;

    NValue groupId = ValueFactory::getTempStringValue(msg->groupId, static_cast<int32_t>(ntohl(msg->groupIdLength)));

    try {
        sendResponseOrException((uint8_t) m_engine->deleteTopicsGroup(ntohll(msg->undoToken), groupId));
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::fetchTopicsGroups(struct ipc_command *cmd) {
    fetch_topics_groups *msg = (fetch_topics_groups*) cmd;
    NValue startGroupId = ValueFactory::getTempStringValue(msg->startGroupId,
            static_cast<int32_t>(ntohl(msg->groupIdLength)));

    try {
        int32_t result = m_engine->fetchTopicsGroups(static_cast<int32_t>(ntohl(msg->maxResultSize)), startGroupId);
        uint8_t response = result < 0 ? 1 : 0;
        if (sendResponseOrException(response)) {
            return;
        }

        response = result > 0;
        writeOrDie(m_cp, &response, sizeof(uint8_t), true);
        const int32_t size = m_engine->getResultsSize();
        writeOrDie(m_cp, m_engine->getResultsBuffer(), size);
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::commitTopicsGroupOffsets(struct ipc_command *cmd) {
    commit_topics_group_offsets *msg = (commit_topics_group_offsets*) cmd;

    int32_t groupIdLength = static_cast<int32_t>(ntohl(msg->groupIdLength));
    NValue groupId = ValueFactory::getTempStringValue(msg->data, groupIdLength);
    ReferenceSerializeInputBE in(&msg->data[groupIdLength], static_cast<int32_t>(ntohl(msg->offsetsLength)));

    try {
        uint8_t result = (uint8_t) m_engine->commitTopicsGroupOffsets(
                ntohll(msg->uniqueId), ntohll(msg->undoToken), static_cast<int16_t>(ntohs(msg->requestVersion)), groupId, in);

        if (sendResponseOrException(result)) {
            return;
        }
        const int32_t size = m_engine->getResultsSize();
        writeOrDie(m_cp, m_engine->getResultsBuffer(), size);
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::fetchTopicsGroupOffsets(struct ipc_command *cmd) {
    fetch_topics_group_offsets *msg = (fetch_topics_group_offsets*) cmd;

    int32_t groupIdLength = static_cast<int32_t>(ntohl(msg->groupIdLength));
    NValue groupId = ValueFactory::getTempStringValue(msg->data, groupIdLength);
    ReferenceSerializeInputBE in(&msg->data[groupIdLength], static_cast<int32_t>(ntohl(msg->offsetsLength)));

    try {
        uint8_t result = (uint8_t) m_engine->fetchTopicsGroupOffsets(static_cast<int16_t>(ntohs(msg->requestVersion)),
                groupId, in);

        if (sendResponseOrException(result)) {
            return;
        }
        const int32_t size = m_engine->getResultsSize();
        writeOrDie(m_cp, m_engine->getResultsBuffer(), size);
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::deleteExpiredTopicsOffsets(struct ipc_command *cmd) {
    delete_expired_topics_offsets *msg = (delete_expired_topics_offsets*) cmd;
    try {
        sendResponseOrException(
                (uint8_t) m_engine->deleteExpiredTopicsOffsets(ntohll(msg->undoToken), ntohll(msg->deleteOlderThan)));
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::getUSOForExportTable(struct ipc_command *cmd) {
    get_uso *get = (get_uso*)cmd;

    m_engine->resetReusedResultOutputBuffer();
    int32_t streamNameLength = ntohl(get->streamNameLength);
    std::string streamNameStr(get->streamName, streamNameLength);

    size_t ackOffset;
    int64_t seqNo;
    int64_t genId;
    m_engine->getUSOForExportTable(ackOffset, seqNo, genId, streamNameStr);

    // write offset across bigendian.
    int64_t ackOffsetI64 = static_cast<int64_t>(ackOffset);
    ackOffsetI64 = htonll(ackOffsetI64);
    writeOrDie(m_cp, (unsigned char*)&ackOffsetI64, sizeof(ackOffsetI64), true);

    // write the poll data. It is at least 4 bytes of length prefix.
    seqNo = htonll(seqNo);
    writeOrDie(m_cp, (unsigned char*)&seqNo, sizeof(seqNo), true);

    genId = htonll(genId);
    writeOrDie(m_cp, (unsigned char*)&genId, sizeof(genId));
}

void VoltDBSHM::hashinate(struct ipc_command* cmd) {
    hashinate_msg* hash = (hashinate_msg*)cmd;
    NValueArray& params = m_engine->getExecutorContext()->getParameterContainer();

    int32_t configLength = ntohl(hash->configLength);
    boost::scoped_ptr<TheHashinator> hashinator;
    hashinator.reset(ElasticHashinator::newInstance(hash->data, NULL, 0));
    void* offset = hash->data + configLength;
    int sz = static_cast<int> (ntohl(cmd->msgsize) - sizeof(hash));
    ReferenceSerializeInputBE serialize_in(offset, sz);

    int retval = -1;
    try {
        int cnt = serialize_in.readShort();
        vassert(cnt> -1);
        Pool *pool = m_engine->getStringPool();
        deserializeParameterSetCommon(cnt, serialize_in, params, pool);
        retval =
            hashinator->hashinate(params[0]);
        pool->purge();
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }

    char response[5];
    response[0] = kErrorCode_Success;
    *reinterpret_cast<int32_t*>(&response[1]) = htonl(retval);
    writeOrDie(m_cp, (unsigned char*)response, 5);
}

void VoltDBSHM::updateHashinator(struct ipc_command *cmd) {
    hashinate_msg* hash = (hashinate_msg*)cmd;
    try {
        m_engine->updateHashinator(hash->data, NULL, 0);
    } catch (const FatalException &e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::signalHandler(int signum, siginfo_t *info, void *context) {
    char err_msg[128];
    snprintf(err_msg, 128, "SIGSEGV caught: signal number %d, error value %d,"
             " signal code %d\n\n", info->si_signo, info->si_errno,
             info->si_code);
    err_msg[sizeof err_msg - 1] = '\0';
    std::string message = err_msg;
    message.append(m_engine->debug());
    crashVoltDB(SegvException(message.c_str(), context, __FILE__, __LINE__));
}

void VoltDBSHM::signalDispatcher(int signum, siginfo_t *info, void *context) {
    if (currentVolt != NULL)
        currentVolt->signalHandler(signum, info, context);
}

void VoltDBSHM::setupSigHandler(void) const {
#if !defined(MEMCHECK)
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_sigaction = VoltDBSHM::signalDispatcher;
    action.sa_flags = SA_SIGINFO;
    if(sigaction(SIGSEGV, &action, NULL) < 0)
        perror("Failed to setup signal handler for SIGSEGV");
#endif
}

void VoltDBSHM::threadLocalPoolAllocations() {
    std::size_t poolAllocations = ThreadLocalPool::getPoolAllocationSize();
    char response[9];
    response[0] = kErrorCode_Success;
    *reinterpret_cast<std::size_t*>(&response[1]) = htonll(poolAllocations);
    writeOrDie(m_cp, (unsigned char*)response, 9);
}

void VoltDBSHM::pushExportBuffer(
        int32_t partitionId,
        std::string signature,
        voltdb::ExportStreamBlock *block) {
    int32_t index = 0;
    m_reusedResultBuffer[index++] = kErrorCode_pushExportBuffer;
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[index]) = htonl(partitionId);
    index += 4;
    *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[index]) = htonl(static_cast<int32_t>(signature.size()));
    index += 4;
    ::memcpy( &m_reusedResultBuffer[index], signature.c_str(), signature.size());
    index += static_cast<int32_t>(signature.size());
    if (block != NULL) {
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index]) = htonll(block->startSequenceNumber());
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+8]) = htonll(block->getCommittedSequenceNumber());
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+16]) = htonll(block->getRowCount());
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+24]) = htonll(block->lastSpUniqueId());
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+32]) = htonll(block->lastCommittedSpHandle());
    } else {
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index]) = 0;
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+8]) = 0;
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+16]) = 0;
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+24]) = 0;
        *reinterpret_cast<int64_t*>(&m_reusedResultBuffer[index+32]) = 0;
    }
    index += 40;
    if (block != NULL) {
        *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[index]) = htonl(block->rawLength());
        writeOrDie(m_cp, (unsigned char*)m_reusedResultBuffer, index + 4, true);
        // Memset the first 8 bytes to initialize the MAGIC_HEADER_SPACE_FOR_JAVA
        ::memset(block->rawPtr(), 0, 8);
        writeOrDie(m_cp, (unsigned char*)block->rawPtr(), block->rawLength());
        // Need the delete in the if statement for valgrind
        delete [] block->rawPtr();
        delete block;
    } else {
        *reinterpret_cast<int32_t*>(&m_reusedResultBuffer[index]) = htonl(0);
        writeOrDie(m_cp, (unsigned char*)m_reusedResultBuffer, index + 4);
    }
}

void VoltDBSHM::executeTask(struct ipc_command *cmd) {
    try {
        execute_task *task = (execute_task*)cmd;
        voltdb::TaskType taskId = static_cast<voltdb::TaskType>(ntohll(task->taskId));
        ReferenceSerializeInputBE input(task->task, MAX_MSG_SZ);
        m_engine->resetReusedResultOutputBuffer(1);
        // We can't update the result from getResultsBuffer (which may use the failoverBuffer)
        m_reusedResultBuffer[0] = kErrorCode_Success;
        m_engine->executeTask(taskId, input);
        int32_t responseLength = m_engine->getResultsSize();
        writeOrDie(m_cp, m_engine->getResultsBuffer(), responseLength);
    } catch (const FatalException& e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::applyBinaryLog(struct ipc_command *cmd) {
    try {
        apply_binary_log *params = (apply_binary_log*)cmd;
        m_engine->resetReusedResultOutputBuffer(1);
        int64_t rows = m_engine->applyBinaryLog(ntohll(params->txnId),
                                        ntohll(params->spHandle),
                                        ntohll(params->lastCommittedSpHandle),
                                        ntohll(params->uniqueId),
                                        ntohl(params->remoteClusterId),
                                        ntohll(params->undoToken),
                                        params->log);
        char response[9];
        response[0] = kErrorCode_Success;
        *reinterpret_cast<int64_t*>(&response[1]) = htonll(rows);
        writeOrDie(m_cp, (unsigned char*)response, 9);
    } catch (const FatalException& e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::setReplicableTables(struct ipc_command *cmd) {
    try {
        set_replicable_tables* params = (set_replicable_tables*)cmd;

        if (params->tableCount < 0) {
            sendResponseOrException(m_engine->setReplicableTables(params->clusterId, nullptr));
        } else {
            int32_t size = params->tableCount;

            std::vector<std::string> replicableTables;
            replicableTables.reserve(size);

            int sz = static_cast<int> (ntohl(cmd->msgsize) - sizeof(set_replicable_tables));
            ReferenceSerializeInputBE in(params->tableNames, sz);

            for (int i = 0; i < size; ++i) {
                replicableTables.emplace_back(in.readTextString());
            }

            sendResponseOrException(m_engine->setReplicableTables(params->clusterId, &replicableTables));
        }
    } catch (const FatalException& e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::clearAllReplicableTables(struct ipc_command *cmd) {
    try {
        m_engine->clearAllReplicableTables();
        sendResponseOrException(0);
    } catch (const FatalException& e) {
        crashVoltDB(e);
    }
}

void VoltDBSHM::clearReplicableTables(struct ipc_command *cmd) {
    try {
        int clusterId = *((int*)&cmd->data[0]);
        m_engine->clearReplicableTables(clusterId);
        sendResponseOrException(0);
    } catch (const FatalException& e) {
        crashVoltDB(e);
    }
}

int64_t VoltDBSHM::pushDRBuffer(int32_t partitionId, DrStreamBlock *block) {
    if (block != NULL) {
        delete []block->rawPtr();
        delete block;
    }
    return -1;
}

void VoltDBSHM::reportDRBuffer(int32_t partitionId, const char *reason, const char *buffer, size_t length) {
    return;
}

void VoltDBSHM::pushPoisonPill(int32_t partitionId, std::string& reason, voltdb::DrStreamBlock *block) {
    if (block != NULL) {
        delete []block->rawPtr();
        delete block;
    }
}

int VoltDBSHM::reportDRConflict(int32_t partitionId, int32_t remoteClusterId, int64_t remoteTimestamp,
            std::string tableName, bool isReplicatedTable, voltdb::DRRecordType action,
            voltdb::DRConflictType deleteConflict, voltdb::Table *existingMetaTableForDelete, voltdb::Table *existingTupleTableForDelete,
            voltdb::Table *expectedMetaTableForDelete, voltdb::Table *expectedTupleTableForDelete,
            voltdb::DRConflictType insertConflict, voltdb::Table *existingMetaTableForInsert, voltdb::Table *existingTupleTableForInsert,
            voltdb::Table *newMetaTableForInsert, voltdb::Table *newTupleTableForInsert) {
    return 0;
}

bool VoltDBSHM::storeLargeTempTableBlock(voltdb::LargeTempTableBlock* block) {
    return false;
}

bool VoltDBSHM::loadLargeTempTableBlock(voltdb::LargeTempTableBlock* block) {
    throwFatalException("unimplemented method \"%s\" called!", __FUNCTION__);
    return false;
}

bool VoltDBSHM::releaseLargeTempTableBlock(LargeTempTableBlockId blockId) {
    return false;
}

struct VoltDBSHMDeleter {
    void operator()(VoltDBSHM* voltipc) {
        if (voltipc->getEngine() == NULL || !voltipc->getEngine()->isLowestSite()) {
            // if the engine was already destroyed, or if it's not the lowest site,
            // just decrement the latch.
            delete voltipc;
            pthread_mutex_lock(&g_cleanUpMutex);
            if (--g_cleanUpCountdownLatch <= 1) {
                pthread_cond_broadcast(&g_cleanUpCondition);
            }
            pthread_mutex_unlock(&g_cleanUpMutex);
        }
        else {
            // The lowest site: wait for the other sites to shut down before exiting.
            pthread_mutex_lock(&g_cleanUpMutex);
            while (g_cleanUpCountdownLatch > 1) {
                pthread_cond_wait(&g_cleanUpCondition, &g_cleanUpMutex);
            }
            pthread_mutex_unlock(&g_cleanUpMutex);
            delete voltipc;
        }
    }
};


void ping(AeronConnectionPair * cp) {
    // pingpong test
    unsigned char buffer[150000];  
    unsigned char correct_buffer[150000];
    memset(buffer, 0, sizeof(buffer));
    uint32_t times = 50000;
    std::size_t length = 1024;
    auto start = std::chrono::high_resolution_clock::now();
    for (uint32_t i = 0; i < times; ++i) {
        memset(buffer, i % 26 + 97, length);
        memset(correct_buffer, i % 26 + 97, length);
        writeOrDie(cp, buffer, length);
        ssize_t sz = read(cp, buffer, length);
        if (sz != length) {
            printf("expect to read %lu bytes, got %ld bytes\n", length, sz);
            fflush(stdout);
            exit(1);
        }
        if (memcmp(correct_buffer, buffer, length) != 0) {
            printf("expect to read %s, got %s\n", correct_buffer, buffer);
            fflush(stdout);
            exit(1);
        }
        //printf("Round %d passed\n", i);
    }
    auto finish = std::chrono::high_resolution_clock::now();
    auto time_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(finish-start).count();

    printf("ping pong test for ping finished and succeeded, latency %f us\n", time_ns / 1000 / (times + 0.0));
}

void pong(AeronConnectionPair * cp) {
    // pingpong test
    unsigned char buffer[150000];  
    unsigned char correct_buffer[150000];
    memset(buffer, 0, sizeof(buffer));
    uint32_t times = 50000;
    std::size_t length = 1024;
    for (uint32_t i = 0; i < times; ++i) {
        memset(buffer, i % 26 + 97, length);
        memset(correct_buffer, i % 26 + 97, length);
        ssize_t sz = read(cp, buffer, length);
        if (sz != length) {
            printf("expect to read %lu bytes, got %ld bytes\n", length, sz);
            fflush(stdout);
            exit(1);
        }
        if (memcmp(correct_buffer, buffer, length) != 0) {
            printf("expect to read %s, got %s\n", correct_buffer, buffer);
            fflush(stdout);
            exit(1);
        }
        writeOrDie(cp, buffer, length);
        //printf("Round %d passed\n", i);
    }
    printf("ping pong test for pong finished and succeeded\n");
}

void exchangeLAPICId(AeronConnectionPair * cp) {
    writeOrDie(cp, (const unsigned char *)&cp->core_id, sizeof(cp->core_id));
    __attribute__((unused))  auto ret = read(cp, &cp->dual_qemu_lapic_id, sizeof(cp->dual_qemu_lapic_id));
    assert(ret == 4);
    printf("this core_id %d, dual_qemu_lapic_id %d\n", cp->core_id, cp->dual_qemu_lapic_id);
}



static void bind_thread_to_core(int core_id) {
    cpu_set_t cpuset;
    pthread_t thread = pthread_self();
    // Initialize the cpuset mask
    CPU_ZERO(&cpuset);
    CPU_SET(core_id, &cpuset);

    // Bind the thread to the specified core
    int result = pthread_setaffinity_np(thread, sizeof(cpu_set_t), &cpuset);
    if (result != 0)
    {
        VOLT_ERROR("Error setting thread affinity");
        exit(-1);
    }
}


#include <fcntl.h>
#include <unistd.h>
#include<sys/ioctl.h>

#define DBOS_WAIT _IOW('a','a',int32_t*)
#define DBOS_NOTIFY _IOW('a','b',uint64_t*)

int fd_hypervisor = -1;
bool use_para_virt_notification = false;
int32_t dual_qemu_pid = -1;

void eethread2(int eeid, AeronConnectionPair * cp) {
    // pingpong test
    printf("ee %d started, ping pong test\n", eeid);
    bind_thread_to_core(eeid);
    cp->core_id = sched_getcpu();
    bool use_para_virt_notification_temp = use_para_virt_notification;
    cp->use_para_virt_notification = false;
    exchangeLAPICId(cp);
    cp->use_para_virt_notification = use_para_virt_notification_temp;
    unsigned char buffer[150000];  
    unsigned char correct_buffer[150000];
    memset(buffer, 0, sizeof(buffer));
    uint32_t times = 10000;
    std::size_t length = 128000;
    for (uint32_t i = 0; i < times; ++i) {
        memset(buffer, i % 26 + 97, length);
        memset(correct_buffer, i % 26 + 97, length);
        writeOrDie(cp, buffer, length);
        ssize_t sz = read(cp, buffer, length);
        if (sz != length) {
            printf("expect to read %lu bytes, got %ld bytes\n", length, sz);
            fflush(stdout);
            exit(1);
        }
        if (memcmp(correct_buffer, buffer, length) != 0) {
            printf("expect to read %s, got %s\n", correct_buffer, buffer);
            fflush(stdout);
            exit(1);
        }
        //printf("Round %d passed\n", i);
    }
    printf("ping pong test for ee %d finished and succeeded, pv_notify_count %lu, pv_wait_count %lu\n", eeid, cp->pv_notify_count, cp->pv_wait_count);


    /* max message size that can be read from java */
    int max_ipc_message_size = (1024 * 1024 * 2);

    // requests larger than this will cause havoc.
    // cry havoc and let loose the dogs of war
    boost::shared_array<char> data(new char[max_ipc_message_size]);
    memset(data.get(), 0, max_ipc_message_size);

    // instantiate voltdbSHM to interface to EE.
    std::unique_ptr<VoltDBSHM, VoltDBSHMDeleter> voltipc(new VoltDBSHM(cp));

    // loop until the terminate/shutdown command is seen
    bool terminated = false;
    while ( ! terminated) {
        size_t bytesread = 0;

        // read the header
        while (bytesread < 4) {
            std::size_t b = read(cp, data.get() + bytesread, 4 - bytesread);
            if (b == 0) {
                printf("client eof\n");
                return;
            } else if (b == -1) {
                printf("client error\n");
                return;
            }
            bytesread += b;
        }

        // read the message body in to the same data buffer
        int msg_size = ntohl(((struct ipc_command*) data.get())->msgsize);
        if (staticDebugVerbose) {
            std::cout << "Received message size " << msg_size << std::endl;
        }
        if (msg_size > max_ipc_message_size) {
            max_ipc_message_size = msg_size;
            char* newdata = new char[max_ipc_message_size];
            memset(newdata, 0, max_ipc_message_size);
            memcpy(newdata, data.get(), 4);
            data.reset(newdata);
        }

        while (bytesread < msg_size) {
            std::size_t b = read(cp, data.get() + bytesread, msg_size - bytesread);
            if (b == 0) {
                printf("client eof\n");
                return;
            } else if (b == -1) {
                printf("client error\n");
                return;
            }
            bytesread += b;
        }

        // dispatch the request
        struct ipc_command *cmd = (struct ipc_command*) data.get();

        // size at least length + command
        if (ntohl(cmd->msgsize) < sizeof(struct ipc_command)) {
            if (staticDebugVerbose) {
                std::cout << "Incomplete bytesread=" << bytesread <<
                        " cmd=" << ntohl(cmd->command) <<
                        " msgsize=" << ntohl(cmd->msgsize) << "\n";
                for (int ii = 0; ii < bytesread; ++ii) {
                    std::cout << "bytesread [" << ii << "] = " << data[ii] << "\n";
                }
                std::cout << std::endl;
            }
        }
        vassert(ntohl(cmd->msgsize) >= sizeof(struct ipc_command));
        if (staticDebugVerbose) {
            std::cout << "Completed command: " << ntohl(cmd->command) << std::endl;
        }
        terminated = voltipc->execute(cmd);
    }

}

static int pv_notify(int fd, uint32_t dual_qemu_pid, uint32_t dual_qemu_lapic_id) {
    uint64_t word = ((uint64_t) dual_qemu_pid << 32) | dual_qemu_lapic_id;
    return ioctl(fd, DBOS_NOTIFY, &word);
}

static void pv_wait(int fd) {
    uint32_t dummy = 0;
    ioctl(fd, DBOS_WAIT, &dummy);
}

static void writeOrDie(AeronConnectionPair * cp, const unsigned char *data, ssize_t sz, bool no_notify) {
    int dual_qemu_lapic_id = cp->dual_qemu_lapic_id; 
    bool notified = false;
    while (cp->outgoing_ringbuffer->write_bytes(data, sz) == false)
    {
        //idleStrategy.idle(0);
        no_notify = false;
        if (no_notify == false && notified == false && fd_hypervisor >= 0 && cp->use_para_virt_notification && cp->outgoing_ringbuffer->get_is_halted()) {
            int ret = pv_notify(fd_hypervisor, dual_qemu_pid, dual_qemu_lapic_id);
            ++cp->pv_notify_count;
            notified = true;
            if (ret < 0) {
                perror(strerror(errno));
                exit(-1);
            }
        }
    }
    if (no_notify == false && notified == false && fd_hypervisor >= 0 && cp->use_para_virt_notification && cp->outgoing_ringbuffer->get_is_halted()) {
        int ret = pv_notify(fd_hypervisor, dual_qemu_pid, dual_qemu_lapic_id);
        ++cp->pv_notify_count;
        if (ret < 0) {
            perror(strerror(errno));
            exit(-1);
        }
        //printf("para noitify return %d\n", ret);
    }
}


static ssize_t read(AeronConnectionPair * cp, void *buf, size_t sz) {
    static constexpr int kCountDownCycles = 30;
    int count_down = kCountDownCycles;
    while (cp->incoming_ringbuffer->read_bytes(buf, sz) == false)
    {
        if (--count_down < 0 && cp->use_para_virt_notification && fd_hypervisor >= 0) {
            assert(fd_hypervisor >= 0);
            assert(cp->incoming_ringbuffer->get_is_halted() == false);
            if (cp->outgoing_ringbuffer->readable_bytes() > 0 && cp->outgoing_ringbuffer->get_is_halted()) {
                pv_notify(fd_hypervisor, dual_qemu_pid, cp->dual_qemu_lapic_id);
            }

            cp->incoming_ringbuffer->set_halted();
            pv_wait(fd_hypervisor);
            cp->incoming_ringbuffer->clear_halted();
            ++cp->pv_wait_count;
            count_down = kCountDownCycles;
        }
    }
    return sz;
}

int main(int argc, char **argv) {
    //Create a pool ref to init the thread local in case a poll message comes early
    voltdb::ThreadLocalPool poolRef;
    const int pid = getpid();
    // NOTE: EEProcess.java code validates the first few lines of this process
    // output, so keep it up to date with these printfs.
    printf("== pid = %d ==\n", pid);
    fflush(stdout);

    int eecount = 1;
    int port = 0; // 0 means pick any port

    // allow caller to specify the number of ees - defaults to 1
    if (argc >= 2) {
        char *eecountStr = argv[1];
        vassert(eecountStr);
        eecount = atoi(eecountStr);
        vassert(eecount >= 0);
    // NOTE: EEProcess.java code validates the first few lines of this process
    // output, so keep it up to date with these printfs.
        printf("== eecount = %d ==\n", eecount);
    }

    //boost::shared_array<pthread_t> eeThreads(new pthread_t[eecount]);

    // allow caller to override port with the second argument
    if (argc >= 3) {
        char *portStr = argv[2];
        vassert(portStr);
        port = atoi(portStr);
        vassert(port > 0);
        vassert(port <= 65535);
    }
    printf("== port = %d ==\n", port);

    if (argc >= 4) {
        char *dual_qemu_pid_str = argv[3];
        assert(dual_qemu_pid_str);
        dual_qemu_pid = atoi(dual_qemu_pid_str);
    }

    if (argc >= 5) {
        char *para_virt_not_str = argv[4];
        assert(para_virt_not_str);
        use_para_virt_notification = atoi(para_virt_not_str);
        assert(eecount >= 0);    
    }

    printf("== dual_qemu_pid = %d ==\n", dual_qemu_pid);
    printf("== use_para_virt_notification = %d ==\n", use_para_virt_notification);
    fflush(stdout);

    const std::string aeronChannel = "aeron:ipc";
    std::vector<std::shared_ptr<AeronConnectionPair>> aeronConns;

    {
        char * buf1 = new char[RING_BUFFER_CAP];
        char * buf2 = new char[RING_BUFFER_CAP];
        auto outgoing_ringbuffer = new RingByteBuffer((char*)buf1, RING_BUFFER_CAP);
        outgoing_ringbuffer->clear();

        auto incoming_ringbuffer_2 = new RingByteBuffer((char*)buf1, RING_BUFFER_CAP);

        auto incoming_ringbuffer = new RingByteBuffer((char*)buf2, RING_BUFFER_CAP);
        incoming_ringbuffer->clear();
        auto outgoing_ringbuffer_2 = new RingByteBuffer((char*)buf2, RING_BUFFER_CAP);

        std::thread t3(ping, new AeronConnectionPair(outgoing_ringbuffer, incoming_ringbuffer));
        std::thread t4(pong, new AeronConnectionPair(outgoing_ringbuffer_2, incoming_ringbuffer_2));
        t3.join();
        t4.join();

    }
    
    for (int ee = 0; ee < eecount; ee++) {
        std::string outgoing_shared_memory_file = "/sys/bus/pci/devices/0000:00:04.0/resource2";
        int fd = open(outgoing_shared_memory_file.c_str(), O_RDWR);
        if (fd < 0) {
            printf("Failed to open %s : %s\n", outgoing_shared_memory_file.c_str(), strerror(errno));
            fflush(stdout);
            exit(1);
        }
        void * addr1 = mmap(nullptr, RING_BUFFER_CAP, PROT_READ | PROT_WRITE, MAP_SHARED, fd, ee * RING_BUFFER_CAP);
        if (addr1 == MAP_FAILED) {
            printf("Failed to mmap %s : %s\n", outgoing_shared_memory_file.c_str(), strerror(errno));
            fflush(stdout);
            exit(1);
        }
        close(fd);
        auto outgoing_ringbuffer = new RingByteBuffer((char*)addr1, RING_BUFFER_CAP);

        std::string incoming_shared_memory_file = "/sys/bus/pci/devices/0000:00:05.0/resource2";
        fd = open(incoming_shared_memory_file.c_str(), O_RDWR);
        if (fd < 0) {
            printf("Failed to open %s : %s\n", incoming_shared_memory_file.c_str(), strerror(errno));
            fflush(stdout);
            exit(1);
        }
        auto addr2 = mmap(nullptr, RING_BUFFER_CAP, PROT_READ | PROT_WRITE, MAP_SHARED, fd, ee * RING_BUFFER_CAP);
        if (addr2 == MAP_FAILED) {
            printf("Failed to mmap %s : %s\n", incoming_shared_memory_file.c_str(), strerror(errno));
            fflush(stdout);
            exit(1);
        }
        close(fd);
        auto incoming_ringbuffer = new RingByteBuffer((char*)addr2, RING_BUFFER_CAP);

        aeronConns.push_back(std::make_shared<AeronConnectionPair>(outgoing_ringbuffer, incoming_ringbuffer));
        printf("%p for %s, %p for %s\n", addr1, outgoing_shared_memory_file.c_str(), addr2, incoming_shared_memory_file.c_str());
    }

    fflush(stdout);

    fd_hypervisor = open("/dev/etx_device", O_RDWR);

    if(fd_hypervisor < 0) {
        printf("Cannot open hypervisor device file...\n");
        return 0;
    }

    printf("/dev/etx_device opened...\n");

    g_cleanUpCountdownLatch = eecount;

    std::vector<std::thread> threads;
    for (int ee = 0; ee < eecount; ee++) {
        threads.emplace_back(std::thread(eethread2, ee, aeronConns[ee].get()));
    }

    // wait for all of the EEs to finish
    for (int ee = 0; ee < eecount; ee++) {
        threads[ee].join();
    }

    SynchronizedThreadLock::destroy();
    fflush(stdout);
    return 0;
}
