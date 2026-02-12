package dev.hunchclient.accessor;

/**
 * Accessor interface to store the actual player name before NameProtect modifies it
 */
public interface PlayerEntityRenderStateAccessor {
    void hunchclient$setActualPlayerName(String name);
    String hunchclient$getActualPlayerName();
}
