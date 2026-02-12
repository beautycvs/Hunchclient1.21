package dev.hunchclient.module.impl.render;

import com.google.gson.JsonObject;
import dev.hunchclient.HunchModClient;
import dev.hunchclient.event.PacketEvent;
import dev.hunchclient.module.ConfigurableModule;
import dev.hunchclient.module.Module;
import dev.hunchclient.module.SettingsProvider;
import dev.hunchclient.module.setting.CheckboxSetting;
import dev.hunchclient.module.setting.ColorPickerSetting;
import dev.hunchclient.module.setting.DropdownSetting;
import dev.hunchclient.module.setting.ModuleSetting;
import dev.hunchclient.module.setting.SliderSetting;
import dev.hunchclient.render.WorldLineRenderer;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;
import java.awt.Color;
import java.util.*;

/**
 * Custom Mage Beam Module
 * Replaces firework rocket particles with custom OpenGL lines
 * Supports various visual effects: dashed, spinning, gradient, etc.
 */
public class CustomMageBeamModule extends Module implements ConfigurableModule, SettingsProvider {

    // Active beams - stores actual particle positions in world space
    private final List<MageBeam> activeBeams = Collections.synchronizedList(new ArrayList<>());

    // Current tick counter for timing
    private int currentTick = 0;

    // === GENERAL SETTINGS ===
    private float lineThickness = 2.5f;
    private int durationTicks = 40;
    private int minPoints = 8;
    private boolean throughWalls = true;
    private float fadeOut = 0.8f;
    private float yOffset = 0.0f; // Adjust beam height
    private boolean hideParticles = true; // Hide vanilla firework particles

    // === COLOR SETTINGS ===
    private int beamColor = 0xFFFF64FF; // Default: pink with full alpha
    private int beamColorEnd = 0xFF64FFFF; // End color for gradient
    private boolean rainbow = false;
    private float rainbowSpeed = 1.0f;

    // === ANIMATION SETTINGS ===
    private boolean spinning = false;
    private float spinSpeed = 2.0f;

    // === DASHED SETTINGS ===
    private float dashLength = 0.5f;
    private float dashGap = 0.3f;

    // === DOTTED SETTINGS ===
    private float dotSpacing = 0.3f;
    private float dotSize = 1.5f;

    // === HELIX SETTINGS ===
    private float helixRadius = 0.15f;
    private float helixPitch = 0.5f;

    // === WAVE SETTINGS ===
    private float waveAmplitude = 0.2f;
    private float waveFrequency = 3.0f;

    // Beam style
    private BeamStyle beamStyle = BeamStyle.SOLID;

    public enum BeamStyle {
        SOLID("Solid"),
        DASHED("Dashed"),
        DOTTED("Dotted"),
        HELIX("Helix"),
        DOUBLE_HELIX("Double Helix"),
        WAVE("Wave"),
        GRADIENT("Gradient");

        private final String displayName;

        BeamStyle(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public CustomMageBeamModule() {
        super("CustomMageBeam", "Replaces mage beam particles with custom lines", Category.VISUALS, RiskLevel.SAFE);
    }

    @Override
    protected void onEnable() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        WorldRenderEvents.END_MAIN.register(this::onWorldRender);
        HunchModClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        if (!(event.packet instanceof ClientboundLevelParticlesPacket packet)) return;
        if (packet.getParticle().getType() != ParticleTypes.FIREWORK) return;

        // Track the particle position
        Vec3 newPoint = new Vec3(packet.getX(), packet.getY(), packet.getZ());

        MageBeam recentBeam = activeBeams.isEmpty() ? null : activeBeams.get(activeBeams.size() - 1);

        if (recentBeam != null &&
            (currentTick - recentBeam.lastUpdateTick) < 2 &&
            isPointInBeamDirection(recentBeam.points, newPoint)) {
            recentBeam.points.add(newPoint);
            recentBeam.lastUpdateTick = currentTick;
        } else {
            MageBeam newBeam = new MageBeam(currentTick);
            newBeam.points.add(newPoint);
            activeBeams.add(newBeam);
        }

        // Cancel the packet to hide vanilla particles
        if (hideParticles) {
            event.setCancelled(true);
        }
    }

