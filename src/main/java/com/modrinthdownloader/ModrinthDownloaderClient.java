package com.modrinthdownloader;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModrinthDownloaderClient implements ClientModInitializer {
    public static final String MOD_ID = "modrinth-downloader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ModrinthDownloader] Initialized for Minecraft 26.1!");
    }
}
