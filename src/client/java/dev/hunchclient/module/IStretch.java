package dev.hunchclient.module;

/**
 * Public API for Stretch functionality - called by mixins
 * This interface stays readable, implementation gets obfuscated
 */
public interface IStretch {
    boolean shouldApplyStretch();
    float calculateHorizontalScale();
}
