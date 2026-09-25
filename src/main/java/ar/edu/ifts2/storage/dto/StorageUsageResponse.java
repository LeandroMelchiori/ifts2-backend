package ar.edu.ifts2.storage.dto;

import java.time.Instant;
import java.util.List;

public record StorageUsageResponse(long usedBytes, Long limitBytes, String scope, Instant measuredAt,
        List<Breakdown> breakdown) {
    public record Breakdown(String key, String label, long bytes, long files) { }
}
