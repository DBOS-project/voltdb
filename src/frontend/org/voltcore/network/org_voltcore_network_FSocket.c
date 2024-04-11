#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <sys/ioctl.h>

#include "org_voltcore_network_FSocket.h"

#define MAX_EVENTS 512

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSocket_openAndBind
  (JNIEnv *env, jobject thisObject, jint j_port) {
    int port = (int) j_port;
    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    // printf("sockfd:%d\n", sockfd);
    if (sockfd < 0) {
        perror("socket failed");
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
        perror("bind failed");
        exit(1);
    }

    ret = listen(sockfd, MAX_EVENTS);
    if (ret < 0) {
        perror("listen failed");
        exit(1);
    }
    return sockfd;
  }

JNIEXPORT jint JNICALL Java_org_voltcore_network_FSocket_accept
  (JNIEnv *env, jobject thisObject, jint sock_fd) {
    struct sockaddr_in client_addr;
    socklen_t addrlen = sizeof(client_addr);
    while (1) {
      int client_sock = accept(sock_fd, (struct sockaddr *)&client_addr, &addrlen);
      // Error check
      if (client_sock < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
          // Resource temporarily unavailable, retry
          continue;
        }
        perror("accept failed");
        exit(1);
      }
      return client_sock;
	}
  }

JNIEXPORT void JNICALL Java_org_voltcore_network_FSocket_close
  (JNIEnv *env, jobject thisObject, jint sock_fd) {
    close(sock_fd);
  }