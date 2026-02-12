package dev.hunchclient.gui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Custom Font Loader for HunchClient
 * Loads TTF/OTF fonts from config directory
 */
public class CustomFontLoader {

    private static final Logger LOGGER = LogManager.getLogger("HunchClient/FontLoader");
    private static CustomFontLoader instance;

    // Font directory path
    private static final Path FONTS_DIR = Paths.get("config", "hunchclient", "fonts");

    // Loaded fonts cache
    private final Map<String, java.awt.Font> loadedFonts = new HashMap<>();
    private final Map<String, Font> fontRenderers = new HashMap<>();

    // Default fonts
    private Font defaultFont;
    private Font boldFont;
    private Font italicFont;
    private Font monoFont;

    private CustomFontLoader() {
        initializeFontsDirectory();
        loadBuiltinFonts();
        loadCustomFonts();
    }

    public static CustomFontLoader getInstance() {
        if (instance == null) {
            instance = new CustomFontLoader();
        }
        return instance;
    }

    /**
     * Initialize fonts directory and create README
     */
    private void initializeFontsDirectory() {
        try {
            if (!Files.exists(FONTS_DIR)) {
                Files.createDirectories(FONTS_DIR);
                LOGGER.info("Created fonts directory: {}", FONTS_DIR);

                // Create README.txt with instructions
                String readme = """
                    === HunchClient Custom Fonts ===

                    Place your custom font files in this directory.
                    Supported formats: .ttf, .otf

                    Font files will be automatically loaded when you start Minecraft.
                    You can select them in the GUI Theme Settings.

                    Recommended fonts:
                    - Roboto.ttf (Modern, clean)
                    - JetBrainsMono.ttf (Great for monospace)
                    - Inter.ttf (Professional UI font)
                    - Poppins.ttf (Rounded, friendly)

                    Download free fonts from:
                    - Google Fonts: https://fonts.google.com
                    - Font Squirrel: https://www.fontsquirrel.com
                    - DaFont: https://www.dafont.com

                    Note: Font files are loaded at startup.
                    Restart Minecraft after adding new fonts.
                    """;

                Path readmePath = FONTS_DIR.resolve("README.txt");
                Files.writeString(readmePath, readme);
                LOGGER.info("Created README.txt in fonts directory");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialize fonts directory", e);
        }
    }

    /**
     * Load built-in font variations
     */
    private void loadBuiltinFonts() {
        Minecraft client = Minecraft.getInstance();

        // Get default font renderer
        defaultFont = client.font;
        fontRenderers.put("default", defaultFont);

        // Create variations (these are simulated since Minecraft doesn't have true bold/italic)
        boldFont = defaultFont; // Would need custom implementation
        italicFont = defaultFont; // Would need custom implementation
        monoFont = defaultFont; // Would need custom implementation

        fontRenderers.put("bold", boldFont);
        fontRenderers.put("italic", italicFont);
        fontRenderers.put("monospace", monoFont);

        LOGGER.info("Loaded built-in font variations");
    }

    /**
     * Load custom fonts from directory
     */
    private void loadCustomFonts() {
        if (!Files.exists(FONTS_DIR)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(FONTS_DIR, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> {
                     String name = p.getFileName().toString().toLowerCase();
                     return name.endsWith(".ttf") || name.endsWith(".otf");
                 })
                 .forEach(this::loadFontFile);
        } catch (IOException e) {
            LOGGER.error("Failed to load custom fonts", e);
        }
    }

    /**
     * Load a single font file
     */
    private void loadFontFile(Path fontPath) {
        try {
            File fontFile = fontPath.toFile();
            String fontName = fontFile.getName()
                .replaceAll("\\.(ttf|otf)$", "")
                .toLowerCase();

            // Load the font using AWT
            java.awt.Font awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, fontFile);
            loadedFonts.put(fontName, awtFont);

            // Create Minecraft TextRenderer from the font
            // Note: This is a simplified version. Real implementation would need
            // to integrate with Minecraft's font system properly
            Font renderer = createTextRenderer(awtFont);
            if (renderer != null) {
                fontRenderers.put(fontName, renderer);
                LOGGER.info("Loaded custom font: {}", fontName);
            }

        } catch (IOException | java.awt.FontFormatException e) {
            LOGGER.error("Failed to load font: {}", fontPath, e);
        }
    }

