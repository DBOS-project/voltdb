package org.voltcore.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class TimeTracker {
    public enum TrackingEvent {
        RcvSPRequest,
        StartHandleSPRequest,
        SndSPRequestToSPVM,
        RcvSQLRequest,
        SndSQLResponse,
        RcvSPResponseFromSPVM,
        FinishHandleSPRequest,
        SndSPResponse
    }
    static final List<TrackingEvent> eventsInOrder = Arrays.asList(
        TrackingEvent.RcvSPRequest,
        TrackingEvent.StartHandleSPRequest,
        TrackingEvent.SndSPRequestToSPVM,
        TrackingEvent.RcvSQLRequest,
        TrackingEvent.SndSQLResponse,
        TrackingEvent.RcvSPResponseFromSPVM,
        TrackingEvent.FinishHandleSPRequest,
        TrackingEvent.SndSPResponse
    );
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
        // System.out.println(event.name());
        if (!statsBuffer.containsKey(event))
            statsBuffer.put(event, new ArrayList<>());
        statsBuffer.get(event).add(relativeTime);
    }

    public static void printTimeStats() {
        System.out.println("---- Timing stats ----");
        System.out.printf("%-25s: %-10s %-15s %-15s %s\n", "Event", "n", "Average time (ns)", "Median time (ns)", "CV");
        for (TrackingEvent event: eventsInOrder) {
            if (!statsBuffer.containsKey(event)) {
                continue;
            }
            long mean = (long) getMean(statsBuffer.get(event));
            long median = (long) getMedian(statsBuffer.get(event));
            double cv = getCoefficientOfVariation(statsBuffer.get(event), mean);
            System.out.printf("%-25s: %-10d %-15d %-15d %.2f\n", event.name(), statsBuffer.get(event).size(), mean, median, cv);
        }
        System.out.println("---- ============ ----");
    }

    public static void reset() {
        statsBuffer.clear();
        startTime = 0;
    }

    public static double getMean(List<Long> sets) {
        double sum = 0;
        for (long time: sets) {
            sum += time;
        }
        return sum / sets.size();
    }

    public static double getMedian(List<Long> sets) {
        Collections.sort(sets);

        int middle = sets.size()/2;
        double median = 0;
        if (sets.size()%2 == 1) {
            median = (sets.get(middle) + sets.get(middle - 1))/2;
        } else {
            median = sets.get(middle);
        }
        return median;
    }

    public static double getCoefficientOfVariation(List<Long> sets, double mean) {
        double sum = 0;
        for (long time: sets) {
            sum += Math.pow(time - mean, 2);
        }
        double sd = Math.sqrt(sum / sets.size());
        return sd / mean;
    }
}
