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
 *
 * <p>Two transports are supported:</p>
 * <ul>
 *   <li><b>openssh</b> (default when {@link #remoteHost} is set): uses the
 *       system {@code ssh}/{@code scp} against an SSH host <i>alias</i> from
 *       {@code ~/.ssh/config} (i.e. you normally run {@code ssh myhost}). This
 *       relies on key-based auth already configured for that alias &mdash; no
 *       password is placed on the command line.</li>
 *   <li><b>putty</b> (default when {@link #remoteHost} is empty): uses PuTTY's
 *       {@code pscp}/{@code plink} with {@code username@remoteIp} and a
 *       {@code -pw} password.</li>
 * </ul>
 */
public class Config {

    public enum Mode { OPENSSH, PUTTY }

    /** Absolute path of the local folder to watch, e.g. {@code C:\work\Dir1}. */
    public String localFolder;

    /** Absolute path of the mirror folder on the remote host, e.g. {@code /home/ubuntu/Dir1}. */
    public String mirrorFolder;

    /**
     * SSH host alias, exactly as you type it in {@code ssh <alias>} (resolved
     * from {@code ~/.ssh/config} or a saved session). When set, the OpenSSH
     * transport is used and {@link #remoteIp}/{@link #username}/{@link #password}
     * are ignored.
     */
    public String remoteHost;

    /** Remote Ubuntu host IP or hostname (used only when {@link #remoteHost} is empty). */
    public String remoteIp;

    /** SSH username (used only when {@link #remoteHost} is empty). */
    public String username;

    /** SSH password (used only by the PuTTY transport). */
    public String password;

    /**
     * Remote SSH port. 0 (the default) means "unspecified" &mdash; no port flag
     * is passed, so the SSH client's own default / alias config decides.
     */
    public int port = 0;

    /**
     * Transport override: {@code "openssh"} or {@code "putty"}. When null/blank
     * the mode is inferred: OpenSSH if {@link #remoteHost} is set, else PuTTY.
     */
    public String mode;

    /** SCP executable. Defaults to {@code scp} (openssh) or {@code pscp} (putty). */
    public String scpCommand;

    /** SSH executable used for {@code mkdir -p}. Defaults to {@code ssh} or {@code plink}. */
    public String sshCommand;

    /**
     * When true, non-interactive/batch flags are added so a run fails fast
     * instead of hanging on a password prompt. Defaults to true.
     */
    public Boolean batchMode = Boolean.TRUE;

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
        cfg.applyDefaults();
        cfg.validate(jsonFile);
        return cfg;
    }

    /** The effective transport after inference. */
    public Mode effectiveMode() {
        if (mode != null && mode.trim().equalsIgnoreCase("putty")) return Mode.PUTTY;
        if (mode != null && mode.trim().equalsIgnoreCase("openssh")) return Mode.OPENSSH;
        return isBlank(remoteHost) ? Mode.PUTTY : Mode.OPENSSH;
    }

    /** True when the batch/non-interactive flags should be used. */
    public boolean useBatchMode() {
        return batchMode == null || batchMode;
    }

    /**
     * The scp/ssh destination for the current mode: an alias in OpenSSH mode,
     * or {@code username@remoteIp} in PuTTY mode.
     */
    public String remoteTarget() {
        return effectiveMode() == Mode.OPENSSH && !isBlank(remoteHost)
                ? remoteHost.trim()
                : username + "@" + remoteIp;
    }

    private void applyDefaults() {
        Mode m = effectiveMode();
        if (isBlank(scpCommand)) scpCommand = (m == Mode.OPENSSH) ? "scp" : "pscp";
        if (isBlank(sshCommand)) sshCommand = (m == Mode.OPENSSH) ? "ssh" : "plink";
        if (port < 0) port = 0;
    }

    private void validate(Path jsonFile) throws IOException {
        StringBuilder missing = new StringBuilder();
        if (isBlank(localFolder)) missing.append(" localFolder");
        if (isBlank(mirrorFolder)) missing.append(" mirrorFolder");

        if (effectiveMode() == Mode.OPENSSH) {
            // Alias mode: need an alias, unless an explicit user@ip was given.
            if (isBlank(remoteHost) && (isBlank(remoteIp) || isBlank(username))) {
                missing.append(" remoteHost (or remoteIp+username)");
            }
        } else {
            // PuTTY mode: need user, ip and password on the command line.
            if (isBlank(remoteIp)) missing.append(" remoteIp");
            if (isBlank(username)) missing.append(" username");
            if (isBlank(password)) missing.append(" password");
        }

        if (missing.length() > 0) {
            throw new IOException("Config file " + jsonFile.toAbsolutePath()
                    + " is missing required fields for " + effectiveMode()
                    + " mode:" + missing);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
