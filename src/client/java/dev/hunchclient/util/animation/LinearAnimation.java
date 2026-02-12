package dev.hunchclient.util.animation;

/** Linear animation interpolation. */
public class LinearAnimation<E extends Number & Comparable<E>> extends Animation<E> {

    public LinearAnimation(long duration) {
        super(duration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(E start, E end, boolean reverse) {
        E startVal = reverse ? end : start;
        E endVal = reverse ? start : end;

        if (!isAnimating()) {
            return reverse ? start : end;
        }

        float startFloat = startVal.floatValue();
        float endFloat = endVal.floatValue();
        float result = startFloat + (endFloat - startFloat) * (getPercent() / 100.0f);

        // Cast back to the original type
        if (start instanceof Integer) {
            return (E) Integer.valueOf((int) result);
        } else if (start instanceof Float) {
            return (E) Float.valueOf(result);
        } else if (start instanceof Double) {
            return (E) Double.valueOf((double) result);
        } else if (start instanceof Long) {
            return (E) Long.valueOf((long) result);
        }

        return (E) Float.valueOf(result);
    }
}
