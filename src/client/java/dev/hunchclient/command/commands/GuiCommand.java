package dev.hunchclient.command.commands;

import dev.hunchclient.command.Command;
import dev.hunchclient.gui.SkeetScreen2;

/**
 * Open the HunchClient GUI
 */
public class GuiCommand extends Command {

    @Override
    public String getName() {
        return "gui";
    }

    @Override
    public String getDescription() {
        return "Open the HunchClient GUI";
    }

    @Override
    public String getUsage() {
        return "gui";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"menu", "clickgui"};
    }

    @Override
    public Category getCategory() {
        return Category.GENERAL;
    }

    @Override
    public void execute(String[] args) {
        if (mc.player != null) {
            mc.execute(() -> mc.setScreen(new SkeetScreen2()));
            sendSuccess("Opening GUI...");
        } else {
            sendError("You must be in-game to open the GUI");
        }
    }
}
