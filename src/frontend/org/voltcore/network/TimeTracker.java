package org.voltcore.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class TimeTracker {
    public enum TrackingEvent {
        RcvSPRequest,
        SndSPRequestToSPVM,
        RcvSQLRequest,
        SndSQLResponse,
        RcvSPResponseFromSPVM,
        SndSPResponse
    }
    static final Map<TrackingEvent, List<Long>> statsBuffer = new HashMap<>();
    static long startTime;
    TimeTracker() {}

    public static void add(TrackingEvent event, Long time) {
        long relativeTime;
        if (event.equals(TrackingEvent.RcvSPRequest)) {
            relativeTime = 0l;
            startTime = time;
        } else {
            relativeTime = time - startTime;
        }
        if (!statsBuffer.containsKey(event))
            statsBuffer.put(event, new ArrayList<>());
        statsBuffer.get(event).add(relativeTime);
    }

    public static void printTimeStats() {
        System.out.println("---- Timing stats ----");
        for (TrackingEvent event: statsBuffer.keySet()) {
            long total = 0;
            for (long time: statsBuffer.get(event)) {
                total += time;
            }
            System.out.printf("%-25s: %d\n", event.name(), total / statsBuffer.get(event).size());
        }
        System.out.println("---- ============ ----");
    }

    public static void reset() {
        statsBuffer.clear();
    }
}
