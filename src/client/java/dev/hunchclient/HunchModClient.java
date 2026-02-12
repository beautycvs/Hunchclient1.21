package dev.hunchclient;

import dev.hunchclient.module.ModuleManager;
import dev.hunchclient.module.impl.*;
import dev.hunchclient.module.impl.dungeons.*;
import dev.hunchclient.module.impl.render.CustomMageBeamModule;
import dev.hunchclient.module.impl.misc.*;
import dev.hunchclient.module.impl.sbd.AutoKickModule;
import dev.hunchclient.module.impl.sbd.PartyFinderModule;
import com.mojang.blaze3d.platform.InputConstants;
import dev.hunchclient.command.CommandManager;
import dev.hunchclient.command.bind.BindManager;
import dev.hunchclient.util.SSLBypass;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;

import java.lang.invoke.MethodHandles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

import dev.hunchclient.gui.SkeetScreen2;
import dev.hunchclient.util.Executor;
import dev.hunchclient.module.impl.dungeons.secrets.DungeonManager;
import dev.hunchclient.util.Scheduler;
import dev.hunchclient.bridge.ModuleBridge;
import dev.hunchclient.bridge.EventBridge;
import dev.hunchclient.bridge.module.*;

public class HunchModClient implements ClientModInitializer {

	private static KeyMapping openGuiKey;
	private static KeyMapping terminalDebugKey;
	private static KeyMapping freecamKey;
	private static KeyMapping fakeLagKey;
	private static KeyMapping blinkKey;

	// Custom keybind category for HunchClient
	public static final KeyMapping.Category KEYBIND_CATEGORY = KeyMapping.Category.register(net.minecraft.resources.ResourceLocation.parse("hunchclient:main"));
	private static volatile boolean authenticated = false;

	// Event bus for Terminal Solver and other event-driven modules
	public static final IEventBus EVENT_BUS;

	static {
		EVENT_BUS = new EventBus();
		// Register lambda factory for our package to enable @EventHandler annotations
		EVENT_BUS.registerLambdaFactory("dev.hunchclient", (lookupInMethod, klass) -> {
			try {
				// Use privateLookupIn for Java 9+
				return (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup());
			} catch (Exception e) {
				throw new RuntimeException("Failed to create lambda factory for " + klass.getName(), e);
			}
		});
	}

