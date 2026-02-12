package dev.hunchclient.util.animation;

/** Base animation class with time-based interpolation. */
public abstract class Animation<T extends Number & Comparable<T>> {
    private final long duration;
    private long startTime = 0L;
    private boolean animating = false;
    private boolean reversed = false;

    public Animation(long duration) {
        this.duration = duration;
    }

    public void start() {
        long currentTime = System.currentTimeMillis();

        if (!animating) {
            animating = true;
            reversed = false;
            startTime = currentTime;
            return;
        }

        float percent = Math.min(Math.max((currentTime - startTime) / (float)duration, 0.0f), 1.0f);
        reversed = !reversed;
        startTime = currentTime - (long)((1.0f - percent) * duration);
    }

    public float getPercent() {
        if (!animating) return 100.0f;

        float percent = ((System.currentTimeMillis() - startTime) / (float)duration * 100.0f);
        if (percent >= 100.0f) {
            animating = false;
            return 100.0f;
        }
        return Math.min(percent, 100.0f);
    }

    public boolean isAnimating() {
        return animating;
    }

    public abstract T get(T start, T end, boolean reverse);
}
