package dev.hunchclient.bridge.module;

public interface IIrcRelay {
    boolean isEnabled();
    boolean handleOutgoingChat(String message);
}