	@Override
	public void onInitializeClient() {
		HunchClient.LOGGER.info("HunchClient client initializing...");

		// Install SSL bypass FIRST - before ANY HTTPS connections (auth, IRC, etc.)
		// This allows connections to self-signed certificates (like IRC relay server)
		SSLBypass.install();

		// Public release: auth/whitelist disabled
		performAuthentication();

		// Initialize MobGlow system
		dev.hunchclient.render.MobGlow.init();

		// Initialize RenderHelper with Fabric API WorldRenderEvents
		dev.hunchclient.render.RenderHelper.init();

		// Initialize HUD Editor System - Register unified HUD renderer
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, tickCounter) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				// Only render HUD when no screen is open, OR when HudEditorScreen is open
				// Don't render HUD over other GUIs (like SkeetScreen2) to prevent interference
				if (mc.screen == null || mc.screen instanceof dev.hunchclient.hud.HudEditorScreen) {
					dev.hunchclient.hud.HudEditorManager.getInstance().render(context, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
				}

				// Render Mouse Emulator Visualization (after terminal closes)
				dev.hunchclient.render.MouseVisualizerOverlay.render(context);
			}
		});
		HunchClient.LOGGER.info("HUD Editor System initialized");

		// Register title music sound early
		dev.hunchclient.util.TitleMusicRegistry.register();

		// Initialize Command System
		CommandManager.getInstance();
		HunchClient.LOGGER.info("Command system initialized with {} commands",
			CommandManager.getInstance().getCommands().size());

		// Initialize Bind System
		BindManager.getInstance().loadBinds();
		HunchClient.LOGGER.info("Bind system initialized");

		// Initialize modules
		ModuleManager moduleManager = ModuleManager.getInstance();
		moduleManager.registerModule(new FullBrightModule());
		moduleManager.registerModule(new SprintModule());
		moduleManager.registerModule(new LanchoModule());

		// New modules
		moduleManager.registerModule(new StretchModule());
		moduleManager.registerModule(new AnimationsModule());
		ViewmodelOverlayModule galaxyModule = new ViewmodelOverlayModule();
		moduleManager.registerModule(galaxyModule);
		moduleManager.registerModule(new CustomHitSoundModule());
		moduleManager.registerModule(new BlockOutlineModule());
		DarkModeShaderModule darkModeModule = new DarkModeShaderModule();
		moduleManager.registerModule(darkModeModule);
		darkModeModule.setEnabled(true); // Enable by default
		HunchClient.LOGGER.info("DarkModeShaderModule registered and enabled");
		moduleManager.registerModule(new ImageHUDModule());
		moduleManager.registerModule(new DungeonBreakerHelperModule());
		moduleManager.registerModule(new BonzoStaffHelperModule());
		moduleManager.registerModule(new DungeonOptimizerModule());
		moduleManager.registerModule(new RenderOptimizeModule());
		moduleManager.registerModule(new ChatUtilsModule());
		moduleManager.registerModule(new CustomMageBeamModule());

		// Initialize galaxy texture on startup
		System.out.println("[HunchModClient] About to call GalaxyTextureReplacer.init()...");
		dev.hunchclient.render.GalaxyTextureReplacer.init();
		System.out.println("[HunchModClient] GalaxyTextureReplacer.init() completed!");

		// Toggleable modules (OFF = server überschreibt, AN = lokal überschreibt)
		NameProtectModule nameProtect = new NameProtectModule();
		moduleManager.registerModule(nameProtect);
		// NameProtect: OFF = nur remote replacements, AN = remote + local (self name)

		PlayerSizeSpinModule playerSize = new PlayerSizeSpinModule();
		moduleManager.registerModule(playerSize);
		// PlayerSize: OFF = server kann override, AN = lokal überschreibt für self
		moduleManager.registerModule(new EtherwarpHelperModule());
		moduleManager.registerModule(new LeftClickEtherwarpModule());
		moduleManager.registerModule(new AutoSuperboomModule());

		// AutoSS - needs EVENT_BUS for packet events
		AutoSSModule autoSS = new AutoSSModule(EVENT_BUS);
		moduleManager.registerModule(autoSS);

		moduleManager.registerModule(new AlignAuraModule());
		moduleManager.registerModule(new ICant4Module());
		moduleManager.registerModule(new ChestAuraModule());
		moduleManager.registerModule(new CloseDungeonChestsModule());
		moduleManager.registerModule(new AutoMaskSwapModule());
		moduleManager.registerModule(new F7SimModule());
		moduleManager.registerModule(new StarredMobsESPModule());
		moduleManager.registerModule(new SecretHitboxesModule());
		moduleManager.registerModule(new SecretRoutesModule());
		moduleManager.registerModule(new SecretTriggerbotModule());
		moduleManager.registerModule(new SecretAuraModule());
		moduleManager.registerModule(new SSHelperModule());
		// BossBlockMinerModule is deprecated - replaced by DungeonBreakerExtrasModule
		moduleManager.registerModule(new DungeonBreakerExtrasModule());
		moduleManager.registerModule(new FuckDioriteModule());

		// Skeet-styled Dungeon Map with RGB glow border
		moduleManager.registerModule(new SkeetDungeonMapModule());

		// Terminal Solver - F7 Terminal Helper (ported from Odin)
		TerminalSolverModule terminalSolver = new TerminalSolverModule(EVENT_BUS);
		moduleManager.registerModule(terminalSolver);

		// SBD modules (Skyblock Dungeons)
		moduleManager.registerModule(new PartyFinderModule());
		moduleManager.registerModule(new AutoKickModule());

		// Chat modules
		moduleManager.registerModule(new AutoLeapModule());
		moduleManager.registerModule(new KaomojiReplacerModule());
		moduleManager.registerModule(new MeowMessagesModule());

		// Misc modules
		moduleManager.registerModule(new PlayerTrapModule());
		moduleManager.registerModule(new dev.hunchclient.module.impl.misc.NowPlayingModule());
		moduleManager.registerModule(new SkeetThemeModule());
		moduleManager.registerModule(new CustomFontModule());

		// Pokedex module - for capturing OG users
		dev.hunchclient.module.impl.PokedexModule pokedexModule = new dev.hunchclient.module.impl.PokedexModule();
		moduleManager.registerModule(pokedexModule);
		pokedexModule.setEnabled(true); // Explicitly enable to trigger onEnable
		HunchClient.LOGGER.info("Pokedex module registered and enabled");

		// Replay Buffer - FFmpeg-based instant replay
		moduleManager.registerModule(new ReplayBufferModule());

		// Freecam - Free-floating camera mode
		moduleManager.registerModule(new FreecamModule());

		// Blink - Queue packets for teleport effect (LiquidBounce-style)
		moduleManager.registerModule(new BlinkModule());

		// FakeLag - Delay packets like Clumsy
		moduleManager.registerModule(new FakeLagModule());

		// Auto Experiments - needs EVENT_BUS for GUI events
		AutoExperimentsModule autoExperiments = new AutoExperimentsModule(EVENT_BUS);
		moduleManager.registerModule(autoExperiments);

		// IRC module - always enabled
		IrcRelayModule ircRelayModule = new IrcRelayModule();
		moduleManager.registerModule(ircRelayModule);
		ircRelayModule.setEnabled(true);
		ircRelayModule.setToggleable(false);

		HunchClient.LOGGER.info("Registered {} modules", moduleManager.getModules().size());

		// === Bridge Initialization (mixin↔obfuscated boundary) ===
		// Direct implements - modules that implement their bridge interface
		ModuleBridge.register(IF7Sim.class, moduleManager.getModule(F7SimModule.class));
		ModuleBridge.register(ISecretHitboxes.class, moduleManager.getModule(SecretHitboxesModule.class));
		ModuleBridge.register(IViewmodelOverlay.class, moduleManager.getModule(ViewmodelOverlayModule.class));
		ModuleBridge.register(ICustomHitSound.class, moduleManager.getModule(CustomHitSoundModule.class));
		ModuleBridge.register(ITerminalSolver.class, moduleManager.getModule(TerminalSolverModule.class));
		ModuleBridge.register(IDarkModeShader.class, moduleManager.getModule(DarkModeShaderModule.class));
		ModuleBridge.register(IFullBright.class, moduleManager.getModule(FullBrightModule.class));
		ModuleBridge.register(INameProtect.class, moduleManager.getModule(NameProtectModule.class));
		ModuleBridge.register(IAnimations.class, moduleManager.getModule(AnimationsModule.class));
		ModuleBridge.register(IStretch.class, moduleManager.getModule(StretchModule.class));

		// Adapter registrations - modules with static methods that conflict with interface
		ModuleBridge.register(IRenderOptimize.class, new IRenderOptimize() {
			@Override public boolean isEnabled() { return RenderOptimizeModule.getInstance() != null && RenderOptimizeModule.getInstance().isEnabled(); }
			@Override public int getRemoveArmorMode() { return RenderOptimizeModule.getRemoveArmorMode(); }
			@Override public float getSneakCameraAmount() { return RenderOptimizeModule.getSneakCameraAmount(); }
			@Override public boolean shouldHideBlockBreakParticles() { return RenderOptimizeModule.shouldHideBlockBreakParticles(); }
			@Override public boolean shouldDisableHurtCamera() { return RenderOptimizeModule.shouldDisableHurtCamera(); }
			@Override public boolean shouldRemoveLightning() { return RenderOptimizeModule.shouldRemoveLightning(); }
			@Override public boolean shouldHideFallingBlockEntities() { return RenderOptimizeModule.shouldHideFallingBlockEntities(); }
			@Override public boolean shouldDisableVignette() { return RenderOptimizeModule.shouldDisableVignette(); }
			@Override public boolean shouldDisableVanillaArmorHud() { return RenderOptimizeModule.shouldDisableVanillaArmorHud(); }
			@Override public boolean shouldHidePotionOverlay() { return RenderOptimizeModule.shouldHidePotionOverlay(); }
			@Override public boolean shouldRemoveFireOverlay() { return RenderOptimizeModule.shouldRemoveFireOverlay(); }
			@Override public boolean shouldDisableWaterOverlay() { return RenderOptimizeModule.shouldDisableWaterOverlay(); }
			@Override public boolean shouldDisableSuffocatingOverlay() { return RenderOptimizeModule.shouldDisableSuffocatingOverlay(); }
			@Override public boolean shouldHideInventoryEffects() { return RenderOptimizeModule.shouldHideInventoryEffects(); }
			@Override public boolean shouldHideExplosionParticles() { return RenderOptimizeModule.shouldHideExplosionParticles(); }
			@Override public boolean shouldHideFallingDustParticles() { return RenderOptimizeModule.shouldHideFallingDustParticles(); }
			@Override public boolean shouldDisableFog() { return RenderOptimizeModule.shouldDisableFog(); }
		});

		ModuleBridge.register(IRevertAxeSwords.class, new IRevertAxeSwords() {
			@Override public boolean isEnabled() { return RevertAxeSwordsModule.getInstance() != null && RevertAxeSwordsModule.getInstance().isEnabled(); }
			@Override public net.minecraft.world.item.ItemStack getVisualReplacement(net.minecraft.world.item.ItemStack stack) { return RevertAxeSwordsModule.getVisualReplacement(stack); }
		});

		BonzoStaffHelperModule bshRef = moduleManager.getModule(BonzoStaffHelperModule.class);
		ModuleBridge.register(IBonzoStaffHelper.class, new IBonzoStaffHelper() {
			@Override public boolean isEnabled() { return bshRef != null && bshRef.isEnabled(); }
			@Override public boolean shouldPressBackward() { return BonzoStaffHelperModule.shouldPressBackward(); }
			@Override public boolean shouldCancelVelocity() { return BonzoStaffHelperModule.shouldCancelVelocity(); }
		});

		// Event bridge
		EventBridge.init(EVENT_BUS, dev.hunchclient.event.EventBus.getInstance());

		HunchClient.LOGGER.info("Bridge system initialized");

		// Load config AFTER module registration
		try {
			dev.hunchclient.util.ConfigManager.load();
			HunchClient.LOGGER.info("Config loaded successfully");
		} catch (Exception e) {
			HunchClient.LOGGER.warn("Failed to load config: " + e.getMessage());
		}

		// Register Fabric command hooks
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			// Only register explicit /hunchclient and /hc entry points so we don't shadow server commands
			CommandManager cmdManager = CommandManager.getInstance();

			// Keep legacy /hunchclient and /hc commands as shortcuts
			dispatcher.register(ClientCommandManager.literal("hunchclient")
				.then(ClientCommandManager.argument("command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
					.executes(context -> {
						String commandLine = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "command");
						cmdManager.execute(CommandManager.HUNCHCLIENT_PREFIX + commandLine);
						return 1;
					})
				)
			);

			dispatcher.register(ClientCommandManager.literal("hc")
				.then(ClientCommandManager.argument("command", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
					.executes(context -> {
						String commandLine = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "command");
						cmdManager.execute(CommandManager.HC_PREFIX + commandLine);
						return 1;
					})
				)
			);

			// Keep existing special commands below
			dispatcher.register(ClientCommandManager.literal("lancho")
				.executes(context -> {
					LanchoModule lancho = LanchoModule.getInstance();
					if (lancho != null && lancho.getCommandHandler() != null) {
						lancho.getCommandHandler().handleCommand(new String[0]);
					} else {
						context.getSource().sendFeedback(Component.literal("§c[Lancho] Module not initialized!"));
					}
					return 1;
				})
				.then(ClientCommandManager.argument("args", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
					.executes(context -> {
						String argsString = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "args");
						String[] args = argsString.split(" ");

						LanchoModule lancho = LanchoModule.getInstance();
						if (lancho != null && lancho.getCommandHandler() != null) {
							lancho.getCommandHandler().handleCommand(args);
						} else {
							context.getSource().sendFeedback(Component.literal("§c[Lancho] Module not initialized!"));
						}
						return 1;
					})
				)
			);

			// Register IRC commands
			dispatcher.register(ClientCommandManager.literal("irc")
				.executes(context -> {
					IrcRelayModule irc = IrcRelayModule.getInstance();
					if (irc != null && irc.isEnabled()) {
						irc.toggleIrcMode();
					} else {
						context.getSource().sendFeedback(Component.literal("§c[IRC] Module not initialized or disabled!"));
					}
					return 1;
				})
				.then(ClientCommandManager.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
					.executes(context -> {
						String message = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "message");
						IrcRelayModule irc = IrcRelayModule.getInstance();
						if (irc != null && irc.isEnabled()) {
							irc.sendMessage(message);
						} else {
							context.getSource().sendFeedback(Component.literal("§c[IRC] Module not initialized or disabled!"));
						}
						return 1;
					})
				)
			);

			dispatcher.register(ClientCommandManager.literal("hcc")
				.then(ClientCommandManager.argument("message", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
					.executes(context -> {
						String message = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "message");
						IrcRelayModule irc = IrcRelayModule.getInstance();
						if (irc != null && irc.isEnabled()) {
							irc.sendMessage(message);
						} else {
							context.getSource().sendFeedback(Component.literal("§c[IRC] Module not initialized or disabled!"));
						}
						return 1;
					})
				)
			);

			// Register F7Sim cleanup commands (multiple aliases for safety)
			dispatcher.register(ClientCommandManager.literal("f7cleanup")
				.executes(context -> {
					F7SimModule f7sim = moduleManager.getModule(F7SimModule.class);
					if (f7sim != null) {
						context.getSource().sendFeedback(Component.literal("§e[F7Sim] Running cleanup..."));
						f7sim.forceCleanupTerminalStands();
						context.getSource().sendFeedback(Component.literal("§a[F7Sim] Force cleanup executed!"));
					} else {
						context.getSource().sendFeedback(Component.literal("§c[F7Sim] Module not found!"));
					}
					return 1;
				})
			);

			// Alias: cleanterminals
			dispatcher.register(ClientCommandManager.literal("cleanterminals")
				.executes(context -> {
					F7SimModule f7sim = moduleManager.getModule(F7SimModule.class);
					if (f7sim != null) {
						context.getSource().sendFeedback(Component.literal("§e[F7Sim] Running cleanup..."));
						f7sim.forceCleanupTerminalStands();
						context.getSource().sendFeedback(Component.literal("§a[F7Sim] All terminal armor stands removed!"));
					} else {
						context.getSource().sendFeedback(Component.literal("§c[F7Sim] Module not found!"));
					}
					return 1;
				})
			);

			// Debug packets command
			dispatcher.register(ClientCommandManager.literal("debugpackets")
				.executes(context -> {
					dev.hunchclient.util.PacketDebugger.setDebugEnabled(true);
					context.getSource().sendFeedback(Component.literal("§a[Debug] Packet logging ENABLED - check console!"));
					return 1;
				})
				.then(ClientCommandManager.literal("off")
					.executes(context -> {
						dev.hunchclient.util.PacketDebugger.setDebugEnabled(false);
						context.getSource().sendFeedback(Component.literal("§c[Debug] Packet logging DISABLED"));
						return 1;
					})
				)
			);

			// Ivor Fohr Simulator command
			dispatcher.register(ClientCommandManager.literal("ivorfohr")
				.executes(context -> {
					F7SimModule f7sim = moduleManager.getModule(F7SimModule.class);
					if (f7sim != null) {
						if (f7sim.isIvorFohrRunning()) {
							f7sim.stopIvorFohrSimulation();
						} else {
							f7sim.startIvorFohrSimulation();
						}
					} else {
						context.getSource().sendFeedback(Component.literal("§c[Ivor Fohr] F7Sim module not found!"));
					}
					return 1;
				})
			);

			// HUD Editor Command - Open unified HUD editor
			dev.hunchclient.command.HudEditorCommand.register(dispatcher);

			// F7 Phase Debug Command - Shows current F7 phase and P3 section
			dispatcher.register(ClientCommandManager.literal("f7debug")
				.executes(context -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null || mc.level == null) {
						context.getSource().sendFeedback(Component.literal("§c[F7Debug] Not in game!"));
						return 1;
					}

					// Get dungeon info
					boolean inDungeon = dev.hunchclient.util.DungeonUtils.isInDungeon();
					boolean inBoss = dev.hunchclient.util.DungeonUtils.isInBossFight();
					dev.hunchclient.util.DungeonUtils.FloorInfo floorInfo = dev.hunchclient.util.DungeonUtils.getCurrentFloorInfo();
					dev.hunchclient.util.DungeonUtils.F7Phase phase = dev.hunchclient.util.DungeonUtils.getF7Phase();
					dev.hunchclient.util.DungeonUtils.F7P3Section p3Section = dev.hunchclient.util.DungeonUtils.getF7P3Section();

					// Player position
					double x = mc.player.getX();
					double y = mc.player.getY();
					double z = mc.player.getZ();

					// Send debug info
					context.getSource().sendFeedback(Component.literal("§6§l[F7 DEBUG INFO]"));
					context.getSource().sendFeedback(Component.literal("§7─────────────────────────────"));
					context.getSource().sendFeedback(Component.literal("§eIn Dungeon: §f" + (inDungeon ? "§aYES" : "§cNO")));
					context.getSource().sendFeedback(Component.literal("§eIn Boss: §f" + (inBoss ? "§aYES" : "§cNO")));
					context.getSource().sendFeedback(Component.literal("§eFloor: §f" + (floorInfo.isValid() ?
						(floorInfo.master() ? "M" : "F") + floorInfo.number() : "§cNONE")));
					context.getSource().sendFeedback(Component.literal("§eF7 Phase: §f" + phase.name() +
						(phase != dev.hunchclient.util.DungeonUtils.F7Phase.UNKNOWN ? " §a✓" : " §c✗")));
					context.getSource().sendFeedback(Component.literal("§eP3 Section: §f" + p3Section.name() +
						(p3Section != dev.hunchclient.util.DungeonUtils.F7P3Section.UNKNOWN ? " §a✓" : " §c✗")));
					context.getSource().sendFeedback(Component.literal("§7─────────────────────────────"));
					context.getSource().sendFeedback(Component.literal("§ePosition: §7X=§f" + String.format("%.1f", x) +
						" §7Y=§f" + String.format("%.1f", y) + " §7Z=§f" + String.format("%.1f", z)));
					context.getSource().sendFeedback(Component.literal("§7─────────────────────────────"));

					return 1;
				})
			);

			// P3 Debug Command (alias for f7debug)
			dispatcher.register(ClientCommandManager.literal("p3debug")
				.executes(context -> {
					// Just call f7debug
					return dispatcher.execute("f7debug", context.getSource());
				})
			);
		});

		// Register keybinding to open GUI (Right Shift)
		openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hunchclient.open_gui",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			KEYBIND_CATEGORY
		));

		// Register keybinding for Terminal Debug Dump (unbound by default)
		terminalDebugKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hunchclient.terminal_debug",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, // Unbound by default - user can set in controls
			KEYBIND_CATEGORY
		));

		// Register keybinding for Freecam toggle (unbound by default)
		freecamKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hunchclient.freecam",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, // Unbound by default - user can set in controls
			KEYBIND_CATEGORY
		));

		// Register keybinding for FakeLag toggle (unbound by default)
		fakeLagKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hunchclient.fakelag",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, // Unbound by default - user can set in controls
			KEYBIND_CATEGORY
		));

		// Register keybinding for Blink toggle (unbound by default)
		blinkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.hunchclient.blink",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_UNKNOWN, // Unbound by default - user can set in controls
			KEYBIND_CATEGORY
		));

		// Initialize Executor system
		Executor.init();

		// Initialize DungeonManager (Skyblocker port)
		HunchClient.LOGGER.info("Initializing DungeonManager...");
		DungeonManager.init();

		// Register tick event
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Tick scheduler for DungeonManager
			Scheduler.INSTANCE.tick();

			// Inject fake dungeon map to prevent issues when holding bow (Skytils-inspired)
			DungeonManager.injectFakeMapIfNeeded(client);

			// Update NowPlaying module (media polling)
			dev.hunchclient.module.impl.misc.NowPlayingModule nowPlaying = ModuleManager.getInstance().getModule(dev.hunchclient.module.impl.misc.NowPlayingModule.class);
			if (nowPlaying != null && nowPlaying.isEnabled()) {
				nowPlaying.update();
			}

			// GUI toggle - NEW SKEET GUI!
			if (openGuiKey.consumeClick() && client.screen == null) {
				client.setScreen(new SkeetScreen2());
			}

			// Terminal Debug Dump - dumps last 10 terminal states to chat
			if (terminalDebugKey.consumeClick()) {
				dev.hunchclient.module.impl.terminalsolver.TerminalDebugger.getInstance().dumpToChat();
			}

			// Freecam toggle
			if (freecamKey.consumeClick()) {
				FreecamModule freecam = moduleManager.getModule(FreecamModule.class);
				if (freecam != null) {
					freecam.toggle();
				}
			}

			// FakeLag toggle (Clumsy-style lag)
			if (fakeLagKey.consumeClick()) {
				FakeLagModule fakeLag = moduleManager.getModule(FakeLagModule.class);
				if (fakeLag != null) {
					fakeLag.toggle();
				}
			}

			// Blink toggle (packet queue teleport)
			if (blinkKey.consumeClick()) {
				BlinkModule blink = moduleManager.getModule(BlinkModule.class);
				if (blink != null) {
					blink.toggle();
				}
			}

			// World unload detection for AlignAura, StarredMobsESP, and AutoSuperboom
			if (client.level == null) {
				AlignAuraModule alignAura = moduleManager.getModule(AlignAuraModule.class);
				if (alignAura != null) {
					alignAura.onWorldUnload();
				}

				StarredMobsESPModule starredESP = moduleManager.getModule(StarredMobsESPModule.class);
				if (starredESP != null) {
					starredESP.onWorldUnload();
				}
			}

			// Tick menu music manager
			dev.hunchclient.util.MenuMusicManager.getInstance().tick();

			// Tick limbo music manager (loops Eleanor Rigby)
			dev.hunchclient.util.LimboMusicManager.getInstance().tick();

			// Tick all modules
			moduleManager.tick();

			// Tick all executors
			Executor.tickAll();
		});

		// Register cleanup on disconnect/world unload to clean up F7Sim and TerminalSolver state
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HunchClient.LOGGER.info("Client disconnected - cleaning up F7Sim and TerminalSolver...");

			// Cleanup F7SimModule terminals and armor stands
			F7SimModule f7sim = moduleManager.getModule(F7SimModule.class);
			if (f7sim != null) {
				f7sim.onWorldUnload();
			}

			// Cleanup TerminalSolverModule state and mouse emulator
			TerminalSolverModule termSolver = moduleManager.getModule(TerminalSolverModule.class);
			if (termSolver != null) {
				termSolver.onWorldUnload();
			}

			HunchClient.LOGGER.info("Disconnect cleanup completed");
		});

		// Register cleanup on client shutdown to prevent memory leaks
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			HunchClient.LOGGER.info("HunchClient shutting down - cleaning up resources...");

			// Cleanup NVGRenderer native resources (critical for preventing native memory leaks)
			try {
				dev.hunchclient.render.NVGRenderer.cleanup();
				HunchClient.LOGGER.info("NVGRenderer cleaned up");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error cleaning up NVGRenderer: " + e.getMessage());
			}

			// Cleanup HudRenderer resources
			try {
				dev.hunchclient.render.HudRenderer.getInstance().clearCache();
				HunchClient.LOGGER.info("HudRenderer cache cleared");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error cleaning up HudRenderer: " + e.getMessage());
			}

			// Cleanup GlowRenderer
			try {
				dev.hunchclient.render.GlowRenderer.getInstance().cleanup();
				HunchClient.LOGGER.info("GlowRenderer cleaned up");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error cleaning up GlowRenderer: " + e.getMessage());
			}

			// Shutdown HttpClient thread pool (prevents thread leaks)
			try {
				dev.hunchclient.module.impl.lancho.HttpClient.shutdown();
				HunchClient.LOGGER.info("HttpClient thread pool shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down HttpClient: " + e.getMessage());
			}

			// Shutdown IRC relay executor
			try {
				dev.hunchclient.module.impl.IrcRelayModule.shutdownExecutor();
				HunchClient.LOGGER.info("IrcRelay executor shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down IrcRelay executor: " + e.getMessage());
			}

			// Shutdown PlayerHeadCache executor
			try {
				dev.hunchclient.module.impl.dungeons.map.PlayerHeadCache.shutdown();
				HunchClient.LOGGER.info("PlayerHeadCache executor shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down PlayerHeadCache executor: " + e.getMessage());
			}

			// Shutdown Pokedex screenshot executor and clear textures
			try {
				dev.hunchclient.gui.PokedexScreen.clearCaches();
				dev.hunchclient.gui.PokedexScreen.shutdownExecutor();
				HunchClient.LOGGER.info("PokedexScreen resources cleaned up");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error cleaning up PokedexScreen: " + e.getMessage());
			}

			// Shutdown Hypixel API executor
			try {
				dev.hunchclient.module.impl.sbd.api.HypixelApiClient.shutdownExecutor();
				HunchClient.LOGGER.info("HypixelApiClient executor shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down HypixelApiClient executor: " + e.getMessage());
			}

			// Shutdown LocationManager scheduler
			try {
				dev.hunchclient.util.LocationManager.getInstance().shutdown();
				HunchClient.LOGGER.info("LocationManager scheduler shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down LocationManager: " + e.getMessage());
			}

			// Cleanup Now Playing album art
			try {
				dev.hunchclient.module.impl.misc.AlbumArtLoader.getInstance().shutdown();
				HunchClient.LOGGER.info("AlbumArtLoader cleaned up");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error cleaning up AlbumArtLoader: " + e.getMessage());
			}

			// Shutdown FFmpeg replay buffer
			try {
				dev.hunchclient.util.FFmpegManager.getInstance().shutdown();
				HunchClient.LOGGER.info("FFmpeg replay buffer shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down FFmpeg: " + e.getMessage());
			}

			// Shutdown PacketQueueManager (FakeLag scheduler)
			try {
				dev.hunchclient.network.PacketQueueManager.getInstance().shutdown();
				HunchClient.LOGGER.info("PacketQueueManager shut down");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error shutting down PacketQueueManager: " + e.getMessage());
			}

			// Save config before shutdown
			try {
				dev.hunchclient.util.ConfigManager.save();
				HunchClient.LOGGER.info("Config saved");
			} catch (Exception e) {
				HunchClient.LOGGER.error("Error saving config: " + e.getMessage());
			}

			HunchClient.LOGGER.info("HunchClient shutdown complete");
		});

		HunchClient.LOGGER.info("HunchClient client initialized!");
	}

	/**
	 * Public release: auth/whitelist disabled.
	 */
	private void performAuthentication() {
		authenticated = true;
		HunchClient.LOGGER.info("Authentication disabled (public release)");
	}

	/**
	 * Checks if client is authenticated
	 */
	public static boolean isAuthenticated() {
		return authenticated;
	}
}
