package com.fastsneak.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.api.NameableEnum;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FastSneakConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("fast-sneak.json");
    private static final SneakHeight DEFAULT_SNEAK_HEIGHT = SneakHeight.PRE_1_9;

    private static FastSneakConfig instance = new FastSneakConfig();

    public SneakHeight sneakHeight = DEFAULT_SNEAK_HEIGHT;
    public boolean animateShallowSneak = false;
    public boolean animateDeepSneak = true;
    public boolean affectThirdPerson = false;

    public static FastSneakConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                FastSneakConfig loaded = GSON.fromJson(reader, FastSneakConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (Exception exception) {
                FastSneakClient.LOGGER.warn("Could not load config, using defaults", exception);
                instance = new FastSneakConfig();
            }
        }

        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException exception) {
            FastSneakClient.LOGGER.warn("Could not save config", exception);
        }
    }

    private void sanitize() {
        if (sneakHeight == null) {
            sneakHeight = DEFAULT_SNEAK_HEIGHT;
        }
    }

    public enum SneakHeight implements NameableEnum {
        PRE_1_9(1.54F, "Pre-1.9"),
        PRE_1_14(1.42F, "Pre-1.14"),
        NORMAL(SneakCameraController.MODERN_SNEAK_EYE_HEIGHT, "Normal");

        private final float eyeHeight;
        private final String displayName;

        SneakHeight(float eyeHeight, String displayName) {
            this.eyeHeight = eyeHeight;
            this.displayName = displayName;
        }

        public float eyeHeight() {
            return eyeHeight;
        }

        @Override
        public Component getDisplayName() {
            return Component.literal(displayName);
        }
    }
}