    /**
     * Create a TextRenderer from AWT Font
     * This is a simplified placeholder - real implementation would need proper integration
     */
    private Font createTextRenderer(java.awt.Font awtFont) {
        // In a real implementation, we would need to:
        // 1. Convert AWT Font to bitmap font sheets
        // 2. Create FontStorage with the glyphs
        // 3. Build TextRenderer with the custom font

        // For now, return default renderer as placeholder
        return Minecraft.getInstance().font;
    }

    /**
     * Get TextRenderer for a font name
     */
    public Font getFont(String fontName) {
        if (fontName == null || fontName.isEmpty()) {
            return defaultFont;
        }
        return fontRenderers.getOrDefault(fontName.toLowerCase(), defaultFont);
    }

    /**
     * Get all available font names
     */
    public String[] getAvailableFonts() {
        return fontRenderers.keySet().toArray(new String[0]);
    }

    /**
     * Check if a font is loaded
     */
    public boolean hasFont(String fontName) {
        return fontRenderers.containsKey(fontName.toLowerCase());
    }

    /**
     * Get scaled font size
     */
    public float getScaledFontSize(float scale) {
        return 9.0f * scale; // Minecraft default font size is 9
    }

    /**
     * Reload all fonts
     */
    public void reloadFonts() {
        LOGGER.info("Reloading custom fonts...");
        loadedFonts.clear();
        fontRenderers.clear();
        loadBuiltinFonts();
        loadCustomFonts();
        LOGGER.info("Font reload complete. Loaded {} fonts", fontRenderers.size());
    }

    /**
     * Enhanced font rendering with custom fonts
     * This would be used by GUI components
     */
    public static class FontRenderHelper {

        /**
         * Draw text with custom font
         */
        public static void drawText(GuiGraphics context, String text, int x, int y,
                                   String fontName, float fontSize, int color) {
            CustomFontLoader loader = getInstance();
            Font renderer = loader.getFont(fontName);

            // For now, ignore fontSize scaling due to API changes
            // Would need proper implementation for matrix operations in 1.21
            context.drawString(renderer, text, x, y, color, false);
        }

        /**
         * Draw centered text with custom font
         */
        public static void drawCenteredText(GuiGraphics context, String text,
                                           int centerX, int y,
                                           String fontName, float fontSize, int color) {
            CustomFontLoader loader = getInstance();
            Font renderer = loader.getFont(fontName);
            int width = renderer.width(text);
            int x = centerX - width / 2;
            drawText(context, text, x, y, fontName, fontSize, color);
        }

        /**
         * Draw text with shadow
         */
        public static void drawTextWithShadow(GuiGraphics context, String text,
                                             int x, int y,
                                             String fontName, float fontSize, int color) {
            CustomFontLoader loader = getInstance();
            Font renderer = loader.getFont(fontName);

            // For now, ignore fontSize scaling due to API changes
            context.drawString(renderer, text, x, y, color, true); // true = with shadow
        }

        /**
         * Get text width with custom font
         */
        public static int getTextWidth(String text, String fontName, float fontSize) {
            CustomFontLoader loader = getInstance();
            Font renderer = loader.getFont(fontName);
            return (int)(renderer.width(text) * fontSize);
        }

        /**
         * Get text height with custom font
         */
        public static int getTextHeight(String fontName, float fontSize) {
            CustomFontLoader loader = getInstance();
            Font renderer = loader.getFont(fontName);
            return (int)(renderer.lineHeight * fontSize);
        }
    }
}