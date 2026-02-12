package dev.hunchclient.bridge.module;

public interface ICustomFont {
    boolean isEnabled();
    boolean shouldReplaceMinecraftFont();
    Object getSelectedFont();
    float getFontSize();
}
