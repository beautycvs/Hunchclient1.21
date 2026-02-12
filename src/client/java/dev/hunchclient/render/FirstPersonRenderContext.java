package dev.hunchclient.render;

import net.minecraft.world.item.ItemDisplayContext;

/**
 * Helper class to track if we're currently rendering a first-person item
 * Used to communicate between HeldItemRendererMixin and ItemRenderStateLayerMixin
 */
public class FirstPersonRenderContext {

    private static final ThreadLocal<Boolean> isFirstPerson = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<ItemDisplayContext> displayContext = ThreadLocal.withInitial(() -> ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);

    public static void setFirstPerson(boolean firstPerson) {
        isFirstPerson.set(firstPerson);
    }

    public static void setDisplayContext(ItemDisplayContext context) {
        displayContext.set(context);
    }

    public static boolean isFirstPerson() {
        return isFirstPerson.get();
    }

    public static ItemDisplayContext getDisplayContext() {
        return displayContext.get();
    }

    public static void reset() {
        isFirstPerson.set(false);
        displayContext.set(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }
}
