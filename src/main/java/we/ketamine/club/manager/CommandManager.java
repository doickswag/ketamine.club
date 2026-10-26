package we.ketamine.club.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.commands.Command;
import we.ketamine.club.features.commands.impl.ActiveModulesCommand;
import we.ketamine.club.features.commands.impl.BindCommand;
import we.ketamine.club.features.commands.impl.EnemyCommand;
import we.ketamine.club.features.commands.impl.FakePlayerCommand;
import we.ketamine.club.features.commands.impl.FriendCommand;
import we.ketamine.club.features.commands.impl.FunnyCommand;
import we.ketamine.club.features.commands.impl.HelpCommand;
import we.ketamine.club.features.commands.impl.LoadKitCommand;
import we.ketamine.club.features.commands.impl.PrefixCommand;
import we.ketamine.club.features.commands.impl.SaveKitCommand;
import we.ketamine.club.features.commands.impl.SyncAllCommand;
import we.ketamine.club.features.commands.impl.ToggleCommand;
import we.ketamine.club.util.traits.Jsonable;

public class CommandManager extends Feature implements Jsonable {
   private static final Logger LOGGER = LogManager.getLogger("Commands");
   private static final String DEFAULT_COMMAND_PREFIX = ".";
   private final CommandDispatcher<CommandManager> dispatcher = new CommandDispatcher();
   private final Map<String, Command> commandAliasMap = new LinkedHashMap<>();
   private final List<Command> commandList = new LinkedList<>();
   private String commandPrefix = ".";
   private boolean funnyVisible = false;

   public CommandManager() {
      super("Commands");
   }

   public void init() {
      this.register(new BindCommand());
      this.register(new FriendCommand());
      this.register(new EnemyCommand());
      this.register(new HelpCommand());
      this.register(new PrefixCommand());
      this.register(new ToggleCommand());
      this.register(new ActiveModulesCommand());
      this.register(new FunnyCommand());
      this.register(new SaveKitCommand());
      this.register(new LoadKitCommand());
      this.register(new FakePlayerCommand());
      this.register(new SyncAllCommand());
      LOGGER.info("Registered {} commands", this.commandList.size());
      KetamineClub.configManager.addConfig(this);
   }

   public void onChatSent(String message) {
      try {
         int result = this.dispatcher.execute(message.substring(this.commandPrefix.length()).trim(), this);
         if (result == 1) {
            Command.sendMessage("{green} Command executed successfully");
         } else if (result == 0) {
            Command.sendMessage("{red} Failed to execute command");
         }
      } catch (CommandSyntaxException var3) {
         LOGGER.debug("Command syntax error", var3);
         Command.sendMessage("{red} %s", var3.getMessage());
      } catch (RuntimeException var4) {
         LOGGER.error("Command execution failed", var4);
         Command.sendMessage("{red} Command execution failed; check the log for details");
      }
   }

   public void register(Command command) {
      this.commandList.add(command);

      for (String alias : command.getAliases()) {
         this.commandAliasMap.put(alias, command);
         LiteralArgumentBuilder<CommandManager> builder = Command.literal(alias);
         command.createArgumentBuilder(builder);
         this.dispatcher.register(builder);
      }
   }

   public boolean setCommandPrefix(String commandPrefix) {
      if (commandPrefix != null && !commandPrefix.isBlank() && !commandPrefix.startsWith("/") && !commandPrefix.codePoints().anyMatch(Character::isWhitespace)) {
         this.commandPrefix = commandPrefix;
         return true;
      } else {
         return false;
      }
   }

   public String getCommandPrefix() {
      return this.commandPrefix;
   }

   public boolean isFunnyVisible() {
      return this.funnyVisible;
   }

   public void setFunnyVisible(boolean funnyVisible) {
      this.funnyVisible = funnyVisible;
   }

   public Command getCommand(String alias) {
      return this.commandAliasMap.get(alias);
   }

   public Set<String> getCommandAliases() {
      return Collections.unmodifiableSet(this.commandAliasMap.keySet());
   }

   public List<Command> getCommands() {
      return Collections.unmodifiableList(this.commandList);
   }

   public CommandDispatcher<CommandManager> getDispatcher() {
      return this.dispatcher;
   }

   @Override
   public JsonElement toJson() {
      JsonObject object = new JsonObject();
      object.addProperty("prefix", this.commandPrefix);
      object.addProperty("funnyVisible", this.funnyVisible);
      return object;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (element != null && element.isJsonObject()) {
         JsonObject object = element.getAsJsonObject();
         if (object.has("prefix")) {
            try {
               if (!this.setCommandPrefix(object.get("prefix").getAsString())) {
                  this.setCommandPrefix(".");
               }
            } catch (RuntimeException var4) {
               this.setCommandPrefix(".");
            }
         }

         if (object.has("funnyVisible")) {
            this.setFunnyVisible(object.get("funnyVisible").getAsBoolean());
         }
      }
   }

   @Override
   public String getFileName() {
      return "commands.json";
   }
}
