package com.pgmacdesign.mc3dprint.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wraps a translucent buffer and rewrites every vertex color to a fixed tint
 * and alpha — the standard "ghost block" technique (Building Gadgets, Create
 * schematics, Litematica all do a variant of this).
 */
public class GhostVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float tintRed;
    private final float tintGreen;
    private final float tintBlue;
    private final int alpha;

    public GhostVertexConsumer(VertexConsumer delegate, float tintRed, float tintGreen,
                               float tintBlue, int alpha) {
        this.delegate = delegate;
        this.tintRed = tintRed;
        this.tintGreen = tintGreen;
        this.tintBlue = tintBlue;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int ignoredAlpha) {
        delegate.color((int) (red * tintRed), (int) (green * tintGreen), (int) (blue * tintBlue), alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int ignoredAlpha) {
        delegate.defaultColor((int) (red * tintRed), (int) (green * tintGreen), (int) (blue * tintBlue), alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
