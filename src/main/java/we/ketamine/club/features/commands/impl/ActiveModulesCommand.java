package we.ketamine.club.features.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.StringJoiner;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.hud.ActiveModulesHudModule;
import we.ketamine.club.manager.CommandManager;

public class ActiveModulesCommand extends Command {
   public ActiveModulesCommand() {
      super("activemodules", "am");
      this.setDescription("Manages the ActiveModules HUD list");
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)builder.then(
                  literal("add")
                     .then(
                        argument("module", StringArgumentType.word())
                           .suggests(addSuggestions())
                           .executes(
                              ctx -> {
                                 String name = StringArgumentType.getString(ctx, "module");
                                 Module module = KetamineClub.moduleManager.getModuleByName(name);
                                 if (module == null) {
                                    return this.fail("No module named {green} %s", new Object[]{name});
                                 } else {
                                    ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
                                    if (h == null) {
                                       return this.fail("ActiveModules HUD element not found", new Object[0]);
                                    } else {
                                       return !h.add(name)
                                          ? this.success("{green} %s {reset} is already in the list", new Object[]{module.getDisplayName()})
                                          : this.success("Added {green} %s {reset} to ActiveModules", new Object[]{module.getDisplayName()});
                                    }
                                 }
                              }
                           )
                     )
               ))
               .then(
                  literal("remove")
                     .then(
                        argument("module", StringArgumentType.word())
                           .suggests(removeSuggestions())
                           .executes(
                              ctx -> {
                                 String name = StringArgumentType.getString(ctx, "module");
                                 ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
                                 if (h == null) {
                                    return this.fail("ActiveModules HUD element not found", new Object[0]);
                                 } else {
                                    return !h.remove(name)
                                       ? this.success("{green} %s {reset} is not in the list", new Object[]{name})
                                       : this.success("Removed {green} %s {reset} from ActiveModules", new Object[]{name});
                                 }
                              }
                           )
                     )
               ))
            .then(literal("clear").executes(ctx -> {
               ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
               if (h == null) {
                  return this.fail("ActiveModules HUD element not found", new Object[0]);
               } else {
                  h.clear();
                  return this.success("Cleared ActiveModules list", new Object[0]);
               }
            })))
         .then(literal("list").executes(ctx -> {
            ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
            if (h == null) {
               return this.fail("ActiveModules HUD element not found", new Object[0]);
            } else {
               List<String> entries = h.getEntries();
               if (entries.isEmpty()) {
                  return this.success("ActiveModules list is empty", new Object[0]);
               } else {
                  StringJoiner joiner = new StringJoiner(", ");
                  entries.forEach(joiner::add);
                  return this.success("ActiveModules (%s): %s", new Object[]{entries.size(), joiner});
               }
            }
         }));
   }

   private static SuggestionProvider<CommandManager> addSuggestions() {
      return (ctx, builder) -> {
         String input = builder.getRemainingLowerCase();
         ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
         List<String> entries = h != null ? h.getEntries() : List.of();

         for (Module module : KetamineClub.moduleManager.getModules()) {
            String name = module.getName();
            if (!entries.stream().anyMatch(e -> e.equalsIgnoreCase(name))
               && (name.toLowerCase().contains(input) || module.getDisplayName().toLowerCase().contains(input))) {
               builder.suggest(name);
            }
         }

         return builder.buildFuture();
      };
   }

   private static SuggestionProvider<CommandManager> removeSuggestions() {
      return (ctx, builder) -> {
         String input = builder.getRemainingLowerCase();
         ActiveModulesHudModule h = ActiveModulesHudModule.getInstance();
         if (h == null) {
            return builder.buildFuture();
         } else {
            for (String entry : h.getEntries()) {
               if (entry.toLowerCase().contains(input)) {
                  builder.suggest(entry);
               }
            }

            return builder.buildFuture();
         }
      };
   }
}
