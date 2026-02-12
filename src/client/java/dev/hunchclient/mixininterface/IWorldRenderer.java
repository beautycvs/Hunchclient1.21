package dev.hunchclient.mixininterface;

import com.mojang.blaze3d.pipeline.RenderTarget;

public interface IWorldRenderer {
    void hunchclient$pushEntityOutlineFramebuffer(RenderTarget framebuffer);
    void hunchclient$popEntityOutlineFramebuffer();
}
