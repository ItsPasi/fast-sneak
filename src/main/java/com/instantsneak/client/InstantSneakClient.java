package com.instantsneak.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstantSneakClient implements ClientModInitializer {
    public static final String MOD_ID = "instant-sneak";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        InstantSneakConfig.load();
        LOGGER.info("Instant Sneak loaded");
    }
}
