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
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int ignoredAlpha) {
        delegate.setColor((int) (red * tintRed), (int) (green * tintGreen), (int) (blue * tintBlue), alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    //? if >=1.21.11 {
    /*@Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        // Route the packed form through the tinting overload so ghosts stay tinted.
        return setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >> 24) & 0xFF);
    }
    *///?}
}
