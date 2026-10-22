package com.arcaneforge.config;

import com.arcaneforge.ArcaneForge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Loads, saves and hot-reloads {@code config/arcaneforge.json}. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static volatile ArcaneForgeConfig config = new ArcaneForgeConfig();
    private static volatile int revision = 1;

    private ConfigManager() {
    }

    public static ArcaneForgeConfig get() {
        return config;
    }

    /** Changes whenever weapon-relevant values change; used to refresh stats on existing weapons. */
    public static int revision() {
        return revision;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(ArcaneForge.MOD_ID + ".json");
    }

    public static void load() {
        try {
            reload();
        } catch (Exception e) {
            ArcaneForge.LOGGER.error("Failed to load Arcane Forge config, using defaults", e);
            config = new ArcaneForgeConfig();
            config.sanitize();
            revision = computeRevision(config);
        }
    }

    /**
     * Reads the config from disk (creating it with defaults when missing), merges in any new default keys,
     * writes the merged file back and swaps it in atomically.
     */
    public static synchronized void reload() throws IOException {
        Path file = path();
        ArcaneForgeConfig loaded;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, ArcaneForgeConfig.class);
            } catch (RuntimeException e) {
                Path backup = file.resolveSibling(ArcaneForge.MOD_ID + ".json.broken");
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                throw new IOException("Invalid JSON in " + file.getFileName() + " (a backup was saved as " + backup.getFileName() + "): " + e.getMessage(), e);
            }
            if (loaded == null) {
                loaded = new ArcaneForgeConfig();
            }
        } else {
            loaded = new ArcaneForgeConfig();
        }
        loaded.sanitize();
        save(loaded);
        config = loaded;
        revision = computeRevision(loaded);
        ArcaneForge.LOGGER.info("Loaded Arcane Forge config (revision {})", revision);
    }

    public static synchronized void save(ArcaneForgeConfig cfg) throws IOException {
        Path file = path();
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(cfg, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int computeRevision(ArcaneForgeConfig cfg) {
        int hash = Objects.hash(GSON.toJson(cfg.weapons), GSON.toJson(cfg.metals), cfg.forging.oilTemperBonus);
        return hash == 0 ? 1 : hash;
    }
}
