package com.scputil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent record of when the program last ran, stored in {@code lastrun.json}
 * next to the program. Files whose last-modified time is newer than this value
 * are considered "modified since the previous run".
 */
public class SyncState {

    /** Epoch milliseconds of the last successful run. 0 means "never ran". */
    public long lastRunEpochMillis = 0L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Loads state, returning a fresh (never-ran) state when the file is absent or unreadable. */
    public static SyncState load(Path jsonFile) {
        if (!Files.exists(jsonFile)) {
            return new SyncState();
        }
        try (Reader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
            SyncState state = GSON.fromJson(reader, SyncState.class);
            return state != null ? state : new SyncState();
        } catch (Exception e) {
            // A corrupt state file should not stop a sync; treat it as never-ran.
            return new SyncState();
        }
    }

    /** Writes the state to disk. */
    public void save(Path jsonFile) throws IOException {
        try (Writer writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
    }
}