    @Override
    protected void onDisable() {
        activeBeams.clear();
        currentTick = 0;
        HunchModClient.EVENT_BUS.unsubscribe(this);
    }

    private boolean isPointInBeamDirection(List<Vec3> points, Vec3 newPoint) {
        if (points.isEmpty() || points.size() <= 1) return true;

        Vec3 first = points.get(0);
        Vec3 last = points.get(points.size() - 1);
        Vec3 direction = last.subtract(first).normalize();
        Vec3 toNew = newPoint.subtract(last).normalize();

        return direction.dot(toNew) > 0.95;
    }

    private void onClientTick(Minecraft client) {
        if (!isEnabled()) return;

        currentTick++;
        activeBeams.removeIf(beam -> (currentTick - beam.creationTick) > durationTicks);
    }

    private void onWorldRender(WorldRenderContext context) {
        if (!isEnabled() || activeBeams.isEmpty()) return;

        WorldLineRenderer.setContext(
            context.matrices(),
            context.consumers(),
            context.worldState().cameraRenderState.pos
        );

        long now = System.currentTimeMillis();
        List<MageBeam> beamsCopy = new ArrayList<>(activeBeams);

        for (MageBeam beam : beamsCopy) {
            if (beam.points.size() < minPoints) continue;

            float age = (currentTick - beam.creationTick) / (float) durationTicks;
            float alpha = Math.max(0, 1.0f - (age * (1.0f - fadeOut + 0.2f)));

            renderBeam(beam, alpha, now);
        }
    }

    private void renderBeam(MageBeam beam, float alpha, long now) {
        // Create a defensive copy to avoid ConcurrentModificationException
        // (network thread can add points while render thread iterates)
        List<Vec3> rawPoints;
        synchronized (beam.points) {
            rawPoints = new ArrayList<>(beam.points);
        }
        if (rawPoints.size() < 2) return;

        // Apply Y offset to all points
        List<Vec3> points = new ArrayList<>(rawPoints.size());
        for (Vec3 p : rawPoints) {
            points.add(p.add(0, yOffset, 0));
        }

        float r, g, b, a;

        if (rainbow) {
            // Use modulo to prevent floating point precision issues with large time values
            float hue = ((now % 10000L) / 10000.0f) * rainbowSpeed;
            hue = hue % 1.0f;
            Color rainbowColor = Color.getHSBColor(hue, 1.0f, 1.0f);
            r = rainbowColor.getRed() / 255f;
            g = rainbowColor.getGreen() / 255f;
            b = rainbowColor.getBlue() / 255f;
            a = ((beamColor >> 24) & 0xFF) / 255f * alpha;
        } else {
            a = ((beamColor >> 24) & 0xFF) / 255f * alpha;
            r = ((beamColor >> 16) & 0xFF) / 255f;
            g = ((beamColor >> 8) & 0xFF) / 255f;
            b = (beamColor & 0xFF) / 255f;
        }

        switch (beamStyle) {
            case SOLID -> renderSolidBeam(points, r, g, b, a);
            case DASHED -> renderDashedBeam(points, r, g, b, a);
            case DOTTED -> renderDottedBeam(points, r, g, b, a);
            case HELIX -> renderHelixBeam(points, r, g, b, a, now, false);
            case DOUBLE_HELIX -> renderHelixBeam(points, r, g, b, a, now, true);
            case WAVE -> renderWaveBeam(points, r, g, b, a, now);
            case GRADIENT -> renderGradientBeam(points, alpha, now);
        }
    }

