CC = gcc
JC = javac
CFLAGS = -fPIC -O0 -g -gdwarf-2
INCLUDE = -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux
NATIVE_FILENAME = org_voltcore_network_FSelect
JAVA_FILENAME = FSelect
TEST_FILENAME = TestFSelect

echomake:
	$(CC) -c $(CFLAGS) $(INCLUDE) $(NATIVE_FILENAME).c -o $(NATIVE_FILENAME).o $(INCLUDE)
	$(CC) -shared $(CFLAGS) -g -o libnative.so $(NATIVE_FILENAME).o -lc
	# $(JC) $(JAVA_FILENAME).java
	# $(JC) -cp ../../.. $(TEST_FILENAME).java

.PHONY: clean

clean:
	rm -f *.class *.o *.so