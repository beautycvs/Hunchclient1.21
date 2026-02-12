package dev.hunchclient.gui;

import net.minecraft.util.Mth;

/**
 * Smooth animation helper for GUI elements
 * Provides easing functions and animation state management
 */
public class GuiAnimations {

    /**
     * Animated value that smoothly transitions to a target
     */
    public static class AnimatedValue {
        private float current;
        private float target;
        private final float speed; // How fast to animate (0.0-1.0, higher = faster)

        public AnimatedValue(float initial, float speed) {
            this.current = initial;
            this.target = initial;
            this.speed = Mth.clamp(speed, 0.01f, 1.0f);
        }

        public void setTarget(float target) {
            this.target = target;
        }

        public void update(float deltaTime) {
            if (Math.abs(current - target) < 0.001f) {
                current = target;
                return;
            }

            // Smooth exponential ease-out
            float diff = target - current;
            current += diff * speed * Math.min(deltaTime * 60f, 1.0f); // 60 FPS normalized
        }

        public float get() {
            return current;
        }

        public boolean isAnimating() {
            return Math.abs(current - target) > 0.001f;
        }

        public void setImmediate(float value) {
            this.current = value;
            this.target = value;
        }
    }

    /**
     * Fade animation for opacity
     */
    public static class FadeAnimation extends AnimatedValue {
        public FadeAnimation(float speed) {
            super(0.0f, speed);
        }

        public void fadeIn() {
            setTarget(1.0f);
        }

        public void fadeOut() {
            setTarget(0.0f);
        }

        public int applyAlpha(int color) {
            int alpha = (int) (get() * 255);
            return (color & 0x00FFFFFF) | (alpha << 24);
        }
    }

    /**
     * Smooth scroll handler
     */
    public static class SmoothScroll {
        private float current;
        private float target;
        private final float friction = 0.25f; // Higher = faster deceleration

        public SmoothScroll() {
            this.current = 0;
            this.target = 0;
        }

        public void scroll(float amount) {
            target += amount;
        }

        public void setTarget(float target) {
            this.target = target;
        }

        public void setMin(float min) {
            if (target < min) target = min;
            if (current < min) current = min;
        }

        public void setMax(float max) {
            if (target > max) target = max;
            if (current > max) current = max;
        }

        public void update() {
            if (Math.abs(current - target) < 0.1f) {
                current = target;
                return;
            }

            float diff = target - current;
            current += diff * friction;
        }

        public int getCurrent() {
            return Math.round(current);
        }

        public void setImmediate(float value) {
            current = value;
            target = value;
        }
    }

    /**
     * Easing functions
     */
    public static class Ease {
        public static float inOutQuad(float t) {
            return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
        }

        public static float outCubic(float t) {
            return 1 - (float) Math.pow(1 - t, 3);
        }

        public static float inOutCubic(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
        }

        public static float outBack(float t) {
            float c1 = 1.70158f;
            float c3 = c1 + 1;
            return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
        }
    }

    /**
     * Pulse animation for attention-grabbing
     */
    public static class PulseAnimation {
        private float time;
        private final float speed;

        public PulseAnimation(float speed) {
            this.speed = speed;
        }

        public void update(float deltaTime) {
            time += deltaTime * speed;
        }

        public float getPulse() {
            return (float) ((Math.sin(time) + 1.0) / 2.0); // 0.0 to 1.0
        }

        public int applyBrightness(int color, float min, float max) {
            float brightness = min + getPulse() * (max - min);
            int r = (int) (((color >> 16) & 0xFF) * brightness);
            int g = (int) (((color >> 8) & 0xFF) * brightness);
            int b = (int) ((color & 0xFF) * brightness);
            return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
        }
    }
}
