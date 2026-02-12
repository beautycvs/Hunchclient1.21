package dev.hunchclient.gui;

import dev.hunchclient.module.impl.dungeons.BossBlockMinerModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlockMarkEditScreen extends Screen {
    private final BossBlockMinerModule module;
    private final Minecraft mc = Minecraft.getInstance();

    public BlockMarkEditScreen(BossBlockMinerModule module) {
        super(Component.literal("Block Marker Edit Mode"));
        this.module = module;
    }

    @Override
    protected void init() {
        super.init();
        module.setEditMode(true);
    }

    @Override
    public void onClose() {
        module.setEditMode(false);
        module.saveConfig();
        super.onClose();

        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a[BossBlockMiner] Edit mode closed - Config saved"), false);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Semi-transparent dark overlay
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Title and instructions
        String[] lines = {
            ChatFormatting.GREEN + "BLOCK MARKER EDIT MODE",
            "",
            ChatFormatting.YELLOW + "Right-click" + ChatFormatting.GRAY + " blocks to mark/unmark them",
            ChatFormatting.GRAY + "Press " + ChatFormatting.WHITE + "ESC" + ChatFormatting.GRAY + " to save and exit",
            "",
            ChatFormatting.AQUA + "Marked Blocks: " + ChatFormatting.WHITE + module.getMarkedBlocks().size(),
            "",
            ChatFormatting.DARK_GRAY + "Tip: Use this in F7 sim or anywhere",
            ChatFormatting.DARK_GRAY + "Coordinates are saved permanently"
        };

        int totalHeight = lines.length * 12;
        int startY = (this.height - totalHeight) / 2;

        for (int i = 0; i < lines.length; i++) {
            context.drawCenteredString(
                this.font,
                Component.literal(lines[i]),
                this.width / 2,
                startY + (i * 12),
                0xFFFFFFFF
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        // Don't pause the game
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
