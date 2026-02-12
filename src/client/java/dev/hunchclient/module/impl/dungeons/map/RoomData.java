package dev.hunchclient.module.impl.dungeons.map;

import java.util.List;

/**
 * Room metadata loaded from Skytils room database
 * 1:1 Port from noamm's RoomData.kt
 */
public class RoomData {
    private final String name;
    private final RoomType type;
    private final List<Integer> cores;  // Core hashes for room identification
    private final int crypts;
    private final int secrets;
    private final int trappedChests;

    public RoomData(String name, RoomType type, List<Integer> cores, int crypts, int secrets, int trappedChests) {
        this.name = name;
        this.type = type;
        this.cores = cores;
        this.crypts = crypts;
        this.secrets = secrets;
        this.trappedChests = trappedChests;
    }

    /**
     * Create an unknown room data with just a type
     */
    public static RoomData createUnknown(RoomType type) {
        return new RoomData("Unknown", type, List.of(), 0, 0, 0);
    }

    public String getName() {
        return name;
    }

    public RoomType getType() {
        return type;
    }

    public List<Integer> getCores() {
        return cores;
    }

    public int getCrypts() {
        return crypts;
    }

    public int getSecrets() {
        return secrets;
    }

    public int getTrappedChests() {
        return trappedChests;
    }

    @Override
    public String toString() {
        return String.format("RoomData{name='%s', type=%s, cores=%d, secrets=%d, crypts=%d}",
                name, type, cores.size(), secrets, crypts);
    }
}
