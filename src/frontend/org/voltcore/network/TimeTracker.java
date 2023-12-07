package org.voltcore.network;

import java.util.ArrayList;
import java.util.Collections;
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
        System.out.printf("%-25s: %-12s %s\n", "Event", "Average time (ms)", "Median time (ms)");
        for (TrackingEvent event: statsBuffer.keySet()) {
            long total = 0;
            for (long time: statsBuffer.get(event)) {
                total += time;
            }
            System.out.printf("%-25s: %-12d %d\n", event.name(), total / statsBuffer.get(event).size(), (long) getMedian((ArrayList<Long>) statsBuffer.get(event)));
        }
        System.out.println("---- ============ ----");
    }

    public static void reset() {
        statsBuffer.clear();
    }

    public static double getMedian(ArrayList<Long> sets) {
        Collections.sort(sets);

        double middle = sets.size()/2;
            if (sets.size()%2 == 1) {
                middle = (sets.get(sets.size()/2) + sets.get(sets.size()/2 - 1))/2;
            } else {
                middle = sets.get(sets.size() / 2);
            }
        return middle;
    }
}
