package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.HunchClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Config for BOSS room blocks (no RoomID needed, coords are always the same)
 */
public class BossBlockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/hunchclient/boss_blocks.json");

    private List<BlockPos> blocks = new ArrayList<>();

    public static BossBlockConfig load() {
        if (!CONFIG_FILE.exists()) {
            HunchClient.LOGGER.info("[BossBlockConfig] No boss config found, creating new");
            return new BossBlockConfig();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            BossBlockConfig config = new BossBlockConfig();

            if (json.has("blocks")) {
                JsonArray blocksArray = json.getAsJsonArray("blocks");
                for (int i = 0; i < blocksArray.size(); i++) {
                    JsonObject blockObj = blocksArray.get(i).getAsJsonObject();
                    int x = blockObj.get("x").getAsInt();
                    int y = blockObj.get("y").getAsInt();
                    int z = blockObj.get("z").getAsInt();
                    config.blocks.add(new BlockPos(x, y, z));
                }
            }

            HunchClient.LOGGER.info("[BossBlockConfig] Loaded {} boss blocks", config.blocks.size());
            return config;

        } catch (Exception e) {
            HunchClient.LOGGER.error("[BossBlockConfig] Failed to load boss config", e);
            return new BossBlockConfig();
        }
    }

    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();

            JsonObject json = new JsonObject();
            JsonArray blocksArray = new JsonArray();

            for (BlockPos pos : blocks) {
                JsonObject blockObj = new JsonObject();
                blockObj.addProperty("x", pos.getX());
                blockObj.addProperty("y", pos.getY());
                blockObj.addProperty("z", pos.getZ());
                blocksArray.add(blockObj);
            }

            json.add("blocks", blocksArray);

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(json, writer);
            }

            HunchClient.LOGGER.info("[BossBlockConfig] Saved {} boss blocks", blocks.size());

        } catch (Exception e) {
            HunchClient.LOGGER.error("[BossBlockConfig] Failed to save boss config", e);
        }
    }

    public void addBlock(BlockPos pos) {
        if (!blocks.contains(pos)) {
            blocks.add(pos);
            HunchClient.LOGGER.info("[BossBlockConfig] Added boss block: {}", pos);
        }
    }

    public void removeBlock(BlockPos pos) {
        if (blocks.remove(pos)) {
            HunchClient.LOGGER.info("[BossBlockConfig] Removed boss block: {}", pos);
        }
    }

    public void clear() {
        blocks.clear();
        HunchClient.LOGGER.info("[BossBlockConfig] Cleared all boss blocks");
    }

    public List<BlockPos> getBlocks() {
        return new ArrayList<>(blocks);
    }

    public int getBlockCount() {
        return blocks.size();
    }
}
