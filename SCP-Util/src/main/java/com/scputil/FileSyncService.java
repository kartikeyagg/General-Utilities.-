package com.scputil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Walks the local folder (recursively, including sub-directories), finds every
 * file modified since the last run, and copies each one to the mirror location
 * on the remote Ubuntu host over SCP.
 *
 * <p>The remote host is never inspected or modified apart from the file copy
 * itself &mdash; there are no {@code mkdir}, status or listing commands. The
 * tool knows only the target folder path, so the remote mirror folder tree must
 * already exist; a file whose remote sub-folder is missing simply fails and is
 * retried on the next run.</p>
 *
 * <p>The copy is driven by shelling out to an external tool: the system
 * {@code scp} (OpenSSH route) or PuTTY's {@code pscp} (PuTTY route, password via
 * {@code -pw}). The route is chosen in {@code config.json} or at runtime.</p>
 */
public class FileSyncService {

    private final Config config;
    private final Consumer<String> log;

    public FileSyncService(Config config, Consumer<String> log) {
        this.config = config;
        this.log = log;
    }

    /** Result of a sync run. */
    public static class Result {
        public int transferred;
        public int failed;
        public int scanned;
    }

    /**
     * Runs the sync.
     *
     * @param sinceEpochMillis only files modified strictly after this instant are copied;
     *                         pass 0 to copy everything (first run).
     * @return summary counts
     */
    public Result run(long sinceEpochMillis) throws IOException {
        Path localRoot = Path.of(config.localFolder);
        if (!Files.isDirectory(localRoot)) {
            throw new IOException("Local folder does not exist or is not a directory: " + localRoot);
        }

        Result result = new Result();
        List<Path> modified = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(localRoot)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            result.scanned = files.size();
            for (Path file : files) {
                long modifiedAt = Files.getLastModifiedTime(file).toMillis();
                if (modifiedAt > sinceEpochMillis) {
                    modified.add(file);
                }
            }
        }

        if (modified.isEmpty()) {
            log.accept("No files modified since the last run. Nothing to copy.");
            return result;
        }

        log.accept(modified.size() + " modified file(s) to copy:");

        // The remote is never inspected or modified apart from the copy itself,
        // so the destination folder tree must already exist on the remote. A
        // file whose remote sub-folder is missing simply fails and is retried
        // on the next run.
        for (Path file : modified) {
            Path relative = localRoot.relativize(file);
            String remotePath = toRemotePath(relative);

            try {
                copyFile(file, remotePath);
                result.transferred++;
                log.accept("  [ok] " + relative);
            } catch (Exception e) {
                result.failed++;
                log.accept("  [FAILED] " + relative + " -> " + e.getMessage());
            }
        }

        return result;
    }

    /** Converts a local relative path into a forward-slash remote path under the mirror folder. */
    private String toRemotePath(Path relative) {
        String rel = relative.toString().replace('\\', '/');
        String base = stripTrailingSlash(config.mirrorFolder);
        return base + "/" + rel;
    }

    private static String stripTrailingSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    /** Copies a single local file to the given remote path via scp. */
    private void copyFile(Path localFile, String remotePath) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(config.resolvedScpCommand());
        // Both scp and pscp use uppercase -P for the port.
        if (config.port > 0) {
            cmd.add("-P");
            cmd.add(String.valueOf(config.port));
        }
        addAuthAndBatchFlags(cmd);
        cmd.add(localFile.toString());
        cmd.add(config.remoteTarget() + ":" + remotePath);
        exec(cmd, "copy " + localFile.getFileName());
    }

    /**
     * Appends the transport-specific auth and non-interactive flags.
     * OpenSSH relies on key auth (no password on the command line) and uses
     * {@code -o BatchMode=yes}; PuTTY takes the password via {@code -pw} and
     * uses {@code -batch}.
     */
    private void addAuthAndBatchFlags(List<String> cmd) {
        if (config.effectiveMode() == Config.Mode.OPENSSH) {
            if (config.useBatchMode()) {
                cmd.add("-o");
                cmd.add("BatchMode=yes");
            }
        } else {
            cmd.add("-pw");
            cmd.add(config.password);
            if (config.useBatchMode()) {
                cmd.add("-batch");
            }
        }
    }

    /** Runs an external command and throws with captured output on non-zero exit. */
    private void exec(List<String> cmd, String description) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException("Could not start '" + cmd.get(0)
                    + "'. Is it installed and on the PATH? (" + e.getMessage() + ")", e);
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timed out trying to " + description);
        }
        int code = process.exitValue();
        if (code != 0) {
            throw new IOException("Failed to " + description + " (exit " + code + "): "
                    + output.toString().trim());
        }
    }
}
