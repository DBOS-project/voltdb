#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <errno.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#include "org_voltcore_network_FSocketConn.h"

JNIEXPORT jobject JNICALL Java_org_voltcore_network_FSocketConn_read
  (JNIEnv *env, jobject thisObject, jint fd) {
    char buffer[1024];
    int n = read(fd, buffer, 1024);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return NULL;
        }
        perror("read failed");
        exit(1);
    }
    if (n == 0) {
        return NULL;
    }
    jobject byteBuf = (*env)->NewDirectByteBuffer(env, (void *)buffer, n);
    // jbyteArray result = (*env)->NewByteArray(env, n);
    // (*env)->SetByteArrayRegion(env, result, 0, n, buffer);
    return byteBuf;
  }


JNIEXPORT void JNICALL Java_org_voltcore_network_FSocketConn_write
  (JNIEnv *env, jobject thisObject, jint fd, jobject byteBuf, jint len) {
    printf("FSocketConn write: fd=%d, len=%d\n", fd, len);
    int sentlen = 0;
    int written;
    char *data = (char *)(*env)->GetDirectBufferAddress(env, byteBuf);
    printf("data: %s\n", data);
    while (sentlen < len) {
        written = write(fd, data + sentlen, len - sentlen);
        if (written < 0) {
              perror("write failed");
            break;
        }
        sentlen += written;
    }
    return sentlen;
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSocketConn_close
  (JNIEnv *env, jobject thisObject, jint fd) {
	close(fd);
  }