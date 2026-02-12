package dev.hunchclient.render;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** NanoVG font wrapper with resource caching. */
public class Font {
    public final String name;
    private final String resourcePath;
    private final byte[] cachedBytes;

    public Font(String name, String resourcePath) {
        this.name = name;
        this.resourcePath = resourcePath;
        this.cachedBytes = null;
    }

    public Font(String name, InputStream inputStream) {
        this.name = name;
        this.resourcePath = null;
        try {
            this.cachedBytes = inputStream.readAllBytes();
            inputStream.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read font from input stream", e);
        }
    }

    public ByteBuffer buffer() {
        byte[] bytes;
        if (cachedBytes != null) {
            bytes = cachedBytes;
        } else {
            try (InputStream stream = this.getClass().getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    throw new FileNotFoundException(resourcePath);
                }
                bytes = stream.readAllBytes();
            } catch (Exception e) {
                throw new RuntimeException("Failed to load font: " + resourcePath, e);
            }
        }

        return ByteBuffer.allocateDirect(bytes.length)
                .order(ByteOrder.nativeOrder())
                .put(bytes)
                .flip();
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Font && name.equals(((Font) other).name);
    }
}
