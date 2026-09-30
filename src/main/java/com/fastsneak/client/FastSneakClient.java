package com.fastsneak.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FastSneakClient implements ClientModInitializer {
    public static final String MOD_ID = "fast-sneak";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        FastSneakConfig.load();
        LOGGER.info("Fast Sneak loaded");
    }
}
