package com.attention.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Service
public class LogsAnalyticsService {

    private static final Path LOGS_DIR = Path.of("logs");

    public boolean logsExist() {
        return Files.exists(LOGS_DIR) && Files.isDirectory(LOGS_DIR);
    }

    public List<LocalDate> listDays() {
        if (!logsExist()) return List.of();

        try {
            return Files.list(LOGS_DIR)
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .map(p -> LocalDate.parse(p.getFileName().toString().replace(".csv", "")))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Map<LocalDate, Integer> switchesPerDay() {
        Map<LocalDate, Integer> result = new TreeMap<>();
        for (LocalDate day : listDays()) {
            Path file = LOGS_DIR.resolve(day + ".csv");
            result.put(day, countEvent(file, "SWITCH"));
        }
        return result;
    }

    public Map<String, Integer> topProcesses(int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (LocalDate day : listDays()) {
            Path file = LOGS_DIR.resolve(day + ".csv");
            mergeProcessCounts(file, counts);
        }

        return counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, limit))
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll);
    }

    public Summary summary() {
        int files = 0, rows = 0, switches = 0, heartbeats = 0;

        for (LocalDate day : listDays()) {
            files++;
            Path file = LOGS_DIR.resolve(day + ".csv");
            FileStats s = readFileStats(file);
            rows += s.rows;
            switches += s.switches;
            heartbeats += s.heartbeats;
        }

        return new Summary(files, rows, switches, heartbeats);
    }

    // ---------- helpers ----------

    private int countEvent(Path file, String event) {
        FileStats s = readFileStats(file);
        return "SWITCH".equals(event) ? s.switches : s.heartbeats;
    }

    private FileStats readFileStats(Path file) {
        int rows = 0, switches = 0, heartbeats = 0;

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;

            while ((line = r.readLine()) != null) {
                if (first) { first = false; continue; } // header
                if (line.isBlank()) continue;

                rows++;

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 5) continue;

                String event = cols.get(1);

                if ("SWITCH".equals(event)) switches++;
                else if ("HEARTBEAT".equals(event)) heartbeats++;
            }
        } catch (IOException ignored) {}

        return new FileStats(rows, switches, heartbeats);
    }

    private void mergeProcessCounts(Path file, Map<String, Integer> counts) {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;

            while ((line = r.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.isBlank()) continue;

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 5) continue;

                String event = cols.get(1);
                String process = cols.get(2);

                if ("SWITCH".equals(event)) {
                    counts.merge(process, 1, Integer::sum);
                }
            }
        } catch (IOException ignored) {}
    }

    // CSV parser
    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private record FileStats(int rows, int switches, int heartbeats) {}

    public record Summary(int files, int rows, int switches, int heartbeats) {}
}
