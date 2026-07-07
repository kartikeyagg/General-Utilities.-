package com.scputil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime configuration, loaded from {@code config.json} which lives in the
 * same directory as the running program.
 *
 * <p>The local folder and the remote (mirror) folder share the same name, e.g.
 * {@code C:\work\Dir1} locally maps to {@code /home/ubuntu/Dir1} remotely.</p>
 */
public class Config {

    /** Absolute path of the local folder to watch, e.g. {@code C:\work\Dir1}. */
    public String localFolder;

    /** Absolute path of the mirror folder on the remote host, e.g. {@code /home/ubuntu/Dir1}. */
    public String mirrorFolder;

    /** Remote Ubuntu host IP or hostname. */
    public String remoteIp;

    /** SSH username on the remote host. */
    public String username;

    /** SSH password on the remote host. */
    public String password;

    /** Remote SSH port. Defaults to 22 when omitted from the json. */
    public int port = 22;

    /**
     * Executable used to copy files with a password on the command line.
     * On Windows this is typically PuTTY's {@code pscp} (accepts {@code -pw}).
     * Defaults to {@code pscp}.
     */
    public String scpCommand = "pscp";

    /**
     * Executable used to run remote shell commands (to create parent dirs)
     * with a password. On Windows this is typically PuTTY's {@code plink}.
     * Defaults to {@code plink}.
     */
    public String sshCommand = "plink";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Reads and validates the config from the given json file. */
    public static Config load(Path jsonFile) throws IOException {
        if (!Files.exists(jsonFile)) {
            throw new IOException("Config file not found: " + jsonFile.toAbsolutePath()
                    + System.lineSeparator()
                    + "Create a config.json next to the program (see README).");
        }
        Config cfg;
        try (Reader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
            cfg = GSON.fromJson(reader, Config.class);
        }
        if (cfg == null) {
            throw new IOException("Config file is empty or malformed: " + jsonFile.toAbsolutePath());
        }
        cfg.validate(jsonFile);
        return cfg;
    }

    private void validate(Path jsonFile) throws IOException {
        StringBuilder missing = new StringBuilder();
        if (isBlank(localFolder)) missing.append(" localFolder");
        if (isBlank(mirrorFolder)) missing.append(" mirrorFolder");
        if (isBlank(remoteIp)) missing.append(" remoteIp");
        if (isBlank(username)) missing.append(" username");
        if (isBlank(password)) missing.append(" password");
        if (missing.length() > 0) {
            throw new IOException("Config file " + jsonFile.toAbsolutePath()
                    + " is missing required fields:" + missing);
        }
        if (port <= 0) port = 22;
        if (isBlank(scpCommand)) scpCommand = "pscp";
        if (isBlank(sshCommand)) sshCommand = "plink";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
