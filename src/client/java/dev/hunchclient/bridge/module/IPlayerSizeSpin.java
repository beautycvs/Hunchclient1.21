package dev.hunchclient.bridge.module;

public interface IPlayerSizeSpin {
    IPlayerModel getPlayerModel(String playerName, boolean isSelf);

    interface IPlayerModel {
        boolean isSpin();
        float getSpinSpeed();
        boolean isInvertSpin();
        boolean isUpsideDown();
        float getScaleX();
        float getScaleY();
        float getScaleZ();
    }
}
