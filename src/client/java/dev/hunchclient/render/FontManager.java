package dev.hunchclient.render;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages custom fonts loaded from config/hunchclient/fonts/
 * Allows users to add .ttf/.otf fonts and select them dynamically
 */
public class FontManager {
    private static final Path FONTS_DIR = Paths.get("config", "hunchclient", "fonts");

    private static final Map<String, Font> loadedFonts = new HashMap<>();
    private static final List<String> availableFontNames = new ArrayList<>();
    private static final Object LOAD_LOCK = new Object();
    private static volatile boolean loaded = false;

    // System fallback fonts (Windows + Linux)
    private static final String[] SYSTEM_FONTS = {
        // Windows
        "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/consola.ttf",
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/calibri.ttf",
        // Linux Fedora/Nobara (exact paths)
        "/usr/share/fonts/liberation-sans-fonts/LiberationSans-Regular.ttf",
        "/usr/share/fonts/open-sans/OpenSans-Regular.ttf",
        "/usr/share/fonts/liberation-narrow/LiberationSansNarrow-Regular.ttf",
        // Linux - common locations
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
        "/usr/share/fonts/google-noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
    };

    static {
        // Create fonts directory if it doesn't exist
        try {
            Files.createDirectories(FONTS_DIR);
            System.out.println("[FontManager] Fonts directory: " + FONTS_DIR.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[FontManager] Failed to create fonts directory: " + e.getMessage());
        }

    }

    /**
     * Load all fonts from config/hunchclient/fonts/ directory
     */
    public static void loadAllFonts() {
        synchronized (LOAD_LOCK) {
            loadAllFontsInternal();
            loaded = true;
        }
    }

    /**
     * Ensure fonts are loaded once (lazy init).
     */
    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOAD_LOCK) {
            if (loaded) {
                return;
            }
            loadAllFontsInternal();
            loaded = true;
        }
    }

    private static void loadAllFontsInternal() {
        loadedFonts.clear();
        availableFontNames.clear();

        System.out.println("[FontManager] Loading fonts...");

        // 1. Load system fallback fonts
        loadSystemFonts();

        // 2. Load custom fonts from config/hunchclient/fonts/
        loadCustomFonts();

        System.out.println("[FontManager] Loaded " + loadedFonts.size() + " fonts: " + availableFontNames);
    }

    /**
     * Reload all fonts (useful for GUI reload button)
     */
    public static void reloadFonts() {
        System.out.println("[FontManager] Reloading fonts...");
        loadAllFonts();

        // Clear NVGRenderer cache so it re-loads fonts
        NVGRenderer.clearFontCache();

        System.out.println("[FontManager] Font reload complete!");
    }

    private static void loadSystemFonts() {
        for (String systemFontPath : SYSTEM_FONTS) {
            File fontFile = new File(systemFontPath);
            if (!fontFile.exists()) continue;

            try {
                String fontName = getFontNameFromFile(fontFile);
                Font font = new Font(fontName, new FileInputStream(fontFile));
                loadedFonts.put(fontName, font);
                availableFontNames.add(fontName);
                System.out.println("[FontManager] Loaded system font: " + fontName);
            } catch (Exception e) {
                System.err.println("[FontManager] Failed to load system font " + systemFontPath + ": " + e.getMessage());
            }
        }
    }

    private static void loadCustomFonts() {
        File fontsDir = FONTS_DIR.toFile();
        if (!fontsDir.exists() || !fontsDir.isDirectory()) {
            System.out.println("[FontManager] Fonts directory does not exist, skipping custom fonts");
            return;
        }

        try (Stream<Path> paths = Files.walk(FONTS_DIR, 1)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> {
                     String name = path.getFileName().toString().toLowerCase();
                     return name.endsWith(".ttf") || name.endsWith(".otf");
                 })
                 .forEach(fontPath -> {
                     try {
                         File fontFile = fontPath.toFile();
                         String fontName = getFontNameFromFile(fontFile);
                         Font font = new Font(fontName, new FileInputStream(fontFile));
                         loadedFonts.put(fontName, font);
                         availableFontNames.add(fontName);
                         System.out.println("[FontManager] Loaded custom font: " + fontName + " from " + fontPath.getFileName());
                     } catch (Exception e) {
                         System.err.println("[FontManager] Failed to load font " + fontPath + ": " + e.getMessage());
                     }
                 });
        } catch (IOException e) {
            System.err.println("[FontManager] Error scanning fonts directory: " + e.getMessage());
        }
    }

    /**
     * Extract a readable name from font file name
     */
    private static String getFontNameFromFile(File file) {
        String name = file.getName();
        // Remove extension
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        // Capitalize first letter
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return name;
    }

    /**
     * Get a font by name
     */
    public static Font getFont(String name) {
        ensureLoaded();
        return loadedFonts.get(name);
    }

    /**
     * Get all available font names
     */
    public static List<String> getAvailableFontNames() {
        ensureLoaded();
        return new ArrayList<>(availableFontNames);
    }

    /**
     * Get the default font (first available)
     */
    public static Font getDefaultFont() {
        ensureLoaded();
        if (availableFontNames.isEmpty()) {
            System.err.println("[FontManager] No fonts available!");
            return null;
        }
        return loadedFonts.get(availableFontNames.get(0));
    }

    /**
     * Check if a font exists
     */
    public static boolean hasFont(String name) {
        ensureLoaded();
        return loadedFonts.containsKey(name);
    }

    /**
     * Get fonts directory path for user information
     */
    public static Path getFontsDirectory() {
        return FONTS_DIR;
    }
}
