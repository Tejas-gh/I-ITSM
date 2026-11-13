package com.iitsm.api.dto;

import java.time.Instant;
import java.util.List;

/** Error body of CONTRACT_FREEZE.md §U.1: {@code {timestamp, status, code, message, details}}. */
public record ApiError(Instant timestamp, int status, String code, String message, List<String> details) {
}
