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
  (JNIEnv * env, jobject thisObject, jint port) {
    jniEnv = env;
    printf("start_server: About to get the static method from %s\n", env);
    javaServerClass = (*env)->FindClass(env, "org/voltcore/network/FSelect");
    printf("start_server: About to get the static method of %s\n", javaServerClass);
    // processMethodId = (*env)->GetStaticMethodID(javaServerClass, "hello", "()V");
    processMethodId = (*env)->GetMethodID(env, javaServerClass, "processMsg", "(ILjava/nio/ByteBuffer;I)V");
    printf("start_server: Got the static method id: %s\n", processMethodId);

    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    // printf("sockfd:%d\n", sockfd);
    if (sockfd < 0) {
        printf("ff_socket failed\n");
        exit(1);
    }

    int on = 1;
    ioctl(sockfd, FIONBIO, &on);

    struct sockaddr_in my_addr;
    bzero(&my_addr, sizeof(my_addr));
    my_addr.sin_family = AF_INET;
    my_addr.sin_port = htons((int) port);
    my_addr.sin_addr.s_addr = htonl(INADDR_ANY);

    int ret = bind(sockfd, (struct sockaddr *)&my_addr, sizeof(my_addr));

    if (ret < 0) {
        printf("bind failed\n");
        exit(1);
    }

    ret = listen(sockfd, MAX_EVENTS);
    if (ret < 0) {
        printf("listen failed\n");
        exit(1);
    }

    if ((epfd = epoll_create(10)) < 0) {
		perror("epoll_create failed");
        exit(1);
	}
    ev.data.fd = sockfd;
    ev.events = EPOLLIN;
    epoll_ctl(epfd, EPOLL_CTL_ADD, sockfd, &ev);
    printf("Setup epoll and sockets; about to run loop\n");
    byteBuffer = (*jniEnv)->NewDirectByteBuffer(env, (void *)buf, BUF_SIZE);
    return epfd;
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fSelect
  (JNIEnv *env, jobject thisObject) {
    /* Wait for events to happen */
    while (1) {
        int nevents = epoll_wait(epfd, events, MAX_EVENTS, -1);
        int i;

        // printf("nevents:%d\n", nevents);

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
                    // printf("Read %d bytes\n", readlen);
                    while (1) {
                        (*jniEnv)->CallVoidMethod(jniEnv, thisObject, processMethodId, events[i].data.fd, byteBuffer, readlen);
                        // printf("About to recv\n");
                        memset(buf, 0, readlen);
                        readlen = recv(events[i].data.fd, buf, sizeof(buf), MSG_DONTWAIT);
                        // printf("Received %d bytes\n", readlen);
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
	while (sentlen < readlen) {
	    written = write(fd, data + sentlen, readlen - sentlen);
	    if (written < 0) {
	        break;
	    }
	    sentlen += written;
	}
	return 0;
  }