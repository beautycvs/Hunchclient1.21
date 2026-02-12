package dev.hunchclient.render;

import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;

/** NanoVG image wrapper for loading textures from disk or resources. */
public class Image {
    public final String identifier;
    public boolean isSVG;
    public InputStream stream;
    private ByteBuffer buffer;

    public Image(String identifier) {
        this.identifier = identifier;
        this.isSVG = identifier.toLowerCase().endsWith(".svg");
        this.stream = getStream(identifier);
    }

    public ByteBuffer buffer() {
        if (buffer == null) {
            try {
                byte[] bytes = stream.readAllBytes();
                buffer = MemoryUtil.memAlloc(bytes.length).put(bytes);
                buffer.flip();
                stream.close();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load image buffer", e);
            }
        }
        return buffer;
    }

    public void releaseBuffer() {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
            buffer = null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Image)) return false;
        return identifier.equals(((Image) other).identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    private static InputStream getStream(String path) {
        String trimmedPath = path.trim();
        try {
            if (trimmedPath.startsWith("http")) {
                // Load from URL
                URI uri = URI.create(trimmedPath);
                return uri.toURL().openStream();
            } else {
                // Try file system first
                File file = new File(trimmedPath);
                if (file.exists() && file.isFile()) {
                    return Files.newInputStream(file.toPath());
                }
                // Try class resources
                InputStream stream = Image.class.getResourceAsStream(trimmedPath);
                if (stream == null) {
                    throw new FileNotFoundException(trimmedPath);
                }
                return stream;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load image: " + trimmedPath, e);
        }
    }
}
