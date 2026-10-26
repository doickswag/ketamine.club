package we.ketamine.club.mixin.render.gui;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.manager.CommandManager;

@Mixin({CommandSuggestions.class})
public abstract class MixinCommandSuggestions {
   @Shadow
   private SuggestionsList suggestions;
   @Shadow
   @Nullable
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   @Nullable
   private ParseResults<ClientSuggestionProvider> currentParse;
   @Shadow
   boolean keepSuggestions;
   @Shadow
   @Final
   EditBox input;

   public MixinCommandSuggestions() {
   }

   @Shadow
   protected abstract void updateUsageInfo(ParseResults<ClientSuggestionProvider> var1, Suggestions var2);

   @Inject(
      method = {"updateCommandInfo"},
      at = {@At("RETURN")}
   )
   public void updateCommandInfo(CallbackInfo ci, @Local StringReader stringReader) {
      String prefix = KetamineClub.commandManager.getCommandPrefix();
      if (stringReader.getString().startsWith(prefix) && this.input.getCursorPosition() >= prefix.length()) {
         stringReader.setCursor(prefix.length());
         CommandDispatcher<CommandManager> dispatcher = KetamineClub.commandManager.getDispatcher();
         ParseResults<CommandManager> parse = dispatcher.parse(stringReader, KetamineClub.commandManager);
         ParseResults<ClientSuggestionProvider> clientParse = (ParseResults<ClientSuggestionProvider>)(ParseResults<?>)parse;
         this.currentParse = clientParse;
         if (this.suggestions == null || !this.keepSuggestions) {
            CompletableFuture<Suggestions> completion = dispatcher.getCompletionSuggestions(parse, this.input.getCursorPosition());
            this.pendingSuggestions = completion;
            completion.thenAccept(completedSuggestions -> {
               if (this.pendingSuggestions == completion && this.currentParse == clientParse) {
                  this.updateUsageInfo(clientParse, completedSuggestions);
               }
            });
         }
      }
   }
}
