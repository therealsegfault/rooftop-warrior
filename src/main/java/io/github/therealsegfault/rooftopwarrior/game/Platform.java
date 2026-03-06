package io.github.therealsegfault.rooftopwarrior.game;

public class Platform {
    public float worldX; // position in world space
    public float y;
    public float w;
    public float h;

    public Platform(float worldX, float y, float w, float h) {
        this.worldX = worldX;
        this.y      = y;
        this.w      = w;
        this.h      = h;
    }

    // Screen position = world position minus how far world has scrolled
    public float screenX(float worldOffset) {
        return worldX - worldOffset;
    }
}
