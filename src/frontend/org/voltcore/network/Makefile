CC = g++
JC = javac
CFLAGS = -fPIC -O0 -g -gdwarf-2 -Wl,--no-undefined
TRACKER_PATH = /home/zxjcarrot/Workspace/networking/latencyBench
INCLUDE = -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -I${TRACKER_PATH}

CFILE = org_voltcore_network_TimeTracker2
JFILE = TimeTracker2
# LIBS+= -L/home/zxjcarrot/Workspace/networking/f-stack/lib -Wl,--whole-archive,-lfstack,--no-whole-archive
LIBS+= -lpapi $(TRACKER_PATH)/libtimetracker.so

echomake:
	$(CC) -c $(CFLAGS) $(INCLUDE) $(CFILE).cpp -o $(CFILE).o
	$(CC) -shared $(CFLAGS) -g $(CFILE).o -L$(TRACKER_PATH) $(LIBS) -o libtimetracker2.so
	
	$(JC) $(JFILE).java

.PHONY: clean

clean:
	rm -f *.class *.o *.so