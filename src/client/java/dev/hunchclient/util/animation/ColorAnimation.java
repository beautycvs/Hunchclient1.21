package dev.hunchclient.util.animation;

import dev.hunchclient.util.Color;

/** Color animation with smooth transitions. */
public class ColorAnimation {
    private final LinearAnimation<Integer> anim;

    public ColorAnimation(long duration) {
        this.anim = new LinearAnimation<>(duration);
    }

    public void start() {
        anim.start();
    }

    public boolean isAnimating() {
        return anim.isAnimating();
    }

    public float getPercent() {
        return anim.getPercent();
    }

    public Color get(Color start, Color end, boolean reverse) {
        int r = anim.get(start.getRed(), end.getRed(), reverse);
        int g = anim.get(start.getGreen(), end.getGreen(), reverse);
        int b = anim.get(start.getBlue(), end.getBlue(), reverse);
        int a = anim.get(start.getAlpha(), end.getAlpha(), reverse);

        return new Color(r, g, b, a / 255.0f);
    }
}
