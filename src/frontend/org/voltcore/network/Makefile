CC = gcc
JC = javac
CFLAGS = -fPIC -O0 -g -gdwarf-2
INCLUDE = -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux
EPOLL_FILE = org_voltcore_network_FSelect
SOCKET_FILE = org_voltcore_network_FSocket
CONN_FILE = org_voltcore_network_FSocketConn
EPOLL_JFILE = FSelect
SOCKET_JFILE = FSocket
CONN_JFILE = FSocketConn
TEST_JFILE = TestFSelect

echomake:
	$(CC) -c $(CFLAGS) $(INCLUDE) $(EPOLL_FILE).c -o $(EPOLL_FILE).o $(INCLUDE)
	$(CC) -shared $(CFLAGS) -g -o libnative_epoll.so $(EPOLL_FILE).o -lc

	$(CC) -c $(CFLAGS) $(INCLUDE) $(SOCKET_FILE).c -o $(SOCKET_FILE).o $(INCLUDE)
	$(CC) -shared $(CFLAGS) -g -o libnative_socket.so $(SOCKET_FILE).o -lc

	$(CC) -c $(CFLAGS) $(INCLUDE) $(CONN_FILE).c -o $(CONN_FILE).o $(INCLUDE)
	$(CC) -shared $(CFLAGS) -g -o libnative_socket_conn.so $(CONN_FILE).o -lc
	
	$(JC) -cp ../../.. $(EPOLL_JFILE).java
	$(JC) $(SOCKET_JFILE).java
	$(JC) $(CONN_JFILE).java
	$(JC) -cp ../../.. $(TEST_JFILE).java

.PHONY: clean

clean:
	rm -f *.class *.o *.so