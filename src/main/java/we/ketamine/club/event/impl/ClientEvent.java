package we.ketamine.club.event.impl;

import we.ketamine.club.event.Event;
import we.ketamine.club.features.Feature;
import we.ketamine.club.features.settings.Setting;

public class ClientEvent extends Event {
   private final ClientEvent.Type type;
   private final Feature feature;
   private Setting<?> setting;

   public ClientEvent(ClientEvent.Type type, Feature feature) {
      this.type = type;
      this.feature = feature;
   }

   public ClientEvent(Setting<?> setting) {
      this(ClientEvent.Type.SETTING_UPDATE, setting.getFeature());
      this.setting = setting;
   }

   public ClientEvent.Type getType() {
      return this.type;
   }

   public Feature getFeature() {
      return this.feature;
   }

   public Setting<?> getSetting() {
      return this.setting;
   }

   public static enum Type {
      TOGGLE_MODULE,
      SETTING_UPDATE;

      private Type() {
      }
   }
}
