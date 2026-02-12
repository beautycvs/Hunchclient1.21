package dev.hunchclient.bridge.module;

public interface IKaomojiReplacer {
    String replaceKaomojis(String message);
    boolean containsKaomoji(String message);
}
