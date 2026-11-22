package com.iitsm.api;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iitsm.infrastructure.clock.AdjustableClock;

@RestController
@RequestMapping("/api/test/clock")
public class TestClockController {

    private final Clock clock;

    public TestClockController(Clock clock) {
        this.clock = clock;
    }

    @PostMapping("/advance")
    public Map<String, Object> advance(@RequestParam(name = "seconds", defaultValue = "270") long seconds) {
        if (clock instanceof AdjustableClock adj) {
            adj.advance(Duration.ofSeconds(seconds));
            return Map.of("offsetSeconds", adj.getOffset().toSeconds(), "now", adj.instant().toString());
        }
        return Map.of("now", clock.instant().toString());
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        if (clock instanceof AdjustableClock adj) {
            adj.reset();
            return Map.of("offsetSeconds", 0, "now", adj.instant().toString());
        }
        return Map.of("now", clock.instant().toString());
    }
}