    private void renderSolidBeam(List<Vec3> points, float r, float g, float b, float a) {
        // PERFORMANCE: Batch all lines
        WorldLineRenderer.beginBatch(throughWalls, lineThickness);
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            WorldLineRenderer.addLine(start, end, r, g, b, a);
        }
        WorldLineRenderer.endBatch();
    }

    private void renderGradientBeam(List<Vec3> points, float alpha, long now) {
        float startR, startG, startB, endR, endG, endB;

        if (rainbow) {
            // Use modulo to prevent floating point precision issues
            float hue1 = ((now % 10000L) / 10000.0f) * rainbowSpeed;
            hue1 = hue1 % 1.0f;
            float hue2 = (hue1 + 0.5f) % 1.0f;
            Color c1 = Color.getHSBColor(hue1, 1.0f, 1.0f);
            Color c2 = Color.getHSBColor(hue2, 1.0f, 1.0f);
            startR = c1.getRed() / 255f;
            startG = c1.getGreen() / 255f;
            startB = c1.getBlue() / 255f;
            endR = c2.getRed() / 255f;
            endG = c2.getGreen() / 255f;
            endB = c2.getBlue() / 255f;
        } else {
            startR = ((beamColor >> 16) & 0xFF) / 255f;
            startG = ((beamColor >> 8) & 0xFF) / 255f;
            startB = (beamColor & 0xFF) / 255f;
            endR = ((beamColorEnd >> 16) & 0xFF) / 255f;
            endG = ((beamColorEnd >> 8) & 0xFF) / 255f;
            endB = (beamColorEnd & 0xFF) / 255f;
        }

        float a = ((beamColor >> 24) & 0xFF) / 255f * alpha;

        // PERFORMANCE: Batch all lines
        WorldLineRenderer.beginBatch(throughWalls, lineThickness);

        // Use (points.size() - 2) as divisor so gradient spans full 0-1 range
        int numSegments = points.size() - 1;
        for (int i = 0; i < numSegments; i++) {
            // t goes from 0 to 1 across all segments
            float t = (numSegments <= 1) ? 0.5f : (float) i / (numSegments - 1);
            float r = startR + (endR - startR) * t;
            float g = startG + (endG - startG) * t;
            float b = startB + (endB - startB) * t;

            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            WorldLineRenderer.addLine(start, end, r, g, b, a);
        }

        WorldLineRenderer.endBatch();
    }

    private void renderDashedBeam(List<Vec3> points, float r, float g, float b, float a) {
        // PERFORMANCE: Batch all lines
        WorldLineRenderer.beginBatch(throughWalls, lineThickness);

        float totalLength = 0;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            float segmentLength = (float) start.distanceTo(end);

            float currentPos = 0;
            float dashCycle = dashLength + dashGap;

            while (currentPos < segmentLength) {
                float cyclePos = (totalLength + currentPos) % dashCycle;
                boolean shouldDraw = cyclePos < dashLength;

                if (shouldDraw) {
                    float dashStart = currentPos;
                    float dashEnd = Math.min(currentPos + (dashLength - cyclePos), segmentLength);

                    Vec3 direction = end.subtract(start).normalize();
                    Vec3 lineStart = start.add(direction.scale(dashStart));
                    Vec3 lineEnd = start.add(direction.scale(dashEnd));

                    WorldLineRenderer.addLine(lineStart, lineEnd, r, g, b, a);

                    currentPos = dashEnd;
                } else {
                    currentPos += dashGap - (cyclePos - dashLength);
                }

                currentPos += 0.01f;
            }

            totalLength += segmentLength;
        }

