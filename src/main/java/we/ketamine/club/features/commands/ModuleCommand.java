package we.ketamine.club.features.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.awt.Color;
import java.util.Locale;
import we.ketamine.club.features.commands.argument.ColorArgumentType;
import we.ketamine.club.features.commands.argument.EnumArgumentType;
import we.ketamine.club.features.commands.argument.NumberArgumentType;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.CommandManager;

public class ModuleCommand extends Command {
   private final Module module;

   public ModuleCommand(Module module) {
      super(module.getName().toLowerCase(Locale.ROOT));
      this.setDescription("Command line configuration implementation for \"" + module.getName() + "\"");
      this.module = module;
   }

   @Override
   public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
      for (Setting<?> setting : this.module.getSettings()) {
         Class<?> type = setting.getDefaultValue().getClass();
         if (Boolean.class.isAssignableFrom(type)) {
            this.registerBooleanArgument(builder, (Setting<Boolean>)setting);
         } else if (Number.class.isAssignableFrom(type)) {
            this.registerNumberArgument(builder, (Setting<Number>)setting);
         } else if (Enum.class.isAssignableFrom(type)) {
            this.registerEnumArgument(builder, (Setting<Enum<?>>)setting);
         } else if (String.class.isAssignableFrom(type)) {
            this.registerStringArgument(builder, (Setting<String>)setting);
         } else if (Color.class.isAssignableFrom(type)) {
            this.registerColorArgument(builder, (Setting<Color>)setting);
         }
      }
   }

   private void registerColorArgument(LiteralArgumentBuilder<CommandManager> builder, Setting<Color> setting) {
      builder.then(
         literal(settingToken(setting))
            .then(
               argument("value", ColorArgumentType.color())
                  .executes(
                     ctx -> {
                        setting.setValue(ColorArgumentType.getColor(ctx, "value"));
                        Color value = setting.getValue();
                        return this.success(
                           "Set %s.%s to RGB(%s, %s, %s)",
                           new Object[]{this.module.getName(), setting.getName(), value.getRed(), value.getGreen(), value.getBlue()}
                        );
                     }
                  )
            )
      );
   }

   private void registerStringArgument(LiteralArgumentBuilder<CommandManager> builder, Setting<String> setting) {
      builder.then(literal(settingToken(setting)).then(argument("value", StringArgumentType.greedyString()).executes(ctx -> {
         String value = StringArgumentType.getString(ctx, "value");
         if (setting == this.module.displayName) {
            this.module.setDisplayName(value);
            return -1;
         } else {
            setting.setValue(value);
            return this.settingChangeReturn(setting);
         }
      })));
   }

   private void registerEnumArgument(LiteralArgumentBuilder<CommandManager> builder, Setting<Enum<?>> setting) {
      Class<Enum<?>> type = (Class<Enum<?>>)setting.getDefaultValue().getClass();
      builder.then(literal(settingToken(setting)).then(argument("value", EnumArgumentType._enum(type)).executes(ctx -> {
         setting.setValue(EnumArgumentType.getEnum(ctx, "value"));
         return this.settingChangeReturn(setting);
      })));
   }

   private <T extends Number> void registerNumberArgument(LiteralArgumentBuilder<CommandManager> builder, Setting<T> setting) {
      Class<T> type = (Class<T>)setting.getDefaultValue().getClass();
      builder.then(
         literal(settingToken(setting))
            .then(argument("value", NumberArgumentType.number(type, NumberArgumentType.minMax(setting.getMin(), setting.getMax()))).executes(ctx -> {
               setting.setValue(NumberArgumentType.get(type, ctx, "value"));
               return this.settingChangeReturn(setting);
            }))
      );
   }

   private void registerBooleanArgument(LiteralArgumentBuilder<CommandManager> builder, Setting<Boolean> setting) {
      builder.then(literal(settingToken(setting)).then(argument("value", BoolArgumentType.bool()).executes(ctx -> {
         boolean value = BoolArgumentType.getBool(ctx, "value");
         if (setting == this.module.enabled) {
            if (value) {
               this.module.enable();
            } else {
               this.module.disable();
            }
         } else {
            setting.setValue(value);
         }

         return this.settingChangeReturn(setting);
      })));
   }

   private int settingChangeReturn(Setting<?> setting) {
      return this.success("Set %s.%s to %s", new Object[]{this.module.getName(), setting.getName(), setting.getValue()});
   }

   private static String settingToken(Setting<?> setting) {
      return setting.getName().toLowerCase(Locale.ROOT).replace(' ', '-');
   }

   @Override
   public boolean isShown() {
      return false;
   }
}
