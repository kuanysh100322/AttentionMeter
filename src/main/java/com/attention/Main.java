package com.attention;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {

    private static final int POLL_MS = 2000;
    private static final int HEARTBEAT_EVERY_N_POLLS = 15;
    private static Path csvFileForToday() {
        Path dir = Path.of("logs");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir.resolve(LocalDate.now().toString() + ".csv");
    }


    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ====== STATE ======
    private static ActiveWindow prev = null;
    private static int switches = 0;
    private static int polls = 0;

    public static void main(String[] args) throws InterruptedException {
        ensureCsvHeader();

        System.out.println("Attention Meter started...");
        System.out.println("CSV path: " + csvFileForToday().toAbsolutePath());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logEvent("STOP", prev, switches);
            System.out.println(ts() + " STOP");
        }));

        // Initialize start (wait until the first active window appears)
        while (prev == null) {
            prev = readActiveWindow();
            Thread.sleep(200);
        }
        logEvent("START", prev, switches);

        while (true) {
            ActiveWindow cur = readActiveWindow();
            if (cur != null) {
                polls++;

                if (!cur.equals(prev)) {
                    switches++;
                    logEvent("SWITCH", cur, switches);
                    System.out.println(ts() + " SWITCH #" + switches + " -> " + cur);
                    prev = cur;
                } else {
                    if (polls % HEARTBEAT_EVERY_N_POLLS == 0) {
                        logEvent("HEARTBEAT", cur, switches);
                        System.out.println(ts() + " HEARTBEAT -> " + cur);
                    }
                }
            }

            Thread.sleep(POLL_MS);
        }
    }

    // ====== ACTIVE WINDOW READ ======

    private static ActiveWindow readActiveWindow() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) return null;

        char[] titleBuf = new char[512];
        User32.INSTANCE.GetWindowText(hwnd, titleBuf, 512);
        String title = Native.toString(titleBuf).trim();

        IntByReference pidRef = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
        int pid = pidRef.getValue();

        String process = readProcessName(pid);

        return new ActiveWindow(process, title);
    }

    private static String readProcessName(int pid) {
        WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ,
                false,
                pid
        );
        if (hProcess == null) return "pid=" + pid;

        try {
            char[] path = new char[1024];
            int len = Psapi.INSTANCE.GetProcessImageFileName(hProcess, path, path.length);
            if (len == 0) return "pid=" + pid;

            String full = new String(path, 0, len);

            int lastSlash = Math.max(full.lastIndexOf('\\'), full.lastIndexOf('/'));
            return (lastSlash >= 0) ? full.substring(lastSlash + 1) : full;
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess);
        }
    }

    // ====== CSV LOGGING ======

    private static void ensureCsvHeader() {
        // If the file does not exist, create it and add the header
        if (Files.exists(csvFileForToday())) return;

        try (BufferedWriter w = Files.newBufferedWriter(
                csvFileForToday(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            w.write("timestamp,event,process,title,switchNumber");
            w.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write CSV header: " + e.getMessage());
        }
    }

    private static void logEvent(String event, ActiveWindow win, int switchNumber) {
        if (win == null) return;

        String line = csv(
                LocalDateTime.now().format(TS),
                event,
                win.process(),
                win.title(),
                String.valueOf(switchNumber)
        );

        try (BufferedWriter w = Files.newBufferedWriter(
                csvFileForToday(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            w.write(line);
            w.newLine();
        } catch (IOException e) {
            System.err.println(ts() + " CSV LOG ERROR: " + e.getMessage());
        }
    }

    private static String csv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvField(fields[i]));
        }
        return sb.toString();
    }

    private static String csvField(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return needQuotes ? ("\"" + v + "\"") : v;
    }

    private static String ts() {
        return "[" + LocalDateTime.now().format(TS) + "]";
    }

    private record ActiveWindow(String process, String title) {
        @Override
        public String toString() {
            return process + " | " + (title == null || title.isBlank() ? "(no title)" : title);
        }
    }
}