        WorldLineRenderer.endBatch();
    }

    private void renderDottedBeam(List<Vec3> points, float r, float g, float b, float a) {
        // PERFORMANCE: Batch all lines
        WorldLineRenderer.beginBatch(throughWalls, lineThickness * dotSize);

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            Vec3 direction = end.subtract(start).normalize();
            float segmentLength = (float) start.distanceTo(end);

            for (float pos = 0; pos < segmentLength; pos += dotSpacing) {
                Vec3 dotPos = start.add(direction.scale(pos));
                Vec3 dotEnd = dotPos.add(direction.scale(0.05));
                WorldLineRenderer.addLine(dotPos, dotEnd, r, g, b, a);
            }
        }

        WorldLineRenderer.endBatch();
    }

    private void renderHelixBeam(List<Vec3> points, float r, float g, float b, float a, long now, boolean doubleHelix) {
        if (points.size() < 2) return;

        // Use modulo to prevent floating point precision issues with large time values
        float timeOffset = spinning ? ((now % 10000L) / 1000f * spinSpeed * (float) Math.PI * 2) : 0;

        // PERFORMANCE: Start batched rendering (single draw call at the end)
        WorldLineRenderer.beginBatch(throughWalls, lineThickness);

        float totalLength = 0;
        Vec3 prevHelix1 = null;
        Vec3 prevHelix2 = null;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            Vec3 direction = end.subtract(start).normalize();
            float segmentLength = (float) start.distanceTo(end);

            Vec3 up = new Vec3(0, 1, 0);
            if (Math.abs(direction.dot(up)) > 0.99) {
                up = new Vec3(1, 0, 0);
            }
            Vec3 right = direction.cross(up).normalize();
            Vec3 perpUp = right.cross(direction).normalize();

            // PERFORMANCE: Larger step size (0.4 instead of 0.1) = 4x fewer segments
            int steps = Math.max(2, (int) (segmentLength / 0.4f));
            for (int step = 0; step <= steps; step++) {
                float t = step / (float) steps;
                Vec3 basePos = start.add(direction.scale(segmentLength * t));
                float angle = ((totalLength + segmentLength * t) / helixPitch) * (float) Math.PI * 2 - timeOffset;

                float offsetX = (float) Math.cos(angle) * helixRadius;
                float offsetY = (float) Math.sin(angle) * helixRadius;

                Vec3 helix1 = basePos.add(right.scale(offsetX)).add(perpUp.scale(offsetY));

                if (prevHelix1 != null) {
                    WorldLineRenderer.addLine(prevHelix1, helix1, r, g, b, a);
                }
                prevHelix1 = helix1;

                if (doubleHelix) {
                    Vec3 helix2 = basePos.add(right.scale(-offsetX)).add(perpUp.scale(-offsetY));
                    if (prevHelix2 != null) {
                        WorldLineRenderer.addLine(prevHelix2, helix2, r, g, b, a);
                    }
                    prevHelix2 = helix2;
                }
            }

            totalLength += segmentLength;
        }

        // PERFORMANCE: Flush all lines in single draw call
        WorldLineRenderer.endBatch();
    }

    private void renderWaveBeam(List<Vec3> points, float r, float g, float b, float a, long now) {
        if (points.size() < 2) return;

        // Use modulo to prevent floating point precision issues with large time values
        float timeOffset = spinning ? ((now % 10000L) / 1000f * spinSpeed) : 0;

        // PERFORMANCE: Start batched rendering
        WorldLineRenderer.beginBatch(throughWalls, lineThickness);

        float totalLength = 0;
        Vec3 prevWavePos = null;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            Vec3 direction = end.subtract(start).normalize();
            float segmentLength = (float) start.distanceTo(end);

            Vec3 up = new Vec3(0, 1, 0);
            if (Math.abs(direction.dot(up)) > 0.99) {
                up = new Vec3(1, 0, 0);
            }
            Vec3 perp = direction.cross(up).normalize();

            // PERFORMANCE: Larger step size (0.4 instead of 0.1) = 4x fewer segments
            int steps = Math.max(2, (int) (segmentLength / 0.4f));
            for (int step = 0; step <= steps; step++) {
                float t = step / (float) steps;
                Vec3 basePos = start.add(direction.scale(segmentLength * t));
                float wave = (float) Math.sin((totalLength + segmentLength * t) * waveFrequency - timeOffset * Math.PI * 2) * waveAmplitude;

                Vec3 wavePos = basePos.add(perp.scale(wave));

                if (prevWavePos != null) {
                    WorldLineRenderer.addLine(prevWavePos, wavePos, r, g, b, a);
                }
                prevWavePos = wavePos;
            }

            totalLength += segmentLength;
        }

        // PERFORMANCE: Flush all lines in single draw call
        WorldLineRenderer.endBatch();
    }

    // === DATA CLASSES ===

    private static class MageBeam {
        final List<Vec3> points = new ArrayList<>();
        final int creationTick;
        int lastUpdateTick;

        MageBeam(int creationTick) {
            this.creationTick = creationTick;
            this.lastUpdateTick = creationTick;
        }
    }

    // === CONFIG ===

    @Override
    public JsonObject saveConfig() {
        JsonObject data = new JsonObject();
        data.addProperty("lineThickness", lineThickness);
        data.addProperty("durationTicks", durationTicks);
        data.addProperty("minPoints", minPoints);
        data.addProperty("beamStyle", beamStyle.name());
        data.addProperty("throughWalls", throughWalls);
        data.addProperty("fadeOut", fadeOut);
        data.addProperty("beamColor", beamColor);
        data.addProperty("beamColorEnd", beamColorEnd);
        data.addProperty("rainbow", rainbow);
        data.addProperty("rainbowSpeed", rainbowSpeed);
        data.addProperty("spinning", spinning);
        data.addProperty("spinSpeed", spinSpeed);
        data.addProperty("dashLength", dashLength);
        data.addProperty("dashGap", dashGap);
        data.addProperty("dotSpacing", dotSpacing);
        data.addProperty("dotSize", dotSize);
        data.addProperty("helixRadius", helixRadius);
        data.addProperty("helixPitch", helixPitch);
        data.addProperty("waveAmplitude", waveAmplitude);
        data.addProperty("waveFrequency", waveFrequency);
        data.addProperty("yOffset", yOffset);
        data.addProperty("hideParticles", hideParticles);
        return data;
    }

    @Override
    public void loadConfig(JsonObject data) {
        if (data == null) return;
        if (data.has("lineThickness")) lineThickness = data.get("lineThickness").getAsFloat();
        if (data.has("durationTicks")) durationTicks = data.get("durationTicks").getAsInt();
        if (data.has("minPoints")) minPoints = data.get("minPoints").getAsInt();
        if (data.has("beamStyle")) {
            try {
                beamStyle = BeamStyle.valueOf(data.get("beamStyle").getAsString());
            } catch (IllegalArgumentException ignored) {}
        }
        if (data.has("throughWalls")) throughWalls = data.get("throughWalls").getAsBoolean();
        if (data.has("fadeOut")) fadeOut = data.get("fadeOut").getAsFloat();
        if (data.has("beamColor")) beamColor = data.get("beamColor").getAsInt();
        if (data.has("beamColorEnd")) beamColorEnd = data.get("beamColorEnd").getAsInt();
        if (data.has("rainbow")) rainbow = data.get("rainbow").getAsBoolean();
        if (data.has("rainbowSpeed")) rainbowSpeed = data.get("rainbowSpeed").getAsFloat();
        if (data.has("spinning")) spinning = data.get("spinning").getAsBoolean();
        if (data.has("spinSpeed")) spinSpeed = data.get("spinSpeed").getAsFloat();
        if (data.has("dashLength")) dashLength = data.get("dashLength").getAsFloat();
        if (data.has("dashGap")) dashGap = data.get("dashGap").getAsFloat();
        if (data.has("dotSpacing")) dotSpacing = data.get("dotSpacing").getAsFloat();
        if (data.has("dotSize")) dotSize = data.get("dotSize").getAsFloat();
        if (data.has("helixRadius")) helixRadius = data.get("helixRadius").getAsFloat();
        if (data.has("helixPitch")) helixPitch = data.get("helixPitch").getAsFloat();
        if (data.has("waveAmplitude")) waveAmplitude = data.get("waveAmplitude").getAsFloat();
        if (data.has("waveFrequency")) waveFrequency = data.get("waveFrequency").getAsFloat();
        if (data.has("yOffset")) yOffset = data.get("yOffset").getAsFloat();
        if (data.has("hideParticles")) hideParticles = data.get("hideParticles").getAsBoolean();
    }

    @Override
    public List<ModuleSetting> getSettings() {
        List<ModuleSetting> settings = new ArrayList<>();

        // === STYLE SELECTOR ===
        String[] styleOptions = new String[BeamStyle.values().length];
        for (int i = 0; i < BeamStyle.values().length; i++) {
            styleOptions[i] = BeamStyle.values()[i].toString();
        }
        settings.add(new DropdownSetting(
            "Style",
            "Beam rendering style",
            "magebeam_style",
            styleOptions,
            () -> beamStyle.ordinal(),
            idx -> beamStyle = BeamStyle.values()[idx]
        ));

        // === GENERAL SETTINGS ===
        settings.add(new ColorPickerSetting(
            "Beam Color",
            "Main beam color (RGBA)",
            "magebeam_color",
            () -> beamColor,
            color -> beamColor = color
        ));

        settings.add(new ColorPickerSetting(
            "End Color",
            "Gradient end color (RGBA)",
            "magebeam_color_end",
            () -> beamColorEnd,
            color -> beamColorEnd = color
        ).setVisible(() -> beamStyle == BeamStyle.GRADIENT));

        settings.add(new SliderSetting(
            "Thickness",
            "Line thickness in pixels",
            "magebeam_thickness",
            1.0f, 10.0f,
            () -> lineThickness,
            val -> lineThickness = val
        ).withDecimals(1).withSuffix("px"));

        settings.add(new SliderSetting(
            "Duration",
            "How long beams stay visible (ticks)",
            "magebeam_duration",
            10.0f, 100.0f,
            () -> (float) durationTicks,
            val -> durationTicks = Math.round(val)
        ).withDecimals(0).withSuffix(" ticks"));

        settings.add(new SliderSetting(
            "Min Points",
            "Minimum particle points to render",
            "magebeam_minpoints",
            1.0f, 20.0f,
            () -> (float) minPoints,
            val -> minPoints = Math.round(val)
        ).withDecimals(0));

        settings.add(new SliderSetting(
            "Fade Out",
            "How slowly beams fade (0=fast, 1=slow)",
            "magebeam_fadeout",
            0.0f, 1.0f,
            () -> fadeOut,
            val -> fadeOut = val
        ).withDecimals(2));

        settings.add(new CheckboxSetting(
            "Through Walls",
            "Render beams through blocks",
            "magebeam_throughwalls",
            () -> throughWalls,
            val -> throughWalls = val
        ));

        settings.add(new CheckboxSetting(
            "Hide Particles",
            "Hide vanilla firework particles",
            "magebeam_hideparticles",
            () -> hideParticles,
            val -> hideParticles = val
        ));

        settings.add(new SliderSetting(
            "Y Offset",
            "Adjust beam height (+ = higher, - = lower)",
            "magebeam_yoffset",
            -1.0f, 1.0f,
            () -> yOffset,
            val -> yOffset = val
        ).withDecimals(2).withSuffix("m"));

        // === COLOR EFFECTS ===
        settings.add(new CheckboxSetting(
            "Rainbow",
            "Cycle through rainbow colors",
            "magebeam_rainbow",
            () -> rainbow,
            val -> rainbow = val
        ));

        settings.add(new SliderSetting(
            "Rainbow Speed",
            "How fast rainbow cycles",
            "magebeam_rainbowspeed",
            0.1f, 5.0f,
            () -> rainbowSpeed,
            val -> rainbowSpeed = val
        ).withDecimals(1).setVisible(() -> rainbow));

        // === ANIMATION SETTINGS ===
        settings.add(new CheckboxSetting(
            "Spinning",
            "Animate helix/wave effects",
            "magebeam_spinning",
            () -> spinning,
            val -> spinning = val
        ).setVisible(() -> beamStyle == BeamStyle.HELIX || beamStyle == BeamStyle.DOUBLE_HELIX || beamStyle == BeamStyle.WAVE));

        settings.add(new SliderSetting(
            "Spin Speed",
            "Animation speed",
            "magebeam_spinspeed",
            0.5f, 10.0f,
            () -> spinSpeed,
            val -> spinSpeed = val
        ).withDecimals(1).setVisible(() -> spinning && (beamStyle == BeamStyle.HELIX || beamStyle == BeamStyle.DOUBLE_HELIX || beamStyle == BeamStyle.WAVE)));

        // === DASHED MODE SETTINGS ===
        settings.add(new SliderSetting(
            "Dash Length",
            "Length of each dash",
            "magebeam_dashlength",
            0.1f, 2.0f,
            () -> dashLength,
            val -> dashLength = val
        ).withDecimals(2).withSuffix("m").setVisible(() -> beamStyle == BeamStyle.DASHED));

        settings.add(new SliderSetting(
            "Dash Gap",
            "Gap between dashes",
            "magebeam_dashgap",
            0.1f, 1.0f,
            () -> dashGap,
            val -> dashGap = val
        ).withDecimals(2).withSuffix("m").setVisible(() -> beamStyle == BeamStyle.DASHED));

        // === DOTTED MODE SETTINGS ===
        settings.add(new SliderSetting(
            "Dot Spacing",
            "Space between dots",
            "magebeam_dotspacing",
            0.1f, 1.0f,
            () -> dotSpacing,
            val -> dotSpacing = val
        ).withDecimals(2).withSuffix("m").setVisible(() -> beamStyle == BeamStyle.DOTTED));

        settings.add(new SliderSetting(
            "Dot Size",
            "Size multiplier for dots",
            "magebeam_dotsize",
            0.5f, 3.0f,
            () -> dotSize,
            val -> dotSize = val
        ).withDecimals(1).withSuffix("x").setVisible(() -> beamStyle == BeamStyle.DOTTED));

        // === HELIX MODE SETTINGS ===
        settings.add(new SliderSetting(
            "Helix Radius",
            "Radius of the helix spiral",
            "magebeam_helixradius",
            0.05f, 0.5f,
            () -> helixRadius,
            val -> helixRadius = val
        ).withDecimals(2).withSuffix("m").setVisible(() -> beamStyle == BeamStyle.HELIX || beamStyle == BeamStyle.DOUBLE_HELIX));

        settings.add(new SliderSetting(
            "Helix Pitch",
            "Distance per full rotation",
            "magebeam_helixpitch",
            0.1f, 2.0f,
            () -> helixPitch,
            val -> helixPitch = val
        ).withDecimals(2).withSuffix("m").setVisible(() -> beamStyle == BeamStyle.HELIX || beamStyle == BeamStyle.DOUBLE_HELIX));

        // === WAVE MODE SETTINGS ===
        settings.add(new SliderSetting(
            "Wave Amplitude",
            "Height of the wave",
            "magebeam_waveamp",
            0.05f, 0.5f,
            () -> waveAmplitude,
            val -> waveAmplitude = val
        ).withDecimals(2).withSuffix("m").setVisible(() -> beamStyle == BeamStyle.WAVE));

        settings.add(new SliderSetting(
            "Wave Frequency",
            "Number of waves per meter",
            "magebeam_wavefreq",
            1.0f, 10.0f,
            () -> waveFrequency,
            val -> waveFrequency = val
        ).withDecimals(1).withSuffix("/m").setVisible(() -> beamStyle == BeamStyle.WAVE));

        return settings;
    }

    @Override
    public void onTick() {
        // Handled by ClientTickEvents
    }
}
