package de.cooperr.chaos.loadgenerator.dto;

import java.util.Map;

public record TargetStatus(String url,
                           long sent, long inFlight,
                           Map<Integer, Long> statusCounts,
                           long timeouts, long connectionErrors,
                           long averageDurationMs, long maxDurationMs) {
}
