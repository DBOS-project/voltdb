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

#include "ff_config.h"
#include "ff_api.h"
#include "ff_epoll.h"

#include "org_voltcore_network_FSelect.h"

#define MAX_EVENTS 512
#define MAX_CONN 512
#define BUF_SIZE 1024 * 16

struct epoll_event ev;
struct epoll_event events[MAX_EVENTS];

bool write_ready = false;

char buf[BUF_SIZE];
jobject byteBuffer;

int epfd;
// int sockfd;

JNIEnv* jniEnv;
jclass javaServerClass;
jobject javaServerObj;
jmethodID processMethodId;
jmethodID acceptMethodId;
jmethodID writeMethodId;

struct callArgs {
    JNIEnv* env;
    jobject thisObject;
	jint sockfd;
};

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fInit
  (JNIEnv *env, jclass class) {
	char*const* argv;
    ff_init(1, argv);
  }

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSelect_fOpen
  (JNIEnv * env, jobject thisObject) {
    jniEnv = env;
    printf("start_server: About to get the static method from %s\n", env);
    javaServerClass = (*env)->FindClass(env, "org/voltcore/network/FSelect");
    printf("start_server: About to get the static method of %s\n", javaServerClass);
    // processMethodId = (*env)->GetStaticMethodID(javaServerClass, "hello", "()V");
    processMethodId = (*env)->GetMethodID(env, javaServerClass, "indicateReadyForRead", "(I)V");
    acceptMethodId = (*env)->GetMethodID(env, javaServerClass, "handleAccept", "(I)V");
    writeMethodId = (*env)->GetMethodID(env, javaServerClass, "handleReadyForWrite", "()V");
    // processMethodId = (*env)->GetMethodID(env, javaServerClass, "processMsg", "(ILjava/nio/ByteBuffer;I)V");
    printf("start_server: Got the static method id: %s\n", processMethodId);

    if ((epfd = ff_epoll_create(10)) < 0) {
		perror("epoll_create failed");
        exit(1);
	}

    printf("Setup epoll and sockets; about to run loop\n");
    byteBuffer = (*env)->NewDirectByteBuffer(env, (void *)buf, BUF_SIZE);
    return epfd;
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_indicateReadyForWrite
  (JNIEnv *env, jobject thisObject) {
	write_ready = true;
	printf("Write ready set to true\n");
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fRegister
  (JNIEnv *env, jobject thisObject, jint epoll_fd, jint fd) {
    struct epoll_event event;
    event.data.fd = fd;
    event.events = EPOLLIN;
	if (ff_epoll_ctl(epoll_fd, EPOLL_CTL_ADD, fd, &event) < 0) {
		perror("epoll_ctl failed");
		exit(1);
	}
    printf("Registered fd %d with epoll fd %d\n", fd, epoll_fd);
  }

void loop(void *arg) {
	struct callArgs *callArg = (struct callArgs *) arg;
    JNIEnv* env = callArg->env;
	jobject thisObject = callArg-> thisObject;
	int sockfd = callArg->sockfd;

	if (write_ready) {
		printf("Write ready; calling java method to drain.\n");
		// might need mutex lock for the race condition of one thread setting write_ready
		// to true and another thread trying to flush the write buffers
		// But there is anyther lock in the Java side, need to think more if race arises
		(*env)->CallVoidMethod(env, thisObject, writeMethodId);
		if ((*env)->ExceptionCheck(env)) {
			printf("Exception in write method\n");
		} else {
			printf("Write method called. No exception\n");
		}
		write_ready = false;
	}

	int nevents = ff_epoll_wait(epfd, events, MAX_EVENTS, 0);
	int i;

	// printf("Got %d events\n", nevents);

	for (i = 0; i < nevents; ++i) {
		printf("Got event on fd %d\n", events[i].data.fd);
		/* Handle new connect */
		if (events[i].data.fd == sockfd) {
			while (1) {
				int nclientfd = ff_accept(sockfd, NULL, NULL);
				if (nclientfd < 0) {
					break;
				}

				(*env)->CallVoidMethod(env, thisObject, acceptMethodId, nclientfd);
				break; // Somehow catch exception from Java side
				/* Add to event list */
				// ev.data.fd = nclientfd;
				// ev.events  = EPOLLIN;
				// if (ff_epoll_ctl(epfd, EPOLL_CTL_ADD, nclientfd, &ev) != 0) {
				// 	printf("epoll_ctl failed:%d, %s\n", errno,
				// 		strerror(errno));
				// 	break;
				// }
			}
		} else { 
			if (events[i].events & EPOLLERR ) {
				/* Simply close socket */
				ff_epoll_ctl(epfd, EPOLL_CTL_DEL, events[i].data.fd, NULL);
				ff_close(events[i].data.fd);
			} else if (events[i].events & EPOLLHUP) {
                /* Simply close socket */
                ff_epoll_ctl(epfd, EPOLL_CTL_DEL,  events[i].data.fd, NULL);
                ff_close(events[i].data.fd);
            } else if (events[i].events & EPOLLIN) {
				(*env)->CallVoidMethod(env, thisObject, processMethodId, events[i].data.fd);
			} else {
				printf("unknown event: %8.8X\n", events[i].events);
			}
		}
	}
}

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fSelect
  (JNIEnv *env, jobject thisObject, jint sockfd) {
    /* Wait for events to happen */
	struct callArgs args;
	args.env = env;
	args.thisObject = thisObject;
	args.sockfd = sockfd;
    ff_run(loop, &args);
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_write
  (JNIEnv * env, jobject thisObject, jint fd, jobject buf, jint readlen) {
    int sentlen = 0;
	int written;
	char *data = (char *)(*env)->GetDirectBufferAddress(env, buf);
  	while (sentlen < readlen) {
	    written = ff_write(fd, data + sentlen, readlen - sentlen);
	    if (written < 0) {
            perror("FSelect: write failed");
	        break;
	    }
	    sentlen += written;
	}
	return 0;
  }