package we.ketamine.club.features.settings;

import java.awt.Color;
import java.util.Objects;
import java.util.function.Predicate;
import org.joml.Vector2f;
import we.ketamine.club.event.impl.ClientEvent;
import we.ketamine.club.features.Feature;
import we.ketamine.club.pingbypass.PingBypassModules;
import we.ketamine.club.util.traits.Util;

public class Setting<T> {
   private final String name;
   private final T defaultValue;
   private volatile T value;
   private volatile T plannedValue;
   private T min;
   private T max;
   private boolean hasRestriction;
   private Predicate<T> visibility;
   private String description;
   private Feature feature;
   private String page = "General";

   public Setting(String name, T defaultValue) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.plannedValue = defaultValue;
      this.description = "";
   }

   public Setting(String name, T defaultValue, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.plannedValue = defaultValue;
      this.description = description;
   }

   public Setting(String name, T defaultValue, T min, T max, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.min = min;
      this.max = max;
      this.plannedValue = defaultValue;
      this.description = description;
      this.hasRestriction = true;
   }

   public Setting(String name, T defaultValue, T min, T max) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.min = min;
      this.max = max;
      this.plannedValue = defaultValue;
      this.description = "";
      this.hasRestriction = true;
   }

   public Setting(String name, T defaultValue, T min, T max, Predicate<T> visibility, String description) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.min = min;
      this.max = max;
      this.plannedValue = defaultValue;
      this.visibility = visibility;
      this.description = description;
      this.hasRestriction = true;
   }

   public Setting(String name, T defaultValue, T min, T max, Predicate<T> visibility) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.min = min;
      this.max = max;
      this.plannedValue = defaultValue;
      this.visibility = visibility;
      this.description = "";
      this.hasRestriction = true;
   }

   public Setting(String name, T defaultValue, Predicate<T> visibility) {
      this.name = name;
      this.defaultValue = defaultValue;
      this.value = defaultValue;
      this.visibility = visibility;
      this.plannedValue = defaultValue;
   }

   public String getName() {
      return this.name;
   }

   public T getValue() {
      return this.value;
   }

   public void setValue(T value) {
      this.setPlannedValue(value);
      if (this.hasRestriction) {
         if (((Number)this.min).floatValue() > ((Number)value).floatValue()) {
            this.setPlannedValue(this.min);
         }

         if (((Number)this.max).floatValue() < ((Number)value).floatValue()) {
            this.setPlannedValue(this.max);
         }
      }

      ClientEvent event = new ClientEvent(this);
      Util.EVENT_BUS.post(event);
      if (!event.isCancelled()) {
         this.value = this.plannedValue;
         PingBypassModules.onSettingChanged(this);
      } else {
         this.plannedValue = this.value;
      }
   }

   public void reset() {
      this.setValue(this.getDefaultValue());
   }

   public T getPlannedValue() {
      return this.plannedValue;
   }

   public void setPlannedValue(T value) {
      this.plannedValue = value;
   }

   public T getMin() {
      return this.min;
   }

   public void setMin(T min) {
      this.min = min;
   }

   public T getMax() {
      return this.max;
   }

   public void setMax(T max) {
      this.max = max;
   }

   public void setValueNoEvent(T value) {
      this.setPlannedValue(value);
      if (this.hasRestriction) {
         if (((Number)this.min).floatValue() > ((Number)value).floatValue()) {
            this.setPlannedValue(this.min);
         }

         if (((Number)this.max).floatValue() < ((Number)value).floatValue()) {
            this.setPlannedValue(this.max);
         }
      }

      this.value = this.plannedValue;
      PingBypassModules.onSettingChanged(this);
   }

   public Feature getFeature() {
      return this.feature;
   }

   public void setFeature(Feature feature) {
      this.feature = feature;
   }

   public int getEnum(String input) {
      for (int i = 0; i < this.value.getClass().getEnumConstants().length; i++) {
         Enum<?> e = (Enum<?>)this.value.getClass().getEnumConstants()[i];
         if (e.name().equalsIgnoreCase(input)) {
            return i;
         }
      }

      return -1;
   }

   public void setEnumValue(String value) {
      for (Enum<?> e : (Enum[])((Enum)this.value).getClass().getEnumConstants()) {
         if (e.name().equalsIgnoreCase(value)) {
            this.value = (T)e;
            this.plannedValue = this.value;
            PingBypassModules.onSettingChanged(this);
         }
      }
   }

   public String currentEnumName() {
      return EnumConverter.getProperName((Enum<?>)this.value);
   }

   public int currentEnum() {
      return EnumConverter.currentEnum((Enum<?>)this.value);
   }

   public void increaseEnum() {
      this.setValue((T)EnumConverter.increaseEnum((Enum<?>)this.value));
   }

   public void increaseEnumNoEvent() {
      this.setValueNoEvent((T)EnumConverter.increaseEnum((Enum<?>)this.value));
   }

   public String getType() {
      if (this.isEnumSetting()) {
         return "Enum";
      } else if (this.isColorSetting()) {
         return "Color";
      } else {
         return this.isVec2fSetting() ? "Pos" : this.getClassName(this.defaultValue);
      }
   }

   public <K> String getClassName(K value) {
      return value.getClass().getSimpleName();
   }

   public String getDescription() {
      return Objects.requireNonNullElse(this.description, "");
   }

   public boolean isNumberSetting() {
      return this.value instanceof Number;
   }

   public boolean isEnumSetting() {
      return this.value instanceof Enum;
   }

   public boolean isStringSetting() {
      return this.value instanceof String;
   }

   public boolean isColorSetting() {
      return this.value instanceof Color;
   }

   public boolean isVec2fSetting() {
      return this.value instanceof Vector2f;
   }

   public T getDefaultValue() {
      return this.defaultValue;
   }

   public String getValueAsString() {
      return this.value.toString();
   }

   public boolean hasRestriction() {
      return this.hasRestriction;
   }

   public Setting<T> setVisibility(Predicate<T> visibility) {
      this.visibility = visibility;
      return this;
   }

   public boolean isVisible() {
      return this.visibility == null || this.visibility.test(this.getValue());
   }

   public String getPage() {
      return this.page;
   }

   public Setting<T> setPage(String page) {
      if (page == null) {
         this.page = "General";
         return this;
      } else {
         String trimmed = page.trim();
         this.page = trimmed.isEmpty() ? "General" : trimmed;
         return this;
      }
   }
}
