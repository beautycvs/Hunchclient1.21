package dev.hunchclient.mixin.client;

import dev.hunchclient.bridge.CommandBridge;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;

/**
 * Adds tab-completion support for . prefix commands
 */
@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    EditBox input;

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void onRefresh(CallbackInfo ci) {
        String text = this.input.getValue();

        // Check if this is a . command
        if (text.startsWith(CommandBridge.SECONDARY_PREFIX) ||
            text.startsWith(CommandBridge.HUNCHCLIENT_PREFIX) ||
            text.startsWith(CommandBridge.HC_PREFIX)) {

            // Get suggestions from our command manager
            List<String> suggestions = CommandBridge.getSuggestions(text);

            if (!suggestions.isEmpty()) {
                // Create a SuggestionsBuilder starting at position 0
                SuggestionsBuilder builder = new SuggestionsBuilder(text, 0);

                // Add all our suggestions
                for (String suggestion : suggestions) {
                    builder.suggest(suggestion);
                }

                // Set the pending suggestions
                this.pendingSuggestions = CompletableFuture.completedFuture(builder.build());

                // Cancel the default suggestion logic for . commands
                if (text.startsWith(CommandBridge.SECONDARY_PREFIX)) {
                    ci.cancel();
                }
            }
        }
    }
}
