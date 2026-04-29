package com.attention.controller;

import com.attention.service.LogsAnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final LogsAnalyticsService service;

    public AnalyticsController(LogsAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "logsDirExists", service.logsExist()
        );
    }

    @GetMapping("/days")
    public List<DaySwitches> days() {
        return service.switchesPerDay().entrySet().stream()
                .map(e -> new DaySwitches(e.getKey(), e.getValue()))
                .toList();
    }

    @GetMapping("/top-processes")
    public Map<String, Integer> topProcesses(@RequestParam(defaultValue = "10") int limit) {
        return service.topProcesses(limit);
    }

    @GetMapping("/summary")
    public LogsAnalyticsService.Summary summary() {
        return service.summary();
    }

    public record DaySwitches(LocalDate date, int switches) {}
}
