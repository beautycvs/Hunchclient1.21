package dev.hunchclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HunchClient implements ModInitializer {
    public static final String MOD_ID = "hunchclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("hunchclient");
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onInitialize() {
        LOGGER.info("HunchClient is initializing...");
    }
}
