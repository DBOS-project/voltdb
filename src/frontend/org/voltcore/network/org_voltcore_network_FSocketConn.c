#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <errno.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <sys/socket.h>

#include "ff_config.h"
#include "ff_api.h"
#include "ff_epoll.h"

#include "org_voltcore_network_FSocketConn.h"

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSocketConn_fread
  (JNIEnv *env, jobject thisObject, jint fd, jobject byteBuf, jint len) {
    char *buffer = (char *)(*env)->GetDirectBufferAddress(env, byteBuf);
    // char buffer[len];
    int n = ff_read(fd, buffer, len);
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
    return n;
  }

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSocketConn_freadInt
  (JNIEnv *env, jobject thisObject, jint fd) {
    int buffer;
    int n = ff_read(fd, &buffer, sizeof(int));
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return -1;
        }
        perror("read failed");
        exit(1);
    }
    if (n == 0) {
        return -1;
    }
    return ntohl(buffer);
  }


JNIEXPORT jint JNICALL Java_org_voltcore_network_FSocketConn_write
  (JNIEnv *env, jobject thisObject, jint fd, jobject byteBuf, jint len) {
    // printf("FSocketConn write: fd=%d, len=%d\n", fd, len);
    int sentlen = 0;
    int written;
    char *data = (char *)(*env)->GetDirectBufferAddress(env, byteBuf);
    // printf("Writing back: %s of length %d\n", data, len);
    while (sentlen < len) {
        written = ff_write(fd, data + sentlen, len - sentlen);
        if (written < 0) {
              perror("FSocketConn: write failed");
              printf("errno: %d in write\n", errno);
              return -1;
        }
        sentlen += written;
    }
    return sentlen;
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSocketConn_close
  (JNIEnv *env, jobject thisObject, jint fd) {
	  ff_close(fd);
  }