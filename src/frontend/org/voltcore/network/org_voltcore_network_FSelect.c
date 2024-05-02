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

#include <pthread.h>

#include "ff_config.h"
#include "ff_api.h"
#include "ff_epoll.h"
#include "time_tracker.h"

#include "org_voltcore_network_FSelect.h"

#define MAX_EVENTS 512
#define MAX_CONN 512
#define BUF_SIZE 1024 * 16

struct epoll_event ev;
struct epoll_event events[MAX_EVENTS];

volatile int queuedFds[MAX_CONN];
volatile int queuedOpType[MAX_CONN];
volatile int opIsModify[MAX_CONN]; // 1-1 correspondance with writeQueuedFds
// int i;
// for (i = 0; i < MAX_CONN; i++) {
// 	writeQueuedFds[i] = -1;
// 	opIsModify[i] = 0;
// }
volatile int queuedFdsCount = 0;
pthread_mutex_t lock;

int epfd;
// int sockfd;

jclass javaServerClass;
jobject javaServerObj;
jmethodID processMethodId;
jmethodID acceptMethodId;
jmethodID writeMethodId;
jmethodID deleteMethodId;

struct callArgs {
    JNIEnv* env;
    jobject thisObject;
	jint sockfd;
};

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fInit
  (JNIEnv *env, jclass class) {
	char*const* argv;
    ff_init(1, argv);
	pthread_mutex_init(&lock, NULL);
  }

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSelect_fOpen
  (JNIEnv * env, jobject thisObject) {
    javaServerClass = (*env)->FindClass(env, "org/voltcore/network/FSelect");
    processMethodId = (*env)->GetMethodID(env, javaServerClass, "indicateReadyForRead", "(I)V");
    acceptMethodId = (*env)->GetMethodID(env, javaServerClass, "handleAccept", "(I)V");
    writeMethodId = (*env)->GetMethodID(env, javaServerClass, "handleReadyForWrite", "(I)V");
    deleteMethodId = (*env)->GetMethodID(env, javaServerClass, "deleteInterest", "(I)V");

    if ((epfd = ff_epoll_create(10)) < 0) {
		perror("epoll_create failed");
        exit(1);
	}

    printf("Setup epoll and sockets; about to run loop\n");
    return epfd;
  }

void add_to_epoll(int fd, int interestOps, int isModify) {
	struct epoll_event event;
	event.data.fd = fd;
	event.events = ((interestOps & 1) ? EPOLLIN | EPOLLET : 0) | ((interestOps & 2) ? EPOLLOUT : 0);
	int event_op = isModify ? EPOLL_CTL_MOD : EPOLL_CTL_ADD;
	if (ff_epoll_ctl(epfd, event_op, fd, &event) < 0) {
		perror("epoll_ctl failed");
		exit(1);
	}
	// printf("Registered fd %d with epoll fd %d for ops %d and events %d\n", fd, epfd, interestOps, event.events);
}

JNIEXPORT void JNICALL Java_org_voltcore_network_FSelect_fRegister
  (JNIEnv *env, jobject thisObject, jint epoll_fd, jint fd, jint interestOps, jboolean isModify) {
	pthread_mutex_lock(&lock);
	queuedFds[queuedFdsCount] = fd;
	// queuedFdsCount--;
	queuedOpType[queuedFdsCount] = (int) interestOps;
	// queuedFdsCount--;
	opIsModify[queuedFdsCount] = isModify;
	queuedFdsCount++;
	pthread_mutex_unlock(&lock);
  }

void loop(void *arg) {
	struct callArgs *callArg = (struct callArgs *) arg;
    JNIEnv* env = callArg->env;
	jobject thisObject = callArg-> thisObject;
	int sockfd = callArg->sockfd;

	pthread_mutex_lock(&lock);
	if (queuedFdsCount > 0) {
		int i;
		for (i = 0; i < queuedFdsCount; i++) {
			add_to_epoll(queuedFds[i], queuedOpType[i], opIsModify[i]);
		}
		queuedFdsCount = 0;
	}
	pthread_mutex_unlock(&lock);

	int nevents = ff_epoll_wait(epfd, events, MAX_EVENTS, 0);
	int i;

	// printf("Got %d events\n", nevents);

	for (i = 0; i < nevents; ++i) {
		record_tracepoint(17);
		// printf("Got event on fd %d\n", events[i].data.fd);
		/* Handle new connect */
		if (events[i].data.fd == sockfd) {
			while (1) {
				int nclientfd = ff_accept(sockfd, NULL, NULL);
				if (nclientfd < 0) {
					break;
				}

				(*env)->CallVoidMethod(env, thisObject, acceptMethodId, nclientfd);
				break; // Somehow catch exception from Java side
			}
		} else { 
			if (events[i].events & EPOLLERR ) {
				/* Simply close socket */
				ff_epoll_ctl(epfd, EPOLL_CTL_DEL, events[i].data.fd, NULL);
				(*env)->CallVoidMethod(env, thisObject, deleteMethodId, events[i].data.fd);
				ff_close(events[i].data.fd);
			} else if (events[i].events & EPOLLHUP) {
                /* Simply close socket */
                ff_epoll_ctl(epfd, EPOLL_CTL_DEL,  events[i].data.fd, NULL);
				(*env)->CallVoidMethod(env, thisObject, deleteMethodId, events[i].data.fd);
                ff_close(events[i].data.fd);
            } else if (events[i].events & EPOLLIN) {
				(*env)->CallVoidMethod(env, thisObject, processMethodId, events[i].data.fd);
			} else if (events[i].events & EPOLLOUT) {
				(*env)->CallVoidMethod(env, thisObject, writeMethodId, events[i].data.fd);
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