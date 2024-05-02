CC = gcc
JC = javac
CFLAGS = -fPIC -O0 -g -gdwarf-2 -Wl,--no-undefined
TRACKER_PATH = /home/zxjcarrot/Workspace/networking/latencyBench
INCLUDE = -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux

EPOLL_FILE = org_voltcore_network_FSelect
SOCKET_FILE = org_voltcore_network_FSocket
CONN_FILE = org_voltcore_network_FSocketConn
TRACKER_FILE = org_voltcore_network_TimeTracker2
EPOLL_JFILE = FSelect
SOCKET_JFILE = FSocket
CONN_JFILE = FSocketConn
TRACKER_JFILE = TimeTracker2
TEST_JFILE = TestFSelect

ifeq ($(FF_PATH),)
	FF_PATH=/home/zxjcarrot/Workspace/networking/f-stack
endif

ifneq ($(shell pkg-config --exists libdpdk && echo 0),0)
	$(error "No installation of DPDK found, maybe you should export environment variable `PKG_CONFIG_PATH`")
endif

PKGCONF ?= pkg-config
CFLAGS_JNI += -O0 -g -gdwarf-2 $(shell $(PKGCONF) --cflags libdpdk)
INC_DIR = $(FF_PATH)/lib ${FF_PATH}/lib/libfstack.so
LIBS+= $(shell $(PKGCONF) --static --libs libdpdk)
# LIBS+= -L/home/zxjcarrot/Workspace/networking/f-stack/lib -Wl,--whole-archive,-lfstack,--no-whole-archive
LIBS+= -Wl,--no-whole-archive -lrt -lm -ldl -lcrypto -pthread -lnuma

echomake:
	$(CC) -c $(CFLAGS) $(CFLAGS_JNI) $(EPOLL_FILE).c -o $(EPOLL_FILE).o $(INCLUDE) -I$(TRACKER_PATH) -I$(INC_DIR)
	$(CC) -shared $(CFLAGS) -g -o libnative_epoll.so $(EPOLL_FILE).o $(TRACKER_PATH)/libtimetracker.so -lc $(LIBS) ${FF_PATH}/lib/libfstack.so

	$(CC) -c $(CFLAGS) $(CFLAGS_JNI) $(SOCKET_FILE).c -o $(SOCKET_FILE).o $(INCLUDE) -I$(INC_DIR)
	$(CC) -shared $(CFLAGS) -g -o libnative_socket.so $(SOCKET_FILE).o -lc $(LIBS) ${FF_PATH}/lib/libfstack.so

	$(CC) -c $(CFLAGS) $(CFLAGS_JNI) $(CONN_FILE).c -o $(CONN_FILE).o $(INCLUDE) -I$(INC_DIR)
	$(CC) -shared $(CFLAGS) -g -o libnative_socket_conn.so $(CONN_FILE).o -lc $(LIBS) ${FF_PATH}/lib/libfstack.so

	$(CC) -c $(CFLAGS) $(INCLUDE) -I$(TRACKER_PATH) $(TRACKER_FILE).c -o $(TRACKER_FILE).o
	$(CC) -shared $(CFLAGS) -g $(TRACKER_FILE).o $(TRACKER_PATH)/libtimetracker.so -o libtimetracker2.so
	
	$(JC) -cp ../../.. $(EPOLL_JFILE).java
	$(JC) $(SOCKET_JFILE).java
	$(JC) $(CONN_JFILE).java
	$(JC) $(TRACKER_JFILE).java
	$(JC) -cp ../../.. $(TEST_JFILE).java

.PHONY: clean

clean:
	rm -f *.class *.o *.so