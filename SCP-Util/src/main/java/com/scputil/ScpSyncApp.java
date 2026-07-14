package com.scputil;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Minimalist Swing front-end. A button triggers a sync: it scans the configured
 * local folder for files modified since the last run and copies them to the
 * mirror folder on the remote Ubuntu host over SCP. A route toggle lets the user
 * choose the OpenSSH ({@code scp}) or PuTTY ({@code pscp}) transport at runtime;
 * it is seeded from {@code config.json} and overrides the config's {@code mode}.
 *
 * <p>{@code config.json} and {@code lastrun.json} both live in the same
 * directory as the running program (the jar's directory, falling back to the
 * working directory).</p>
 */
public class ScpSyncApp {

    private static final String CONFIG_FILE = "config.json";
    private static final String STATE_FILE = "lastrun.json";

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Path appDir;
    private final JFrame frame;
    private final JButton syncButton;
    private final JRadioButton opensshRadio;
    private final JRadioButton puttyRadio;
    private final JLabel statusLabel;
    private final JTextArea logArea;

    public ScpSyncApp() {
        this.appDir = resolveAppDir();

        frame = new JFrame("SCP Sync");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(560, 420));

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        root.setBackground(Color.WHITE);

        // --- Top: the minimalist action button plus the transport route toggle ---
        syncButton = new JButton("Sync Now");
        syncButton.setFont(syncButton.getFont().deriveFont(Font.BOLD, 15f));
        syncButton.setFocusPainted(false);
        syncButton.setMargin(new Insets(10, 24, 10, 24));
        syncButton.addActionListener(e -> runSync());

        opensshRadio = new JRadioButton("OpenSSH (scp)");
        puttyRadio = new JRadioButton("PuTTY (pscp)");
        opensshRadio.setBackground(Color.WHITE);
        puttyRadio.setBackground(Color.WHITE);
        ButtonGroup routeGroup = new ButtonGroup();
        routeGroup.add(opensshRadio);
        routeGroup.add(puttyRadio);
        // Seed the selected route from config.json; default to OpenSSH if unreadable.
        if (initialModeFromConfig() == Config.Mode.PUTTY) {
            puttyRadio.setSelected(true);
        } else {
            opensshRadio.setSelected(true);
        }

        JPanel routePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        routePanel.setBackground(Color.WHITE);
        JLabel routeLabel = new JLabel("Route:");
        routeLabel.setForeground(new Color(0x55, 0x55, 0x55));
        routePanel.add(routeLabel);
        routePanel.add(opensshRadio);
        routePanel.add(puttyRadio);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.add(syncButton, BorderLayout.WEST);
        top.add(routePanel, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        // --- Center: activity log ---
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0xDD, 0xDD, 0xDD)));
        root.add(scroll, BorderLayout.CENTER);

        // --- Bottom: status line ---
        statusLabel = new JLabel("Ready.");
        statusLabel.setForeground(new Color(0x55, 0x55, 0x55));
        root.add(statusLabel, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);

        log("Program directory: " + appDir);
        log("Reading config from: " + appDir.resolve(CONFIG_FILE));
    }

    private void show() {
        frame.setVisible(true);
    }

    /** Kicks off a sync on a background thread so the UI stays responsive. */
    private void runSync() {
        syncButton.setEnabled(false);
        setStatus("Working...");

        // Capture the moment the run starts. On success this becomes the new
        // "last run" mark, so files touched during the transfer are picked up
        // on the next run rather than being silently skipped.
        final long runStart = System.currentTimeMillis();

        // Read the route toggle here, on the EDT, before going off-thread.
        final Config.Mode route = puttyRadio.isSelected() ? Config.Mode.PUTTY : Config.Mode.OPENSSH;

        new SwingWorker<FileSyncService.Result, String>() {
            private String error;

            @Override
            protected FileSyncService.Result doInBackground() {
                try {
                    Config config = Config.load(appDir.resolve(CONFIG_FILE));
                    // The in-window toggle wins over config.json's mode.
                    config.mode = (route == Config.Mode.PUTTY) ? "putty" : "openssh";
                    SyncState state = SyncState.load(appDir.resolve(STATE_FILE));

                    publish("--- Sync started at " + TS.format(Instant.ofEpochMilli(runStart))
                            + " via " + route + " (" + config.resolvedScpCommand() + ") ---");
                    if (state.lastRunEpochMillis == 0) {
                        publish("No previous run recorded; copying all files.");
                    } else {
                        publish("Copying files modified after "
                                + TS.format(Instant.ofEpochMilli(state.lastRunEpochMillis)) + ".");
                    }

                    FileSyncService service = new FileSyncService(config, this::publish);
                    FileSyncService.Result result = service.run(state.lastRunEpochMillis);

                    // Only advance the marker when nothing failed, so failed
                    // files are retried next time.
                    if (result.failed == 0) {
                        state.lastRunEpochMillis = runStart;
                        state.save(appDir.resolve(STATE_FILE));
                    }
                    return result;
                } catch (Exception e) {
                    error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                chunks.forEach(ScpSyncApp.this::log);
            }

            @Override
            protected void done() {
                syncButton.setEnabled(true);
                if (error != null) {
                    log("ERROR: " + error);
                    setStatus("Error: " + error);
                    return;
                }
                FileSyncService.Result result;
                try {
                    result = get();
                } catch (Exception e) {
                    log("ERROR: " + e.getMessage());
                    setStatus("Error: " + e.getMessage());
                    return;
                }
                String summary = String.format(
                        "Done. Scanned %d, copied %d, failed %d.",
                        result.scanned, result.transferred, result.failed);
                log(summary);
                if (result.failed == 0) {
                    log("Last-run marker updated.");
                } else {
                    log("Last-run marker NOT updated (failures will be retried next run).");
                }
                setStatus(summary);
            }
        }.execute();
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + System.lineSeparator());
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    /**
     * Best-effort read of the route from {@code config.json} to seed the toggle
     * at startup. Any problem (missing/invalid config) falls back to OpenSSH.
     */
    private Config.Mode initialModeFromConfig() {
        try {
            return Config.load(appDir.resolve(CONFIG_FILE)).effectiveMode();
        } catch (Exception e) {
            return Config.Mode.OPENSSH;
        }
    }

    /** Directory containing the running jar; falls back to the working directory. */
    private static Path resolveAppDir() {
        try {
            Path location = Path.of(
                    ScpSyncApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (java.nio.file.Files.isRegularFile(location)) {
                return location.getParent(); // location is the jar file itself
            }
            return location; // running from a classes directory (e.g. in the IDE)
        } catch (URISyntaxException | NullPointerException e) {
            return Path.of(System.getProperty("user.dir"));
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to the default look and feel.
        }
        SwingUtilities.invokeLater(() -> new ScpSyncApp().show());
    }
}
