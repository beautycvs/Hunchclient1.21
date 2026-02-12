package dev.hunchclient.module.impl.dungeons;

import dev.hunchclient.module.Module;
import dev.hunchclient.util.DungeonUtils;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.MenuType;

/**
 * Automatically closes regular dungeon secret chests when they are opened.
 * Prevents teammates from accidentally looting secrets while the module is enabled.
 */
public class CloseDungeonChestsModule extends Module {

    private static final Set<MenuType<?>> CHEST_TYPES = Set.of(
        MenuType.GENERIC_9x1,
        MenuType.GENERIC_9x2,
        MenuType.GENERIC_9x3,
        MenuType.GENERIC_9x4,
        MenuType.GENERIC_9x5,
        MenuType.GENERIC_9x6,
        MenuType.GENERIC_3x3
    );

    // Only these exact titles should be blocked (dungeon secret chests)
    private static final Set<String> SECRET_CHEST_TITLES = Set.of(
        "chest",
        "large chest"
    );

    public CloseDungeonChestsModule() {
        super("CloseDungeonChests", "Auto-closes dungeon secret chests before they open.", Category.DUNGEONS, true);
    }

    /**
     * Called from {@link dev.hunchclient.mixin.client.ClientPlayNetworkHandlerMixin} when a screen is about to open.
     *
     * @return true if the opening should be cancelled entirely.
     */
    public boolean handleOpenScreen(ClientboundOpenScreenPacket packet) {
        if (!isEnabled()) {
            return false;
        }

        // STRICT dungeon check - must be in ACTIVE dungeon, not just Dungeon Hub
        // This prevents accidentally closing chests on Private Island or elsewhere
        if (!DungeonUtils.isInActiveDungeon()) {
            return false;
        }

        if (!isChestPacket(packet)) {
            return false;
        }

        if (!isSecretChest(packet.getTitle())) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }

        client.getConnection().send(new ServerboundContainerClosePacket(packet.getContainerId()));
        return true;
    }

    private boolean isChestPacket(ClientboundOpenScreenPacket packet) {
        MenuType<?> type = packet.getType();
        return type != null && CHEST_TYPES.contains(type);
    }

    private boolean isSecretChest(Component title) {
        if (title == null) {
            return false;
        }
        String normalized = DungeonUtils.stripFormatting(title.getString()).trim().toLowerCase(Locale.ROOT);
        return SECRET_CHEST_TITLES.contains(normalized);
    }

    @Override
    protected void onEnable() {
        // No-op
    }

    @Override
    protected void onDisable() {
        // No-op
    }
}
