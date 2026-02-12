package dev.hunchclient.event;

import dev.hunchclient.module.impl.terminal.ClientArmorStandManager;
import dev.hunchclient.module.impl.terminal.TerminalManager;
import dev.hunchclient.module.impl.terminal.TerminalPosition;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Handles right-click interactions for opening terminals.
 */
public class TerminalInteractionHandler {
    private static TerminalManager terminalManager;
    private static ClientArmorStandManager armorStandManager;
    private static boolean registered = false;

    public static void register(TerminalManager manager, ClientArmorStandManager standManager) {
        terminalManager = manager;
        armorStandManager = standManager;

        if (!registered) {
            ClientTickEvents.END_CLIENT_TICK.register(TerminalInteractionHandler::onClientTick);
            UseEntityCallback.EVENT.register(TerminalInteractionHandler::onUseEntity);
            registered = true;
        }
    }

    private static void onClientTick(Minecraft client) {
        if (terminalManager == null || client.player == null || client.level == null) {
            return;
        }

        KeyMapping useKey = client.options.keyUse;
        while (useKey.consumeClick()) {
            TerminalPosition terminal = getTargetedTerminal(client);
            if (terminal == null) {
                terminal = terminalManager.getLookedAtTerminal();
            }

            if (terminal != null) {
                terminalManager.openTerminal(terminal);
            }
        }
    }

    private static TerminalPosition getTargetedTerminal(Minecraft client) {
        if (armorStandManager == null) {
            return null;
        }

        HitResult hit = client.hitResult;
        if (hit instanceof EntityHitResult entityHit) {
            return armorStandManager.getTerminalForEntity(entityHit.getEntity());
        }

        return null;
    }

    public static void unregister() {
        terminalManager = null;
        armorStandManager = null;
    }

    private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, net.minecraft.world.entity.Entity entity, EntityHitResult hitResult) {
        if (!world.isClientSide() || terminalManager == null || armorStandManager == null) {
            return InteractionResult.PASS;
        }

        TerminalPosition terminal = armorStandManager.getTerminalForEntity(entity);
        if (terminal == null) {
            return InteractionResult.PASS;
        }

        terminalManager.openTerminal(terminal);
        return InteractionResult.SUCCESS;
    }
}
