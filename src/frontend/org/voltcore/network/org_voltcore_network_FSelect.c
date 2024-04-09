#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <errno.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/epoll.h>

#include "org_voltcore_network_FSelect.h"

#define MAX_EVENTS 512
#define BUF_SIZE 1024 * 16

struct epoll_event ev;
struct epoll_event events[MAX_EVENTS];

char buf[BUF_SIZE];
jobject byteBuffer;

int epfd;
int sockfd;

JNIEnv* jniEnv;
jclass javaServerClass;
jobject javaServerObj;
jmethodID processMethodId;

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSelect_fOpen
  (JNIEnv * env, jobject thisObject) {
    jniEnv = env;
    printf("start_server: About to get the static method from %s\n", env);
    javaServerClass = (*env)->FindClass(env, "org/voltcore/network/FSelect");
    printf("start_server: About to get the static method of %s\n", javaServerClass);
    // processMethodId = (*env)->GetStaticMethodID(javaServerClass, "hello", "()V");
    processMethodId = (*env)->GetMethodID(env, javaServerClass, "processMsg", "(ILjava/nio/ByteBuffer;I)V");
    printf("start_server: Got the static method id: %s\n", processMethodId);

    if ((epfd = epoll_create(10)) < 0) {
		perror("epoll_create failed");
        exit(1);
	}

    printf("Setup epoll and sockets; about to run loop\n");
    byteBuffer = (*jniEnv)->NewDirectByteBuffer(env, (void *)buf, BUF_SIZE);
    return epfd;
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fRegister
  (JNIEnv *env, jobject thisObject, jint epoll_fd, jint fd) {
    ev.data.fd = fd;
    ev.events = EPOLLIN;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, fd, &ev);
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fSelect
  (JNIEnv *env, jobject thisObject) {
    jobject byteBuf = (*env)->NewDirectByteBuffer(env, (void *)buf, BUF_SIZE);
    /* Wait for events to happen */
    printf("About to start epoll loop\n");
    while (1) {
        int nevents = epoll_wait(epfd, events, MAX_EVENTS, -1);
        int i;

        printf("Got %d events\n", nevents);

        for (i = 0; i < nevents; ++i) {
            /* Handle new connect */
            if (events[i].data.fd == sockfd) {
                while (1) {
                    int nclientfd = accept(sockfd, NULL, NULL);
                    if (nclientfd < 0) {
                        break;
                    }

                    /* Add to event list */
                    ev.data.fd = nclientfd;
                    ev.events  = EPOLLIN;
                    if (epoll_ctl(epfd, EPOLL_CTL_ADD, nclientfd, &ev) != 0) {
                        printf("epoll_ctl failed:%d, %s\n", errno,
                            strerror(errno));
                        break;
                    }
                }
            } else { 
                if (events[i].events & EPOLLERR ) {
                    /* Simply close socket */
                    epoll_ctl(epfd, EPOLL_CTL_DEL, events[i].data.fd, NULL);
                    close(events[i].data.fd);
                } else if (events[i].events & EPOLLIN) {
                    int readlen = read(events[i].data.fd, buf, sizeof(buf));
                    if (readlen == 0) {
                        // printf("Closing client\n");
                        close(events[i].data.fd);
                        continue;
                    }
                    while (1) {
                        printf("About to call processMsg with %s\n", buf);
                        (*env)->CallVoidMethod(env, thisObject, processMethodId, events[i].data.fd, byteBuf, readlen);
                        memset(buf, 0, readlen);
                        readlen = recv(events[i].data.fd, buf, sizeof(buf), MSG_DONTWAIT);
                        if (readlen <= 0) {
                            // printf("Breaking out of this epoll event\n");
                            break;
                        }
                    }
                } else {
                    printf("unknown event: %8.8X\n", events[i].events);
                }
            }
        }
    }
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_write
  (JNIEnv * env, jobject thisObject, jint fd, jobject buf, jint readlen) {
    int sentlen = 0;
	int written;
	char *data = (char *)(*env)->GetDirectBufferAddress(env, buf);
    printf("FSelect data: %s\n", data);
	while (sentlen < readlen) {
	    written = write(fd, data + sentlen, readlen - sentlen);
	    if (written < 0) {
            perror("write failed");
	        break;
	    }
	    sentlen += written;
	}
	return 0;
  }