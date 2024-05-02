#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include "time_tracker.h"

#include "org_voltcore_network_TimeTracker2.h"

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBEnableTracing(JNIEnv *env, jclass obj, jboolean enable) {
    enable_tracing(enable);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBDumpTraces(JNIEnv *env, jclass obj, jbyteArray traceFilePath) {
    jbyte* trace_file_path_chars = (*env)->GetByteArrayElements(env, traceFilePath, NULL);
    int len = (*env)->GetArrayLength(env, traceFilePath);
    // char *trace_file_path = (char *)trace_file_path_chars;
    char *trace_file_path = (char *)malloc(len + 1);
    memcpy(trace_file_path, trace_file_path_chars, len);
    trace_file_path[len] = '\0';
    dump_traces(trace_file_path);
    free(trace_file_path);
    (*env)->ReleaseByteArrayElements(env, traceFilePath, trace_file_path_chars, 0);
    // std::string trace_file_path(reinterpret_cast<char*>(trace_file_path_chars), env->GetArrayLength(traceFilePath));
    return 0;
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

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBWorkQueue(JNIEnv *env, jclass obj) {
    record_tracepoint(18);
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

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBResponseQueue(JNIEnv *env, jclass obj) {
    record_tracepoint(19);
    return 0;
}

JNIEXPORT jint JNICALL Java_org_voltcore_network_TimeTracker2_VoltDBWorkEnd(JNIEnv *env, jclass obj) {
    record_tracepoint(3);
    return 0;
}