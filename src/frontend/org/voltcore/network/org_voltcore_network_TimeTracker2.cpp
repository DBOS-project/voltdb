#include <stdbool.h>
#include "time_tracker.h"

#include "org_voltcore_network_TimeTracker2.h"

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBPAPIReset(JNIEnv *env, jclass obj) {
    return reset_papi();
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBPAPIReadCounter(JNIEnv *env, jclass obj) {
    return read_papi_counter();
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBEnableTracing(JNIEnv *env, jclass obj, jboolean enable) {
    enable_tracing(enable);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBDumpTraces(JNIEnv *env, jclass obj, jbyteArray traceFilePath) {
    jbyte* trace_file_path_chars = env->GetByteArrayElements(traceFilePath, NULL);
    char *trace_file_path = (char *)trace_file_path_chars;
    // std::string trace_file_path(reinterpret_cast<char*>(trace_file_path_chars), env->GetArrayLength(traceFilePath));
    return dump_traces(trace_file_path);
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBLibcWrite(JNIEnv *env, jclass obj) {
    record_tracepoint(6);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBLibcWriteReturn(JNIEnv *env, jclass obj) {
    record_tracepoint(7);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBLibcRead(JNIEnv *env, jclass obj) {
    record_tracepoint(6);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBLibcReadReturn(JNIEnv *env, jclass obj) {
    record_tracepoint(7);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBWorkRecv(JNIEnv *env, jclass obj) {
    record_tracepoint(0);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBWorkStart(JNIEnv *env, jclass obj) {
    record_tracepoint(1);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBLocalCommStart(JNIEnv *env, jclass obj) {
    record_tracepoint(4);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBLocalCommEnd(JNIEnv *env, jclass obj) {
    record_tracepoint(5);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBWorkSend(JNIEnv *env, jclass obj) {
    record_tracepoint(2);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBWorkEnd(JNIEnv *env, jclass obj) {
    record_tracepoint(3);
    return 0;
}