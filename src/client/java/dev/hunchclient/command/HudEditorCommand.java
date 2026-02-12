package dev.hunchclient.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.hunchclient.hud.HudEditorScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Command to open the HUD Editor
 * Usage: /hudedit or /hudeditor
 */
public class HudEditorCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // /hudedit
        dispatcher.register(
            ClientCommandManager.literal("hudedit")
                .executes(HudEditorCommand::openHudEditor)
        );

        // /hudeditor (alias)
        dispatcher.register(
            ClientCommandManager.literal("hudeditor")
                .executes(HudEditorCommand::openHudEditor)
        );
    }

    private static int openHudEditor(CommandContext<FabricClientCommandSource> context) {
        Minecraft mc = Minecraft.getInstance();

        // Schedule screen opening on main thread
        mc.execute(() -> {
            mc.setScreen(new HudEditorScreen(mc.screen));
        });

        context.getSource().sendFeedback(Component.literal("§aOpening HUD Editor..."));
        return 1;
    }
}
