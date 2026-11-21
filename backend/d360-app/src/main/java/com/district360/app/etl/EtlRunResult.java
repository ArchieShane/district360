package com.district360.app.etl;

import java.time.LocalDate;

public record EtlRunResult(String source, String status, Integer rowCount, LocalDate sourceAsOf, long durationMs, String message) {
}
