package dev.hunchclient.bridge;

import dev.hunchclient.bridge.module.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleBridge {

    private static final ConcurrentHashMap<Class<?>, Object> registry = new ConcurrentHashMap<>();

    private ModuleBridge() {}

    public static <T> void register(Class<T> iface, T impl) {
        registry.put(iface, impl);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> iface) {
        return (T) registry.get(iface);
    }

    public static IF7Sim f7sim() { return get(IF7Sim.class); }
    public static IRenderOptimize renderOpt() { return get(IRenderOptimize.class); }
    public static ISecretHitboxes secretHitboxes() { return get(ISecretHitboxes.class); }
    public static IViewmodelOverlay viewmodelOverlay() { return get(IViewmodelOverlay.class); }
    public static ICustomHitSound customHitSound() { return get(ICustomHitSound.class); }
    public static ITerminalSolver terminalSolver() { return get(ITerminalSolver.class); }
    public static IDarkModeShader darkModeShader() { return get(IDarkModeShader.class); }
    public static IRevertAxeSwords revertAxeSwords() { return get(IRevertAxeSwords.class); }
    public static IBonzoStaffHelper bonzoStaffHelper() { return get(IBonzoStaffHelper.class); }
    public static IFullBright fullBright() { return get(IFullBright.class); }
    public static INameProtect nameProtect() { return get(INameProtect.class); }
    public static IAnimations animations() { return get(IAnimations.class); }
    public static IStretch stretch() { return get(IStretch.class); }
    public static IAutoLeap autoLeap() { return get(IAutoLeap.class); }
    public static IAutoKick autoKick() { return get(IAutoKick.class); }
    public static ILancho lancho() { return get(ILancho.class); }
    public static IFuckDiorite fuckDiorite() { return get(IFuckDiorite.class); }
    public static ISSHelper ssHelper() { return get(ISSHelper.class); }
    public static IAlignAura alignAura() { return get(IAlignAura.class); }
    public static IBossBlockMiner bossBlockMiner() { return get(IBossBlockMiner.class); }
    public static ICloseDungeonChests closeDungeonChests() { return get(ICloseDungeonChests.class); }
    public static ISecretAura secretAura() { return get(ISecretAura.class); }
    public static IAutoMaskSwap autoMaskSwap() { return get(IAutoMaskSwap.class); }
    public static IAutoScreenshot autoScreenshot() { return get(IAutoScreenshot.class); }
    public static IDungeonOptimizer dungeonOptimizer() { return get(IDungeonOptimizer.class); }
    public static IChatUtils chatUtils() { return get(IChatUtils.class); }
    public static ICustomFont customFont() { return get(ICustomFont.class); }
    public static IPartyFinder partyFinder() { return get(IPartyFinder.class); }
    public static IPlayerSizeSpin playerSizeSpin() { return get(IPlayerSizeSpin.class); }
    public static IStarredMobsESP starredMobsESP() { return get(IStarredMobsESP.class); }
    public static IAutoSuperboom autoSuperboom() { return get(IAutoSuperboom.class); }
    public static ILeftClickEtherwarp leftClickEtherwarp() { return get(ILeftClickEtherwarp.class); }
    public static IIrcRelay ircRelay() { return get(IIrcRelay.class); }
    public static IMeowMessages meowMessages() { return get(IMeowMessages.class); }
    public static IKaomojiReplacer kaomojiReplacer() { return get(IKaomojiReplacer.class); }
    public static IFreecam freecam() { return get(IFreecam.class); }
}
