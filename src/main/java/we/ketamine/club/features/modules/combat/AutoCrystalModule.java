package we.ketamine.club.features.modules.combat;

import it.unimi.dsi.fastutil.ints.IntListIterator;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import we.ketamine.club.KetamineClub;
import we.ketamine.club.event.impl.entity.player.PreTickEvent;
import we.ketamine.club.event.impl.input.KeyInputEvent;
import we.ketamine.club.event.impl.network.PacketEvent;
import we.ketamine.club.event.impl.render.Render2DEvent;
import we.ketamine.club.event.impl.render.Render3DEvent;
import we.ketamine.club.event.system.Subscribe;
import we.ketamine.club.features.modules.Module;
import we.ketamine.club.features.modules.client.TargetsModule;
import we.ketamine.club.features.modules.world.SpeedMineController;
import we.ketamine.club.features.settings.Bind;
import we.ketamine.club.features.settings.Setting;
import we.ketamine.club.manager.PlacementManager;
import we.ketamine.club.manager.RotationRequest;
import we.ketamine.club.manager.SwapManager;
import we.ketamine.club.util.EnchantmentUtil;
import we.ketamine.club.util.GrimUtil;
import we.ketamine.club.util.MathUtil;
import we.ketamine.club.util.inventory.InventoryUtil;
import we.ketamine.club.util.inventory.Result;
import we.ketamine.club.util.inventory.ResultType;
import we.ketamine.club.util.models.Timer;
import we.ketamine.club.util.player.ChatUtil;
import we.ketamine.club.util.render.MatrixCapture;
import we.ketamine.club.util.render.RenderUtil;
import we.ketamine.club.util.render.font.Fonts;

public class AutoCrystalModule extends Module {
   private final Setting<Boolean> place = this.bool("Place", true).setPage("General");
   private final Setting<Boolean> offhandPlace = this.bool("OffhandPlace", true).setPage("General");
   private final Setting<Integer> placeDelay = this.num("PlaceDelay", 0, 0, 2000).setPage("General");
   private final Setting<Boolean> doBreak = this.bool("Break", true).setPage("General");
   private final Setting<Integer> breakDelay = this.num("BreakDelay", 0, 0, 2000).setPage("General");
   private final Setting<Boolean> stanceAbuse = this.bool("StanceAbuse", true).setVisibility(v -> this.doBreak.getValue()).setPage("General");
   private final Setting<Boolean> rotate = this.bool("Rotate", true).setPage("General");
   private final Setting<Double> minDamage = this.num("MinDamage", 4.0, 0.0, 36.0).setPage("General");
   private final Setting<Double> maxSelfDamage = this.num("MaxSelfDamage", 4.0, 0.0, 36.0).setPage("General");
   private final Setting<AutoCrystalModule.FacePlaceMode> facePlace = this.mode("FacePlace", AutoCrystalModule.FacePlaceMode.NORMAL).setPage("General");
   private final Setting<Double> facePlaceThreshold = this.num("FacePlaceThreshold", 3.0, 0.0, 36.0).setPage("General");
   private final Setting<Bind> facePlaceBind = this.key("FacePlaceBind", Bind.none()).setPage("General");
   private final Setting<Boolean> antiSurround = this.bool("AntiSurround", true).setPage("General");
   private final Setting<Integer> antiSurroundCompletion = this.num("AntiSurroundCompletion", 70, 0, 100).setPage("General");
   private final Setting<Boolean> instantRotate = this.bool("InstantRotate", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> multiReact = this.bool("MultiReact", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> sharedTarget = this.bool("SharedTarget", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> lethalScoring = this.bool("LethalScoring", false).setVisibility(value -> false).setPage("Synergy");
   private final Setting<Boolean> antiChinese = this.bool("AntiChinese", false).setPage("AntiChinese");
   private final Setting<Double> antiChineseRange = this.num("Range", 5.0, 1.0, 5.14).setPage("AntiChinese");
   private final Setting<Double> antiChineseEnemyRange = this.num("EnemyRange", 8.0, 1.0, 30.0).setPage("AntiChinese");
   private final Setting<Boolean> basePlace = this.bool("BasePlace", true).setPage("BasePlace");
   private final Setting<Double> baseDiffRatio = this.num("BasePlaceDiffRatio", 1.4, 1.0, 5.0).setPage("BasePlace");
   private final Setting<Boolean> basePauseEat = this.bool("BasePlacePauseEat", true).setPage("BasePlace");
   private final Setting<Double> baseDelay = this.register(new Setting<>("BasePlaceDelay", 0.25, 0.0, 5.0, "Seconds between obsidian or sponge placements."))
      .setPage("BasePlace");
   private final Setting<BasePlaceController.ReplaceDelayMode> baseReplaceDelay = this.mode(
         "BasePlaceReplaceDelay", BasePlaceController.ReplaceDelayMode.PlaceDelay
      )
      .setPage("BasePlace");
   private final Setting<Boolean> baseSponge = this.bool("BasePlaceSponge", true).setPage("BasePlace");
   private final Setting<Boolean> debug = this.bool("Debug", false).setPage("Extra");
   private final Setting<Boolean> packetLog = this.bool("PacketLog", false).setPage("Extra");
   private final Setting<Boolean> renderBox = this.bool("RenderBox", true).setPage("Render");
   private final Setting<Color> fillColor = this.color("FillColor", 130, 80, 255, 45).setPage("Render");
   private final Setting<Color> outlineColor = this.color("OutlineColor", 130, 80, 255, 255).setPage("Render");
   private final Setting<Float> outlineWidth = this.num("OutlineWidth", 1.5F, 0.5F, 5.0F).setPage("Render");
   private final Setting<Integer> renderTime = this.num("RenderTime", 10, 1, 40).setPage("Render");
   private final Setting<Boolean> damageText = this.bool("DamageText", true).setPage("Render");
   private final Setting<AutoCrystalModule.DamageTextFont> damageTextFont = this.mode("DamageTextFont", AutoCrystalModule.DamageTextFont.VANILLA)
      .setVisibility(v -> this.damageText.getValue())
      .setPage("Render");
   private final Setting<Color> damageTextColor = this.color("DamageTextColor", 255, 255, 255, 255).setPage("Render");
   private final Setting<Float> damageTextScale = this.num("DamageTextScale", 1.0F, 0.3F, 3.0F).setPage("Render");
   private final Setting<Boolean> damageTextShadow = this.bool("DamageTextShadow", true).setPage("Render");
   private static final int MAX_RENDER_ENTRIES = 32;
   private final ArrayList<AutoCrystalModule.RenderEntry> renderEntries = new ArrayList<>();
   private final CrystalRenderTracker crystalRenderTracker = new CrystalRenderTracker();
   private Level renderLevel;
   private Player renderPlayer;
   private static final double PLACE_RANGE = 6.0;
   private static final double BREAK_RANGE = 3.0;
   private static final int BALANCE = 4;
   private static final double HEALTH_BALANCE = 0.2;
   private static final double ARMOR_BALANCE = 0.2;
   private static final boolean NO_SELF_POP = true;
   private static final long REACTIVE_REPLACE_WINDOW_NS = 500000000L;
   private static final long INSTANT_ATTACK_SUPPRESS_NS = 500000000L;
   private static final long MIN_REACTIVE_CONFIRM_MS = 100L;
   private static final long MAX_REACTIVE_CONFIRM_MS = 500L;
   private static final long REACTIVE_CONFIRM_MARGIN_MS = 50L;
   private static final long CRYSTAL_TRACK_MS = 1750L;
   private static final long SLOW_PLACE_INTERVAL_MS = 500L;
   private static final String PLACE_ROTATION_ID = "AutoCrystal_place";
   private static final String BREAK_ROTATION_ID = "AutoCrystal_break";
   private static final String INSTANT_BREAK_ROTATION_ID = "AutoCrystal_instant";
   private static final String MINE_CHAIN_ROTATION_ID = "AutoCrystal_mine_chain";
   private static final int SWAP_PRIORITY = 60;
   private final Timer placeTimer = new Timer();
   private final Timer breakTimer = new Timer();
   private final Timer slowPlaceTimer = new Timer();
   private final BasePlaceController basePlacer = new BasePlaceController(this);
   private final Long2LongOpenHashMap crystalPlaces = new Long2LongOpenHashMap();
   private float lastBestDamage = 0.0F;
   private SwapManager.SwapHandle pendingSwapHandle = null;
   private boolean placingCrystal;
   private final HashMap<String, int[]> diagSendCounts = new HashMap<>();
   private long diagWindowStart = 0L;
   private int diagWindowSends = 0;
   private int diagPeakWindow = 0;
   private long diagLifetimeSends = 0L;
   private long diagLastTickSends = 0L;
   private int diagPeakTickSends = 0;
   private int diagPlaceAttempt;
   private int diagPlaceSent;
   private int diagBreakSent;
   private int diagCrystalSpawn;
   private int diagInstaBreak;
   private int diagReactivePlace;
   private int diagReactiveMiss;
   private double lastCalcMs = 0.0;
   private int lastReactorPlaceTick = -1;
   private volatile BlockPos lastPlaceAir = null;
   private volatile AutoCrystalModule.PlaceTarget lastPlaceTarget = null;
   private volatile long lastPlaceSentNanos = 0L;
   private final Long2LongOpenHashMap pendingReactivePlacements = new Long2LongOpenHashMap();
   private Level reactivePipelineLevel;
   private final AutoCrystalModule.ExposureContext exposureCtx = new AutoCrystalModule.ExposureContext();
   private final ArrayList<AutoCrystalModule.PlaceCandidate> candidates = new ArrayList<>();
   private final Set<Integer> deadIds = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<Integer, Long> instantAttacks = new ConcurrentHashMap<>();
   private final Set<BlockPos> seeThrough = new HashSet<>();
   private int cachedChunkX = Integer.MIN_VALUE;
   private int cachedChunkZ = Integer.MIN_VALUE;
   private ChunkAccess cachedChunk;
   private Level cachedChunkLevel;
   private final PlacementManager.PlacementListener placementListener = this::onBlockUpdate;
   private static final int[][] FACE_OFFSETS = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
   private final ArrayDeque<Long> explodeTimes = new ArrayDeque<>();
   private static final float EXPLODABLE_RESISTANCE = 600.0F;
   private static final double ANTI_CHINESE_RADIUS = 3.0;

   public AutoCrystalModule() {
      super("AutoCrystal", "Automatically places and breaks end crystals.", Module.Category.COMBAT);
      this.damageTextColor.setVisibility(v -> this.damageText.getValue());
      this.damageTextScale.setVisibility(v -> this.damageText.getValue());
      this.damageTextShadow.setVisibility(v -> this.damageText.getValue());
      this.placeDelay.setVisibility(v -> this.place.getValue());
      this.facePlaceThreshold.setVisibility(v -> this.facePlace.getValue() == AutoCrystalModule.FacePlaceMode.SLOW);
      this.breakDelay.setVisibility(v -> this.doBreak.getValue());
      this.baseDiffRatio.setVisibility(v -> this.basePlace.getValue());
      this.basePauseEat.setVisibility(v -> this.basePlace.getValue());
      this.baseDelay.setVisibility(v -> this.basePlace.getValue());
      this.baseReplaceDelay.setVisibility(v -> this.basePlace.getValue() && this.baseSponge.getValue());
      this.baseSponge.setVisibility(v -> this.basePlace.getValue());
      this.antiChineseRange.setVisibility(v -> this.antiChinese.getValue());
      this.antiChineseEnemyRange.setVisibility(v -> this.antiChinese.getValue());
   }

   @Override
   public void onEnable() {
      this.basePlacer.reset();
      this.crystalRenderTracker.reset();
      this.renderLevel = mc.level;
      this.renderPlayer = mc.player;
      this.cachedChunk = null;
      this.cachedChunkX = this.cachedChunkZ = Integer.MIN_VALUE;
      this.crystalPlaces.clear();
      this.deadIds.clear();
      this.instantAttacks.clear();
      this.placeTimer.reset();
      this.breakTimer.reset();
      this.slowPlaceTimer.reset();
      this.lastBestDamage = 0.0F;
      this.lastCalcMs = 0.0;
      this.lastReactorPlaceTick = -1;
      this.pendingSwapHandle = null;
      this.lastPlaceAir = null;
      this.lastPlaceTarget = null;
      this.lastPlaceSentNanos = 0L;
      this.resetReactivePipeline();
      this.reactivePipelineLevel = mc.level;
      this.resetDiag();
      this.renderEntries.clear();
      KetamineClub.placementManager.addListener(this.placementListener);
   }

   @Override
   public void onDisable() {
      this.basePlacer.reset();
      this.crystalRenderTracker.reset();
      this.renderLevel = null;
      this.renderPlayer = null;
      this.clearCrystalRotations();
      KetamineClub.placementManager.removeListener(this.placementListener);
      if (this.pendingSwapHandle != null) {
         KetamineClub.swapManager.release(this.pendingSwapHandle);
         this.pendingSwapHandle = null;
      }

      this.crystalPlaces.clear();
      this.deadIds.clear();
      this.instantAttacks.clear();
      this.lastBestDamage = 0.0F;
      this.lastPlaceAir = null;
      this.lastPlaceTarget = null;
      this.lastPlaceSentNanos = 0L;
      this.resetReactivePipeline();
      this.reactivePipelineLevel = null;
      this.resetDiag();
      this.renderEntries.clear();
   }

   private static long trackingTimeMs() {
      return System.nanoTime() / 1000000L;
   }

   private void ensureRenderSession() {
      if (this.renderLevel != mc.level || this.renderPlayer != mc.player) {
         this.crystalRenderTracker.reset();
         this.renderEntries.clear();
         this.cachedChunk = null;
         this.cachedChunkX = this.cachedChunkZ = Integer.MIN_VALUE;
         this.renderLevel = mc.level;
         this.renderPlayer = mc.player;
      }
   }

   public void trackCrystalSpawn(EndCrystal crystal) {
      if (!nullCheck() && !this.isDisabled()) {
         this.ensureRenderSession();
         this.crystalRenderTracker.spawned(crystal.blockPosition(), crystal.getId(), crystal.getUUID(), trackingTimeMs());
      }
   }

   @Subscribe
   private void onCrystalRemovalApplied(PacketEvent.ReceivePost event) {
      if (!nullCheck()) {
         this.ensureRenderSession();
         this.confirmCrystalRemovals(event.getPacket());
      }
   }

   private void confirmCrystalRemovals(Packet<?> packet) {
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> child : bundle.subPackets()) {
            this.confirmCrystalRemovals(child);
         }
      } else if (packet instanceof ClientboundRemoveEntitiesPacket removed) {
         IntListIterator var7 = removed.getEntityIds().iterator();

         while (var7.hasNext()) {
            int id = (Integer)var7.next();
            if (mc.level.getEntity(id) == null) {
               CrystalRenderTracker.Render removedCrystal = this.crystalRenderTracker.removed(id);
               if (removedCrystal != null) {
                  this.markRender(removedCrystal.base(), removedCrystal.damage());
               }
            }
         }
      }
   }

   private void markRender(BlockPos basePos, float damage) {
      if (this.renderBox.getValue() || this.damageText.getValue()) {
         long now = System.currentTimeMillis();
         this.pruneRenderEntries(now);
         this.renderEntries.removeIf(entry -> entry.pos().equals(basePos));
         this.renderEntries.add(new AutoCrystalModule.RenderEntry(basePos.immutable(), damage, now));
         if (this.renderEntries.size() > 32) {
            this.renderEntries.remove(0);
         }
      }
   }

   private long renderLifeMs() {
      return this.renderTime.getValue().intValue() * 50L;
   }

   private void pruneRenderEntries(long now) {
      long life = this.renderLifeMs();
      this.renderEntries.removeIf(entry -> now - entry.startMs() > life);
   }

   private float renderFade(AutoCrystalModule.RenderEntry entry, long now) {
      return 1.0F - Math.clamp((float)(now - entry.startMs()) / (float)Math.max(1L, this.renderLifeMs()), 0.0F, 1.0F);
   }

   public List<AutoCrystalModule.PingBypassRenderEntry> pingBypassRenderEntries(long now) {
      if (this.isToggled() && this.renderLevel == mc.level && this.renderPlayer == mc.player) {
         long life = Math.max(1L, this.renderLifeMs());
         List<AutoCrystalModule.PingBypassRenderEntry> result = new ArrayList<>(this.renderEntries.size());

         for (AutoCrystalModule.RenderEntry entry : this.renderEntries) {
            long remaining = life - (now - entry.startMs());
            if (remaining > 0L) {
               result.add(new AutoCrystalModule.PingBypassRenderEntry(entry.pos().immutable(), entry.damage(), remaining));
            }
         }

         return result;
      } else {
         return List.of();
      }
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      this.ensureRenderSession();
      if (!nullCheck() && this.damageText.getValue()) {
         long now = System.currentTimeMillis();
         this.pruneRenderEntries(now);
         boolean customFont = this.damageTextFont.getValue() == AutoCrystalModule.DamageTextFont.CLICKGUI;
         int textHeight = customFont ? Fonts.lineHeight() : 9;
         GuiGraphicsExtractor graphics = event.getContext();

         for (AutoCrystalModule.RenderEntry entry : this.renderEntries) {
            if (!(entry.damage() <= 0.0F)) {
               float[] screen = MatrixCapture.worldToScreen(entry.pos().getX() + 0.5, entry.pos().getY() + 0.5, entry.pos().getZ() + 0.5);
               if (screen != null) {
                  String text = String.format("%.1f", entry.damage());
                  int textWidth = customFont ? Fonts.width(text) : mc.font.width(text);
                  float x = -textWidth / 2.0F;
                  float y = -textHeight / 2.0F;
                  Color textColor = withAlpha(this.damageTextColor.getValue(), this.renderFade(entry, now));
                  graphics.pose().pushMatrix();
                  graphics.pose().translate(screen[0], screen[1]);
                  graphics.pose().scale(this.damageTextScale.getValue(), this.damageTextScale.getValue());
                  if (customFont) {
                     if (this.damageTextShadow.getValue()) {
                        Fonts.drawString(graphics, text, x + 0.7F, y + 0.7F, new Color(0, 0, 0, textColor.getAlpha()).getRGB());
                     }

                     Fonts.drawString(graphics, text, x, y, textColor.getRGB());
                  } else {
                     graphics.text(mc.font, text, (int)x, (int)y, textColor.getRGB(), this.damageTextShadow.getValue());
                  }

                  graphics.pose().popMatrix();
               }
            }
         }
      }
   }

   @Override
   public void onRender3D(Render3DEvent event) {
      if (!nullCheck()) {
         this.ensureRenderSession();
         long now = System.currentTimeMillis();
         this.pruneRenderEntries(now);
         if (this.renderBox.getValue()) {
            for (AutoCrystalModule.RenderEntry entry : this.renderEntries) {
               float fade = this.renderFade(entry, now);
               AABB box = new AABB(entry.pos());
               RenderUtil.drawBoxFilled(event.getMatrix(), box, withAlpha(this.fillColor.getValue(), fade));
               RenderUtil.drawBox(event.getMatrix(), box, withAlpha(this.outlineColor.getValue(), fade), this.outlineWidth.getValue());
            }
         }
      }
   }

   private static Color withAlpha(Color color, float multiplier) {
      int alpha = Math.clamp((long)Math.round(color.getAlpha() * multiplier), 0, 255);
      return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (mc.level != null) {
         if (event.getPacket() instanceof ClientboundEntityEventPacket pkt) {
            if (pkt.getEventId() == 3) {
               Entity e = pkt.getEntity(mc.level);
               if (e != null) {
                  this.deadIds.add(e.getId());
               }
            }
         }
      }
   }

   @Subscribe
   private void onPacketSendDiag(PacketEvent.Send event) {
      if (this.packetLog.getValue()) {
         this.diagSendCounts.computeIfAbsent(event.getPacket().getClass().getSimpleName(), k -> new int[1])[0]++;
         this.diagWindowSends++;
         this.diagLifetimeSends++;
         long now = System.currentTimeMillis();
         if (this.diagWindowStart == 0L) {
            this.diagWindowStart = now;
         }

         if (now - this.diagWindowStart >= 1000L) {
            this.flushDiag(now);
         }
      }
   }

   @Subscribe(
      priority = 100
   )
   private void onDiagTickBoundary(PreTickEvent event) {
      if (this.packetLog.getValue()) {
         int tickDelta = (int)(this.diagLifetimeSends - this.diagLastTickSends);
         this.diagLastTickSends = this.diagLifetimeSends;
         if (tickDelta > this.diagPeakTickSends) {
            this.diagPeakTickSends = tickDelta;
         }
      }
   }

   private void flushDiag(long now) {
      if (this.diagWindowSends > this.diagPeakWindow) {
         this.diagPeakWindow = this.diagWindowSends;
      }

      StringBuilder breakdown = new StringBuilder();
      this.diagSendCounts
         .entrySet()
         .stream()
         .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
         .forEach(e -> breakdown.append(e.getKey()).append('=').append(e.getValue()[0]).append(' '));
      KetamineClub.LOGGER
         .info(
            "[ACDiag] {}ms sends={} peakTick={} peakSec={} | place att/sent={}/{} break={} spawn={} insta={} reactive={} miss={} pending={} | {}",
            new Object[]{
               now - this.diagWindowStart,
               this.diagWindowSends,
               this.diagPeakTickSends,
               this.diagPeakWindow,
               this.diagPlaceAttempt,
               this.diagPlaceSent,
               this.diagBreakSent,
               this.diagCrystalSpawn,
               this.diagInstaBreak,
               this.diagReactivePlace,
               this.diagReactiveMiss,
               this.pendingReactivePlacements.size(),
               breakdown.toString().trim()
            }
         );
      this.diagSendCounts.clear();
      this.diagWindowSends = 0;
      this.diagPeakTickSends = 0;
      this.diagPlaceAttempt = this.diagPlaceSent = this.diagBreakSent = this.diagCrystalSpawn = this.diagInstaBreak = this.diagReactivePlace = 0;
      this.diagReactiveMiss = 0;
      this.diagWindowStart = now;
   }

   private void resetDiag() {
      this.diagSendCounts.clear();
      this.diagWindowStart = 0L;
      this.diagWindowSends = 0;
      this.diagPeakWindow = 0;
      this.diagLifetimeSends = 0L;
      this.diagLastTickSends = 0L;
      this.diagPeakTickSends = 0;
      this.diagPlaceAttempt = this.diagPlaceSent = this.diagBreakSent = this.diagCrystalSpawn = this.diagInstaBreak = this.diagReactivePlace = 0;
      this.diagReactiveMiss = 0;
   }

   public void onCrystalAdded(EndCrystal crystal) {
      this.diagCrystalSpawn++;
      BlockPos crystalPos = crystal.blockPosition();
      this.pendingReactivePlacements.remove(crystalPos.asLong());
      AutoMineModule autoMine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
      if (autoMine == null || !autoMine.onMineChainCrystalAdded(crystal)) {
         if (!this.isChainCrystalProtected(crystal)) {
            if (this.doBreak.getValue() && this.breakDelay.getValue() == 0) {
               if (!nullCheck() && !mc.player.isDeadOrDying()) {
                  OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
                  if (offhand == null || !offhand.shouldDeferForEat()) {
                     if (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                        AutoSwordModule sword = KetamineClub.moduleManager.getModuleByClass(AutoSwordModule.class);
                        if (sword == null || !sword.isEnabled() || !sword.isMaceAttackReady()) {
                           AABB bb = crystal.getBoundingBox();
                           Vec3 reachEye = this.bestReachEye(bb);
                           if (this.canReachBox(bb)) {
                              float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                              if (this.selfDamageOk(crystal.position(), null, this.maxSelfDamage.getValue(), playerHealth)) {
                                 Vec3 hit = this.getClosestPointToEye(reachEye, bb);
                                 float[] angles = MathUtil.calcAngle(reachEye, hit);
                                 if (this.canBreakCrystal(crystal, angles[0], angles[1])) {
                                    boolean preRotated = !this.rotate.getValue()
                                       || this.canBreakCrystal(
                                          crystal, KetamineClub.rotationManager.getServerYaw(), KetamineClub.rotationManager.getServerPitch()
                                       );
                                    if (preRotated
                                       || this.submitCrystalRotation("AutoCrystal_instant", 61, angles[0], angles[1], false)
                                          && this.canBreakCrystal(
                                             crystal, KetamineClub.rotationManager.getServerYaw(), KetamineClub.rotationManager.getServerPitch()
                                          )) {
                                       this.attackSpawnCrystal(crystal, crystalPos);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean attackSpawnCrystal(EndCrystal crystal, BlockPos crystalPos) {
      if (!nullCheck() && crystal.isAlive()) {
         this.diagInstaBreak++;
         this.instantAttacks.put(crystal.getId(), System.nanoTime());
         mc.player.connection.send(new ServerboundAttackPacket(crystal.getId()));
         mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
         this.tryReactiveReplace(crystalPos);
         return true;
      } else {
         return false;
      }
   }

   private void tryReactiveReplace(BlockPos airPos) {
      if (this.place.getValue()) {
         if (!this.pendingReactivePlacements.containsKey(airPos.asLong())) {
            AutoCrystalModule.PlaceTarget target = this.lastPlaceTarget;
            long sentNanos = this.lastPlaceSentNanos;
            long nowNanos = System.nanoTime();
            if (target != null && target.base.above().equals(airPos)) {
               if (nowNanos - sentNanos <= 500000000L) {
                  if (this.placeTimer.passedMs(this.placeDelay.getValue().intValue())) {
                     if (this.isDesirablePlacement(airPos, false)) {
                        if (!this.isSlowPlaceThrottled(target.damage)) {
                           if (this.doPlace(target, false, true)) {
                              this.diagReactivePlace++;
                              this.placeTimer.reset();
                              this.recordSlowPlace(target.damage);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void onBlockUpdate(BlockPos pos, boolean nowAir) {
      AutoMineModule autoMine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
      if (autoMine == null || !autoMine.shouldClaimMineCrystalUpdate(pos)) {
         if (nowAir) {
            if (!nullCheck() && !mc.player.isDeadOrDying()) {
               OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
               if (offhand == null || !offhand.shouldDeferForEat()) {
                  if (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
                     AutoSwordModule sword = KetamineClub.moduleManager.getModuleByClass(AutoSwordModule.class);
                     if (sword == null || !sword.isEnabled() || !sword.isMaceAttackReady()) {
                        double cutoff = 64.0;
                        if (!(mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > cutoff)) {
                           this.updateSeeThrough();
                           TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
                           double scanRadius = 18.0;
                           List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, scanRadius);
                           if (this.doBreak.getValue() && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                              EndCrystal target = this.findBestCrystal(potentialTargets);
                              if (target != null && this.breakCrystal(target)) {
                                 this.breakTimer.reset();
                                 return;
                              }
                           }

                           if (this.place.getValue() && this.placeTimer.passedMs(this.placeDelay.getValue().intValue())) {
                              if (this.multiReact.getValue() || mc.player.tickCount != this.lastReactorPlaceTick) {
                                 this.lastReactorPlaceTick = mc.player.tickCount;
                                 AutoCrystalModule.PlaceTarget placeTarget = this.findBestPlace(potentialTargets);
                                 if (placeTarget != null && this.doPlace(placeTarget)) {
                                    this.placeTimer.reset();
                                    this.recordSlowPlace(placeTarget.damage);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Subscribe(
      priority = 1000
   )
   private void onPreTickReleaseStale(PreTickEvent event) {
      this.releasePendingSwap();
   }

   @Subscribe(
      priority = -100
   )
   private void onPreTickRestore(PreTickEvent event) {
      this.releasePendingSwap();
   }

   private void releasePendingSwap() {
      if (this.pendingSwapHandle != null) {
         KetamineClub.swapManager.release(this.pendingSwapHandle);
         this.pendingSwapHandle = null;
      }
   }

   public boolean hasCompletedPlacementSwap() {
      return !this.placingCrystal && this.pendingSwapHandle != null && KetamineClub.swapManager.holdsActive(this.pendingSwapHandle);
   }

   public void releaseCompletedPlacementSwap() {
      if (this.hasCompletedPlacementSwap()) {
         this.releasePendingSwap();
      }
   }

   @Subscribe(
      priority = -1
   )
   private void onBasePlaceTick(PreTickEvent event) {
      if (this.basePlace.getValue() && this.place.getValue() && !nullCheck() && !mc.player.isDeadOrDying()) {
         this.ensureRenderSession();
         this.updateSeeThrough();
         this.basePlacer
            .tick(
               new BasePlaceController.Config(
                  this.baseDiffRatio.getValue(),
                  this.basePauseEat.getValue(),
                  this.baseDelay.getValue(),
                  this.baseReplaceDelay.getValue(),
                  this.baseSponge.getValue()
               )
            );
      } else {
         this.basePlacer.reset();
      }
   }

   @Subscribe
   private void onPreTick(PreTickEvent event) {
      if (!nullCheck() && !mc.player.isDeadOrDying()) {
         this.ensureRenderSession();
         this.crystalRenderTracker.prune(trackingTimeMs());
         if (!this.rotate.getValue()) {
            this.clearCrystalRotations();
         }

         if (this.reactivePipelineLevel != mc.level) {
            this.resetReactivePipeline();
            this.reactivePipelineLevel = mc.level;
         }

         this.expireReactivePlacements();
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand == null || !offhand.shouldDeferForEat()) {
            if (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
               AutoSwordModule sword = KetamineClub.moduleManager.getModuleByClass(AutoSwordModule.class);
               if (sword != null && sword.isEnabled() && sword.isMaceAttackReady()) {
                  this.lastBestDamage = 0.0F;
               } else {
                  this.ageCrystalPlaces();
                  this.updateSeeThrough();
                  boolean antiChineseReady = this.antiChinese.getValue() && this.enemyNearby(this.antiChineseEnemyRange.getValue());
                  boolean broke = false;
                  if (this.doBreak.getValue() && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                     TargetsModule breakTargets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
                     List<AutoCrystalModule.TargetCache> breakTargetCache = this.collectTargets(breakTargets, this.breakSearchRange() + 12.0);
                     EndCrystal target = this.findBestCrystal(breakTargetCache);
                     if (target != null && this.breakCrystal(target)) {
                        this.breakTimer.reset();
                        broke = true;
                     }
                  }

                  if (!broke && antiChineseReady && this.breakTimer.passedMs(this.breakDelay.getValue().intValue())) {
                     EndCrystal acTarget = this.findAntiChineseCrystal();
                     if (acTarget != null && this.breakCrystal(acTarget)) {
                        this.breakTimer.reset();
                        broke = true;
                     }
                  }

                  if (!broke) {
                     boolean canPlace = this.place.getValue() && this.placeTimer.passedMs(this.placeDelay.getValue().intValue());
                     long calcStart = System.nanoTime();
                     TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
                     double scanRadius = 18.0;
                     List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, scanRadius);
                     AutoCrystalModule.PlaceTarget crystalTarget = canPlace ? this.findBestPlace(potentialTargets) : null;
                     this.lastCalcMs = (System.nanoTime() - calcStart) / 1000000.0;
                     if (this.debug.getValue()) {
                        ChatUtil.sendPersistent("autocrystal:calc", Component.literal(String.format("AutoCrystal calc: %.3f ms", this.lastCalcMs)));
                     }

                     boolean placed = false;
                     if (crystalTarget != null && this.doPlace(crystalTarget)) {
                        this.placeTimer.reset();
                        this.recordSlowPlace(crystalTarget.damage);
                        placed = true;
                     }

                     if (!placed && antiChineseReady && canPlace) {
                        AutoCrystalModule.PlaceTarget acPlace = this.findAntiChinesePlace();
                        if (acPlace != null && this.doPlace(acPlace)) {
                           this.placeTimer.reset();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private EndCrystal findBestCrystal(List<AutoCrystalModule.TargetCache> potentialTargets) {
      EndCrystal best = null;
      float bestDmg = 0.0F;
      float bestScore = 0.0F;
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      double maxSelf = this.maxSelfDamage.getValue();
      double searchRange = this.breakSearchRange();
      AABB searchArea = mc.player.getBoundingBox().inflate(searchRange + 2.0);

      for (Entity e : mc.level.getEntities(null, searchArea)) {
         if (e instanceof EndCrystal crystal && !this.isChainCrystalProtected(crystal)) {
            Long instantAttack = this.instantAttacks.get(crystal.getId());
            if (instantAttack == null || System.nanoTime() - instantAttack > 500000000L) {
               AABB bb = crystal.getBoundingBox();
               if (this.canReachBox(bb)) {
                  Vec3 cp = crystal.position();
                  if (anyTargetWithin(potentialTargets, cp, 144.0) && this.selfDamageOk(cp, null, maxSelf, playerHealth)) {
                     float totalDmg = 0.0F;
                     float totalScore = 0.0F;
                     boolean anyTarget = false;

                     for (AutoCrystalModule.TargetCache tc : potentialTargets) {
                        if (!(tc.pos.distanceToSqr(cp) > 144.0)) {
                           float dmg = this.calcDamage(tc, cp);
                           if (this.damageAccepted(tc, dmg)) {
                              totalDmg += dmg;
                              totalScore += this.damageSelectionScore(tc, dmg);
                              anyTarget = true;
                           }
                        }
                     }

                     if (anyTarget && totalScore > bestScore) {
                        bestScore = totalScore;
                        bestDmg = totalDmg;
                        best = crystal;
                     }
                  }
               }
            }
         }
      }

      this.lastBestDamage = bestDmg;
      return best;
   }

   public boolean breakCrystal(EndCrystal crystal) {
      return this.breakCrystal(crystal, false, true);
   }

   public boolean breakCrystalForPistonCrystal(EndCrystal crystal) {
      return this.breakCrystal(crystal, true, true);
   }

   public boolean breakCrystalForSurround(EndCrystal crystal) {
      return this.breakCrystalForSurround(crystal, false);
   }

   public boolean breakCrystalForSurround(EndCrystal crystal, boolean blocksRepair) {
      if (nullCheck() || mc.getConnection() == null || !crystal.isAlive()) {
         return false;
      } else if (!blocksRepair && this.isChainCrystalProtected(crystal)) {
         return false;
      } else {
         long now = System.nanoTime();
         Long previous = this.instantAttacks.get(crystal.getId());
         if (previous != null && now - previous < this.reactiveConfirmTimeoutMs() * 1000000L) {
            return true;
         } else {
            Vec3 eye = this.bestReachEye(crystal.getBoundingBox());
            float[] angles = MathUtil.calcAngle(eye, this.getClosestPointToEye(eye, crystal.getBoundingBox()));
            if (!this.canBreakCrystal(crystal, angles[0], angles[1])) {
               return false;
            } else {
               if (!this.canBreakCrystal(crystal, KetamineClub.rotationManager.getServerYaw(), KetamineClub.rotationManager.getServerPitch())) {
                  RotationRequest rotation = new RotationRequest("Surround_break", 90, angles[0], angles[1], RotationRequest.Mode.SILENT, false, true, false);
                  if (!KetamineClub.rotationManager.submit(rotation)
                     || !this.canBreakCrystal(crystal, KetamineClub.rotationManager.getServerYaw(), KetamineClub.rotationManager.getServerPitch())) {
                     return false;
                  }
               }

               mc.getConnection().send(new ServerboundAttackPacket(crystal.getId()));
               mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
               this.instantAttacks.put(crystal.getId(), now);
               this.diagBreakSent++;
               return true;
            }
         }
      }
   }

   private boolean breakCrystal(EndCrystal crystal, boolean allowPistonCrystal, boolean reactiveReplace) {
      if (!allowPistonCrystal && this.isChainCrystalProtected(crystal)) {
         return false;
      } else {
         long nowNanos = System.nanoTime();
         this.instantAttacks.entrySet().removeIf(entry -> nowNanos - entry.getValue() > 500000000L);
         Long attackedAt = this.instantAttacks.get(crystal.getId());
         if (attackedAt != null && nowNanos - attackedAt <= 500000000L) {
            return false;
         } else {
            AABB box = crystal.getBoundingBox();
            Vec3 eyePos = this.bestReachEye(box);
            Vec3 hit = this.getClosestPointToEye(eyePos, box);
            float[] angles = MathUtil.calcAngle(eyePos, hit);
            if (!this.canBreakCrystal(crystal, angles[0], angles[1])) {
               return false;
            } else {
               boolean preRotated = !this.rotate.getValue()
                  || this.canBreakCrystal(crystal, KetamineClub.rotationManager.getServerYaw(), KetamineClub.rotationManager.getServerPitch());
               return preRotated
                     || this.submitCrystalRotation("AutoCrystal_break", 60, angles[0], angles[1], false)
                        && this.canBreakCrystal(crystal, KetamineClub.rotationManager.getServerYaw(), KetamineClub.rotationManager.getServerPitch())
                  ? this.attackCrystal(crystal, reactiveReplace)
                  : false;
            }
         }
      }
   }

   private boolean attackCrystal(EndCrystal crystal, boolean reactiveReplace) {
      if (!nullCheck() && crystal.isAlive()) {
         BlockPos crystalPos = crystal.blockPosition();
         this.diagBreakSent++;
         this.instantAttacks.put(crystal.getId(), System.nanoTime());
         mc.gameMode.attack(mc.player, crystal);
         mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
         if (reactiveReplace) {
            this.tryReactiveReplace(crystalPos);
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean breakCrystalAt(BlockPos airPos) {
      if (!nullCheck() && airPos != null) {
         AABB search = new AABB(airPos).inflate(1.0);

         for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, search)) {
            if (crystal.blockPosition().equals(airPos) && this.breakCrystal(crystal)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean canBreakCrystal(EndCrystal crystal, float yaw, float pitch) {
      AABB bb = crystal.getBoundingBox();
      Vec3 look = this.getLookVector(yaw, pitch);

      for (Vec3 eyePos : this.possibleReachEyes()) {
         if (bb.contains(eyePos)) {
            return true;
         }

         Vec3 reachEnd = eyePos.add(look.scale(3.0));
         if (bb.clip(eyePos, reachEnd).isPresent()) {
            return true;
         }
      }

      return false;
   }

   private AutoCrystalModule.PlaceTarget findBestPlace(List<AutoCrystalModule.TargetCache> potentialTargets) {
      Vec3 eyePos = mc.player.getEyePosition(1.0F);
      BlockPos playerPos = mc.player.blockPosition();
      int r = (int)Math.ceil(6.0);
      long now = System.currentTimeMillis();
      double placeRangeSq = 36.0;
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      MutableBlockPos cursor = new MutableBlockPos();
      int rr = r * r;
      this.candidates.clear();

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  BlockState baseState = mc.level.getBlockState(cursor);
                  if (baseState.is(Blocks.OBSIDIAN) || baseState.is(Blocks.BEDROCK)) {
                     int bx = cursor.getX();
                     int by = cursor.getY();
                     int bz = cursor.getZ();
                     int ay = by + 1;
                     cursor.set(bx, ay, bz);
                     BlockState airState = mc.level.getBlockState(cursor);
                     if (airState.isAir() && !(sqDistToBox(eyePos, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq)) {
                        double cx = bx + 0.5;
                        double cy = by + 1.0;
                        double cz = bz + 0.5;
                        AABB crystalBox = new AABB(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
                        if (this.canReachBox(crystalBox)) {
                           Vec3 crystalCenter = new Vec3(cx, cy, cz);
                           float boundTotal = 0.0F;
                           boolean anyTarget = false;
                           int i = 0;

                           for (int n = potentialTargets.size(); i < n; i++) {
                              AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
                              if (!(tc.pos.distanceToSqr(crystalCenter) > 144.0)) {
                                 float dmg = this.maxDamage(tc, crystalCenter);
                                 if (this.damageAccepted(tc, dmg)) {
                                    boundTotal += dmg;
                                    anyTarget = true;
                                 }
                              }
                           }

                           if (anyTarget) {
                              this.candidates
                                 .add(new AutoCrystalModule.PlaceCandidate(new BlockPos(bx, by, bz), new BlockPos(bx, ay, bz), crystalCenter, boundTotal));
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (this.candidates.isEmpty()) {
         return null;
      } else {
         this.candidates.sort((a, b) -> Float.compare(b.bound, a.bound));
         BlockPos locked = null;
         long placePendingMs = this.reactiveConfirmTimeoutMs();
         if (this.lastPlaceAir != null) {
            long lockTs = this.crystalPlaces.get(this.lastPlaceAir.asLong());
            boolean pending = lockTs != 0L && now - lockTs < placePendingMs;
            if (!pending && !this.hasLiveCrystalAt(this.lastPlaceAir)) {
               this.lastPlaceAir = null;
            } else {
               double cx = this.lastPlaceAir.getX() + 0.5;
               double cy = this.lastPlaceAir.getY();
               double cz = this.lastPlaceAir.getZ() + 0.5;
               AABB crystalBox = new AABB(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
               if (this.canReachBox(crystalBox)) {
                  locked = this.lastPlaceAir;
               } else {
                  this.lastPlaceAir = null;
               }
            }
         }

         AutoCrystalModule.PlaceTarget best = null;
         float bestScore = 0.0F;
         int i = 0;

         for (int nx = this.candidates.size(); i < nx; i++) {
            AutoCrystalModule.PlaceCandidate c = this.candidates.get(i);
            if (!this.lethalScoring.getValue() && best != null && c.bound <= best.damage) {
               break;
            }

            if ((locked == null || c.airPos.equals(locked)) && !this.isBlocked(c.airPos) && !this.pendingReactivePlacements.containsKey(c.airPos.asLong())) {
               long pendingTs = this.crystalPlaces.get(c.airPos.asLong());
               if ((pendingTs == 0L || now - pendingTs >= placePendingMs) && this.selfDamageOk(c.crystalCenter, null, maxSelf, playerHealth)) {
                  float totalDmg = 0.0F;
                  float totalScore = 0.0F;
                  boolean anyTarget = false;
                  int j = 0;

                  for (int m = potentialTargets.size(); j < m; j++) {
                     AutoCrystalModule.TargetCache tc = potentialTargets.get(j);
                     if (!(tc.pos.distanceToSqr(c.crystalCenter) > 144.0)) {
                        float dmg = this.calcDamage(tc, c.crystalCenter);
                        if (this.damageAccepted(tc, dmg)) {
                           totalDmg += dmg;
                           totalScore += this.damageSelectionScore(tc, dmg);
                           anyTarget = true;
                        }
                     }
                  }

                  if (anyTarget && !this.isSlowPlaceThrottled(totalDmg) && (best == null || totalScore > bestScore)) {
                     best = new AutoCrystalModule.PlaceTarget(c.base, totalDmg);
                     bestScore = totalScore;
                  }
               }
            }
         }

         return best;
      }
   }

   private float damageSelectionScore(AutoCrystalModule.TargetCache target, float damage) {
      if (!this.lethalScoring.getValue()) {
         return damage;
      } else {
         float health = Math.max(0.5F, target.hp + target.abs);
         return damage >= health ? 1000.0F + damage : damage / health * 10.0F;
      }
   }

   private boolean isSlowPlaceThrottled(float damage) {
      return this.facePlace.getValue() == AutoCrystalModule.FacePlaceMode.SLOW
         && damage <= this.facePlaceThreshold.getValue()
         && !this.slowPlaceTimer.passedMs(500L);
   }

   private void recordSlowPlace(float damage) {
      if (this.facePlace.getValue() == AutoCrystalModule.FacePlaceMode.SLOW && damage <= this.facePlaceThreshold.getValue()) {
         this.slowPlaceTimer.reset();
      }
   }

   private boolean damageAccepted(AutoCrystalModule.TargetCache tc, float damage) {
      return this.facePlace.getValue() == AutoCrystalModule.FacePlaceMode.FORCE ? damage > 0.0F : damage >= this.getDynamicMin(tc.hp, tc.abs, tc.armorBroken);
   }

   @Subscribe
   private void onKeyInput(KeyInputEvent event) {
      if (!this.facePlaceBind.getValue().isEmpty() && event.getAction() == 1) {
         if (event.getKey() == this.facePlaceBind.getValue().getKey()) {
            AutoCrystalModule.FacePlaceMode next = switch ((AutoCrystalModule.FacePlaceMode)this.facePlace.getValue()) {
               case SLOW -> AutoCrystalModule.FacePlaceMode.NORMAL;
               case NORMAL -> AutoCrystalModule.FacePlaceMode.FORCE;
               case FORCE -> AutoCrystalModule.FacePlaceMode.SLOW;
            };
            this.facePlace.setValue(next);
            this.announceFacePlace(next);
         }
      }
   }

   private void announceFacePlace(AutoCrystalModule.FacePlaceMode mode) {
      ChatFormatting color = switch (mode) {
         case SLOW -> ChatFormatting.AQUA;
         case NORMAL -> ChatFormatting.BLUE;
         case FORCE -> ChatFormatting.LIGHT_PURPLE;
      };

      String label = switch (mode) {
         case SLOW -> "Slow";
         case NORMAL -> "Normal";
         case FORCE -> "Force";
      };
      ChatUtil.sendPersistent(
         "autocrystal:faceplace",
         Component.literal("FacePlace: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(label).withStyle(new ChatFormatting[]{color, ChatFormatting.BOLD}))
      );
   }

   private boolean isBurrowBlock(BlockState state) {
      return state.is(Blocks.REDSTONE_BLOCK)
         || state.is(Blocks.PISTON)
         || state.is(Blocks.STICKY_PISTON)
         || state.is(Blocks.PISTON_HEAD)
         || state.is(Blocks.MOVING_PISTON);
   }

   private boolean nearBurrowBlock(Vec3 center) {
      int r = (int)Math.ceil(3.0);
      double radSq = 9.0;
      double playerRangeSq = this.antiChineseRange.getValue() * this.antiChineseRange.getValue();
      Vec3 playerPos = mc.player.position();
      int cx = Mth.floor(center.x);
      int cy = Mth.floor(center.y);
      int cz = Mth.floor(center.z);
      MutableBlockPos cursor = new MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               int bx = cx + dx;
               int by = cy + dy;
               int bz = cz + dz;
               double ox = bx + 0.5 - center.x;
               double oy = by + 0.5 - center.y;
               double oz = bz + 0.5 - center.z;
               if (!(ox * ox + oy * oy + oz * oz > radSq) && !(playerPos.distanceToSqr(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq)) {
                  cursor.set(bx, by, bz);
                  if (this.isBurrowBlock(this.getStateFast(cursor))) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private boolean adjacentToBurrowBlock(BlockPos airPos) {
      double playerRangeSq = this.antiChineseRange.getValue() * this.antiChineseRange.getValue();
      Vec3 playerPos = mc.player.position();
      MutableBlockPos cursor = new MutableBlockPos();

      for (int[] o : FACE_OFFSETS) {
         int bx = airPos.getX() + o[0];
         int by = airPos.getY() + o[1];
         int bz = airPos.getZ() + o[2];
         if (!(playerPos.distanceToSqr(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq)) {
            cursor.set(bx, by, bz);
            if (this.isBurrowBlock(this.getStateFast(cursor))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean enemyNearby(double range) {
      TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
      double rangeSq = range * range;
      AABB area = mc.player.getBoundingBox().inflate(range);

      for (Entity e : mc.level.getEntities(mc.player, area)) {
         if (e instanceof Player p && (targets == null || targets.isValidPlayerTarget(p)) && mc.player.distanceToSqr(p) <= rangeSq) {
            return true;
         }
      }

      return false;
   }

   private AutoCrystalModule.PlaceTarget findAntiChinesePlace() {
      Vec3 eyePos = mc.player.getEyePosition(1.0F);
      BlockPos playerPos = mc.player.blockPosition();
      int r = (int)Math.ceil(6.0);
      long now = System.currentTimeMillis();
      double placeRangeSq = 36.0;
      double maxSelf = this.maxSelfDamage.getValue();
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      MutableBlockPos cursor = new MutableBlockPos();
      int rr = r * r;
      AutoCrystalModule.PlaceTarget best = null;
      double bestDistSq = Double.MAX_VALUE;

      for (int x = -r; x <= r; x++) {
         int xx = x * x;

         for (int y = -r; y <= r; y++) {
            int xxyy = xx + y * y;

            for (int z = -r; z <= r; z++) {
               if (xxyy + z * z <= rr) {
                  cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                  BlockState baseState = mc.level.getBlockState(cursor);
                  if (baseState.is(Blocks.OBSIDIAN) || baseState.is(Blocks.BEDROCK)) {
                     int bx = cursor.getX();
                     int by = cursor.getY();
                     int bz = cursor.getZ();
                     int ay = by + 1;
                     cursor.set(bx, ay, bz);
                     BlockState airState = mc.level.getBlockState(cursor);
                     if (airState.isAir() && !(sqDistToBox(eyePos, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq)) {
                        double cx = bx + 0.5;
                        double cy = by + 1.0;
                        double cz = bz + 0.5;
                        AABB crystalBox = new AABB(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
                        if (this.canReachBox(crystalBox)) {
                           Vec3 crystalCenter = new Vec3(cx, cy, cz);
                           double dSq = eyePos.distanceToSqr(crystalCenter);
                           if (!(dSq >= bestDistSq)) {
                              BlockPos airPos = new BlockPos(bx, ay, bz);
                              if (this.adjacentToBurrowBlock(airPos) && !this.isBlocked(airPos) && !this.pendingReactivePlacements.containsKey(airPos.asLong())
                                 )
                               {
                                 long pendingTs = this.crystalPlaces.get(airPos.asLong());
                                 if ((pendingTs == 0L || now - pendingTs >= this.reactiveConfirmTimeoutMs())
                                    && this.selfDamageOk(crystalCenter, null, maxSelf, playerHealth)) {
                                    bestDistSq = dSq;
                                    best = new AutoCrystalModule.PlaceTarget(new BlockPos(bx, by, bz), 0.0F);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private EndCrystal findAntiChineseCrystal() {
      float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      double maxSelf = this.maxSelfDamage.getValue();
      double searchRange = this.breakSearchRange();
      EndCrystal best = null;
      double bestDist = Double.MAX_VALUE;
      AABB searchArea = mc.player.getBoundingBox().inflate(searchRange + 2.0);

      for (Entity e : mc.level.getEntities(null, searchArea)) {
         if (e instanceof EndCrystal crystal && !this.isChainCrystalProtected(crystal)) {
            AABB bb = crystal.getBoundingBox();
            if (this.canReachBox(bb)) {
               Vec3 cp = crystal.position();
               if (this.nearBurrowBlock(cp) && this.selfDamageOk(cp, null, maxSelf, playerHealth)) {
                  double d = mc.player.distanceToSqr(crystal);
                  if (d < bestDist) {
                     bestDist = d;
                     best = crystal;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean isChainCrystalProtected(EndCrystal crystal) {
      PistonCrystalModule pistonCrystal = KetamineClub.moduleManager.getModuleByClass(PistonCrystalModule.class);
      if (pistonCrystal != null && pistonCrystal.isChainCrystalProtected(crystal)) {
         return true;
      } else {
         AutoMineModule autoMine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
         return autoMine != null && autoMine.isMineChainCrystalProtected(crystal);
      }
   }

   private List<AutoCrystalModule.TargetCache> collectTargets(TargetsModule targets, double scanRadius) {
      List<AutoCrystalModule.TargetCache> out = new ArrayList<>();
      AABB area = mc.player.getBoundingBox().inflate(scanRadius);
      Player preferred = null;
      if (this.sharedTarget.getValue()) {
         AutoMineModule autoMine = KetamineClub.moduleManager.getModuleByClass(AutoMineModule.class);
         Player mineTarget = autoMine != null && autoMine.isEnabled() ? autoMine.getTargetPlayer() : null;
         if (mineTarget != null
            && !mineTarget.isDeadOrDying()
            && !mineTarget.isRemoved()
            && area.intersects(mineTarget.getBoundingBox())
            && (targets == null || targets.isValidPlayerTarget(mineTarget))) {
            preferred = mineTarget;
         }
      }

      for (Entity e : mc.level.getEntities(mc.player, area)) {
         if (e instanceof LivingEntity living
            && !living.isDeadOrDying()
            && !this.deadIds.contains(e.getId())
            && (preferred == null || e == preferred)
            && (targets == null || targets.isValidPlayerTarget(e))) {
            out.add(this.targetCache(living));
         }
      }

      return out;
   }

   private AutoCrystalModule.TargetCache targetCache(LivingEntity living) {
      boolean armorBroken = living instanceof Player player && this.isAnyArmorBroken(player);
      int protection = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         if (!living.getItemBySlot(slot).isEmpty()) {
            protection += slot == EquipmentSlot.LEGS ? 8 : 4;
         }
      }

      return new AutoCrystalModule.TargetCache(
         living.position(),
         living.getBoundingBox(),
         living.getHealth(),
         living.getAbsorptionAmount(),
         armorBroken,
         (float)living.getAttributeValue(Attributes.ARMOR),
         (float)living.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
         protection
      );
   }

   double basePlaceRange() {
      return 6.0;
   }

   long basePlaceConfirmMs() {
      return this.reactiveConfirmTimeoutMs() + 150L;
   }

   boolean isBasePlaceTarget(Player player) {
      if (!nullCheck()
         && player != null
         && player != mc.player
         && !player.isRemoved()
         && !player.isDeadOrDying()
         && !this.deadIds.contains(player.getId())
         && mc.player.getBoundingBox().inflate(18.0).intersects(player.getBoundingBox())) {
         TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
         return targets == null || targets.isValidPlayerTarget(player);
      } else {
         return false;
      }
   }

   boolean inBaseCrystalRange(BlockPos base) {
      BlockPos air = base.above();
      Vec3 eye = mc.player.getEyePosition(1.0F);
      if (sqDistToBox(eye, air.getX(), air.getY(), air.getZ(), air.getX() + 1, air.getY() + 1, air.getZ() + 1) > 36.0) {
         return false;
      } else {
         double x = base.getX() + 0.5;
         double y = base.getY() + 1.0;
         double z = base.getZ() + 0.5;
         return this.canReachBox(new AABB(x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0));
      }
   }

   double basePlaceDamage(BlockPos base, Player target, double diffRatio) {
      if (this.isBasePlaceTarget(target) && this.inBaseCrystalRange(base)) {
         SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
         if (mine != null && mine.isEnabled() && mine.alreadyBreaking(base)) {
            return 0.0;
         } else {
            Vec3 explosion = new Vec3(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
            AutoCrystalModule.TargetCache cache = this.targetCache(target);
            if (cache.pos.distanceToSqr(explosion) > 144.0) {
               return 0.0;
            } else {
               float enemyDamage = this.calcDamage(cache, explosion, base);
               if (!this.damageAccepted(cache, enemyDamage)) {
                  return 0.0;
               } else {
                  float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                  if (!this.selfDamageOk(explosion, base, this.maxSelfDamage.getValue(), health)) {
                     return 0.0;
                  } else {
                     float selfDamage = this.calcDamageSelf(explosion, base);
                     return enemyDamage >= diffRatio * (selfDamage + 0.01) ? enemyDamage : 0.0;
                  }
               }
            }
         }
      } else {
         return 0.0;
      }
   }

   private void updateSeeThrough() {
      this.seeThrough.clear();
      if (this.antiSurround.getValue()) {
         SpeedMineController mine = KetamineClub.moduleManager.getSpeedMine();
         if (mine != null && mine.isEnabled()) {
            mine.collectMiningPositions(this.seeThrough, this.antiSurroundCompletion.getValue().intValue() / 100.0);
         }
      }
   }

   private static boolean anyTargetWithin(List<AutoCrystalModule.TargetCache> cache, Vec3 center, double maxDistSq) {
      int i = 0;

      for (int n = cache.size(); i < n; i++) {
         if (cache.get(i).pos.distanceToSqr(center) <= maxDistSq) {
            return true;
         }
      }

      return false;
   }

   private static double sqDistToBox(Vec3 p, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double dx = p.x < minX ? minX - p.x : (p.x > maxX ? p.x - maxX : 0.0);
      double dy = p.y < minY ? minY - p.y : (p.y > maxY ? p.y - maxY : 0.0);
      double dz = p.z < minZ ? minZ - p.z : (p.z > maxZ ? p.z - maxZ : 0.0);
      return dx * dx + dy * dy + dz * dz;
   }

   private boolean isBlocked(BlockPos airPos) {
      AABB placementBox = new AABB(airPos.getX(), airPos.getY(), airPos.getZ(), airPos.getX() + 1.0, airPos.getY() + 2.0, airPos.getZ() + 1.0);
      return !mc.level.getEntities(null, placementBox).isEmpty();
   }

   private boolean doPlace(AutoCrystalModule.PlaceTarget target) {
      return this.doPlace(target, false, false);
   }

   private boolean doPlace(AutoCrystalModule.PlaceTarget target, boolean trustBase) {
      return this.doPlace(target, trustBase, false);
   }

   private boolean doPlace(AutoCrystalModule.PlaceTarget target, boolean trustBase, boolean reactive) {
      if (this.rotate.getValue()) {
         Vec3 aim = Vec3.atCenterOf(target.base).add(0.0, 0.5, 0.0);
         float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0F), aim);
         if (!this.submitCrystalRotation("AutoCrystal_place", 59, angles[0], angles[1], true)) {
            return false;
         }
      }

      return this.doPlaceNow(target, trustBase, reactive);
   }

   private boolean submitCrystalRotation(String id, int priority, float yaw, float pitch, boolean requireReady) {
      RotationRequest request = this.instantRotate.getValue()
         ? new RotationRequest(id, priority, yaw, pitch, RotationRequest.Mode.SILENT, false, true)
         : new RotationRequest(id, priority, yaw, pitch);
      return KetamineClub.rotationManager.submit(request) && (!requireReady || KetamineClub.rotationManager.isServerRotationReady(request));
   }

   private boolean doPlaceNow(AutoCrystalModule.PlaceTarget target, boolean trustBase, boolean reactive) {
      if (this.placingCrystal) {
         return false;
      } else {
         this.placingCrystal = true;

         boolean var4;
         try {
            var4 = this.doPlaceTransaction(target, trustBase, reactive);
         } finally {
            this.placingCrystal = false;
         }

         return var4;
      }
   }

   private boolean doPlaceTransaction(AutoCrystalModule.PlaceTarget target, boolean trustBase, boolean reactive) {
      SurroundModule surround = KetamineClub.moduleManager.getModuleByClass(SurroundModule.class);
      if (surround != null && surround.isEnabled() && surround.hasOpenCoreCell()) {
         return false;
      } else if (!reactive && this.pendingReactivePlacements.containsKey(target.base.above().asLong())) {
         return false;
      } else if (KetamineClub.swapManager.isBlockedByOther("AutoCrystal", 60)) {
         return false;
      } else {
         Result result = InventoryUtil.find(Items.END_CRYSTAL, EnumSet.of(ResultType.HOTBAR));
         if (!result.found()) {
            this.lastBestDamage = 0.0F;
            return false;
         } else {
            this.lastBestDamage = target.damage;
            BlockPos base = target.base;
            int slot = result.slot();
            if (this.offhandPlace.getValue()) {
               this.diagPlaceAttempt++;
               boolean sentOffhand = reactive
                  ? KetamineClub.placementManager.placeCrystalOffhandReactive(base, slot, trustBase)
                  : KetamineClub.placementManager.placeCrystalOffhandAutoCrystal(base, slot, trustBase);
               if (sentOffhand) {
                  this.diagPlaceSent++;
                  this.recordPlace(target, reactive);
               }

               return sentOffhand;
            } else {
               int originalSlot = KetamineClub.swapManager.serverSlot();
               if (this.pendingSwapHandle != null && this.pendingSwapHandle.isReleased()) {
                  this.pendingSwapHandle = null;
               }

               SwapManager.SwapHandle handle = this.pendingSwapHandle;
               boolean acquiredNow = false;
               if (slot != originalSlot && handle == null) {
                  handle = KetamineClub.swapManager.acquire("AutoCrystal", 60);
                  if (handle == null) {
                     return false;
                  }

                  acquiredNow = true;
               }

               this.diagPlaceAttempt++;
               boolean sent = reactive
                  ? KetamineClub.placementManager.placeCrystalAutoCrystalReactive(base, slot, trustBase)
                  : KetamineClub.placementManager.placeCrystalAutoCrystal(base, slot, trustBase);
               if (sent) {
                  this.diagPlaceSent++;
                  this.recordPlace(target, reactive);
                  if (handle != null) {
                     this.pendingSwapHandle = handle;
                  }
               } else if (acquiredNow) {
                  KetamineClub.swapManager.release(handle);
               }

               return sent;
            }
         }
      }
   }

   private void recordPlace(AutoCrystalModule.PlaceTarget target, boolean reactive) {
      BlockPos airPos = target.base.above();
      if (reactive) {
         this.pendingReactivePlacements.put(airPos.asLong(), System.currentTimeMillis());
      }

      this.crystalPlaces.put(airPos.asLong(), System.currentTimeMillis());
      this.ensureRenderSession();
      CrystalRenderTracker.Render placement = this.crystalRenderTracker.placed(target.base, target.damage, trackingTimeMs(), this.basePlaceConfirmMs());
      this.markRender(placement.base(), placement.damage());
      this.lastPlaceAir = airPos;
      this.lastPlaceSentNanos = System.nanoTime();
      this.lastPlaceTarget = target;
   }

   private void resetReactivePipeline() {
      this.pendingReactivePlacements.clear();
   }

   private void expireReactivePlacements() {
      long now = System.currentTimeMillis();
      long timeout = this.reactiveConfirmTimeoutMs();
      LongIterator iterator = this.pendingReactivePlacements.keySet().iterator();

      while (iterator.hasNext()) {
         long pos = iterator.nextLong();
         if (now - this.pendingReactivePlacements.get(pos) >= timeout) {
            iterator.remove();
            this.diagReactiveMiss++;
         }
      }
   }

   private long reactiveConfirmTimeoutMs() {
      int latency = 0;
      ClientPacketListener connection = mc.getConnection();
      if (connection != null && mc.player != null) {
         PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
         if (info != null) {
            latency = info.getLatency();
         }
      }

      return Math.max(100L, Math.min(500L, latency + 50L));
   }

   private void clearCrystalRotations() {
      KetamineClub.rotationManager.cancel("AutoCrystal_place");
      KetamineClub.rotationManager.cancel("AutoCrystal_break");
      KetamineClub.rotationManager.cancel("AutoCrystal_instant");
      KetamineClub.rotationManager.cancel("AutoCrystal_mine_chain");
   }

   private boolean hasLiveCrystalAt(BlockPos airPos) {
      for (Entity e : mc.level.getEntities(null, new AABB(airPos))) {
         if (e instanceof EndCrystal && e.blockPosition().equals(airPos)) {
            return true;
         }
      }

      return false;
   }

   public boolean canMineCrystalChain() {
      if (!nullCheck() && this.place.getValue() && this.doBreak.getValue() && !mc.player.isDeadOrDying()) {
         OffhandModule offhand = KetamineClub.moduleManager.getModuleByClass(OffhandModule.class);
         if (offhand != null && offhand.shouldDeferForEat()) {
            return false;
         } else if (mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            return false;
         } else {
            AutoSwordModule sword = KetamineClub.moduleManager.getModuleByClass(AutoSwordModule.class);
            return sword != null && sword.isEnabled() && sword.isMaceAttackReady()
               ? false
               : InventoryUtil.find(Items.END_CRYSTAL, EnumSet.of(ResultType.HOTBAR)).found();
         }
      } else {
         return false;
      }
   }

   public boolean executeMineCrystalChain(int topCrystalId, BlockPos innerAirPos, int toolSlot, Runnable stopAction, Consumer<String> packetTrace) {
      if (this.canMineCrystalChain() && innerAirPos != null && packetTrace != null) {
         if (mc.level.getEntity(topCrystalId) instanceof EndCrystal crystal && crystal.isAlive() && crystal.blockPosition().equals(innerAirPos.above())) {
            AABB box = crystal.getBoundingBox();
            if (!this.canReachBox(box)) {
               return false;
            } else {
               float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
               if (!this.selfDamageOk(crystal.position(), null, this.maxSelfDamage.getValue(), health)) {
                  return false;
               } else {
                  BlockPos innerBase = innerAirPos.below();
                  if (!this.prepareMineChainRotation(innerBase)) {
                     return false;
                  } else {
                     Result crystals = InventoryUtil.find(Items.END_CRYSTAL, EnumSet.of(ResultType.HOTBAR));
                     if (!crystals.found()) {
                        return false;
                     } else {
                        Runnable tracedStop = stopAction == null ? null : () -> {
                           packetTrace.accept("STOP_SENT hand=MAIN_HAND toolSlot=" + toolSlot);
                           stopAction.run();
                        };
                        this.diagPlaceAttempt++;
                        boolean sent = KetamineClub.placementManager
                           .mineChainCrystalOffhand(toolSlot, tracedStop, crystal.getId(), crystals.slot(), innerBase, false, packetTrace);
                        if (!sent) {
                           return false;
                        } else {
                           long nowNanos = System.nanoTime();
                           this.instantAttacks.put(crystal.getId(), nowNanos);
                           this.diagBreakSent++;
                           this.diagPlaceSent++;
                           this.recordPlace(new AutoCrystalModule.PlaceTarget(innerBase, 0.0F), true);
                           this.placeTimer.reset();
                           this.breakTimer.reset();
                           return true;
                        }
                     }
                  }
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean retryMineCrystalInner(BlockPos innerAirPos, Consumer<String> packetTrace) {
      if (!this.canMineCrystalChain() || innerAirPos == null || packetTrace == null) {
         return false;
      } else if (mc.level.getBlockState(innerAirPos).isAir() && !this.hasLiveCrystalAt(innerAirPos)) {
         BlockPos base = innerAirPos.below();
         if (!this.prepareMineChainRotation(base)) {
            return false;
         } else if (!this.doPlaceNow(new AutoCrystalModule.PlaceTarget(base, 0.0F), false, true)) {
            return false;
         } else {
            packetTrace.accept("INNER_RETRY_SENT");
            this.placeTimer.reset();
            return true;
         }
      } else {
         return false;
      }
   }

   public boolean prepareMineCrystalChain(BlockPos innerAirPos) {
      return !nullCheck() && innerAirPos != null ? this.prepareMineChainRotation(innerAirPos.below()) : false;
   }

   private boolean prepareMineChainRotation(BlockPos base) {
      if (!this.rotate.getValue()) {
         return true;
      } else {
         Vec3 aim = Vec3.atCenterOf(base).add(0.0, 0.5, 0.0);
         float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0F), aim);
         return this.submitCrystalRotation("AutoCrystal_mine_chain", 62, angles[0], angles[1], true);
      }
   }

   public boolean preplaceCrystal(BlockPos airPos, boolean snap) {
      if (!nullCheck() && this.place.getValue()) {
         BlockPos base = airPos.below();
         BlockState baseState = mc.level.getBlockState(base);
         if (!baseState.is(Blocks.OBSIDIAN) && !baseState.is(Blocks.BEDROCK)) {
            return false;
         } else if (this.isBlocked(airPos)) {
            return false;
         } else {
            long now = System.currentTimeMillis();
            if (this.pendingReactivePlacements.containsKey(airPos.asLong())) {
               return false;
            } else {
               long pendingTs = this.crystalPlaces.get(airPos.asLong());
               return pendingTs != 0L && now - pendingTs < this.reactiveConfirmTimeoutMs()
                  ? false
                  : this.doPlace(new AutoCrystalModule.PlaceTarget(base, 0.0F), false);
            }
         }
      } else {
         return false;
      }
   }

   public boolean preplaceMineCrystalChain(BlockPos airPos) {
      if (KetamineClub.placementManager != null && !KetamineClub.placementManager.hasPending() && KetamineClub.placementManager.placementWindowAvailable(1)) {
         boolean sent = this.preplaceCrystal(airPos, true);
         if (sent) {
            KetamineClub.placementManager.notePlacement(airPos);
         }

         return sent;
      } else {
         return false;
      }
   }

   public void preparePlacement(BlockPos airPos) {
      if (!nullCheck() && this.rotate.getValue() && airPos != null) {
         BlockPos base = airPos.below();
         Vec3 aim = Vec3.atCenterOf(base).add(0.0, 0.5, 0.0);
         float[] angles = MathUtil.calcAngle(mc.player.getEyePosition(1.0F), aim);
         RotationRequest request = new RotationRequest("AutoCrystal_place", 59, angles[0], angles[1]);
         KetamineClub.rotationManager.submit(request);
      }
   }

   public boolean isDesirablePlacement(BlockPos airPos) {
      return this.isDesirablePlacement(airPos, true);
   }

   private boolean isDesirablePlacement(BlockPos airPos, boolean allowAntiSurround) {
      if (nullCheck()) {
         return false;
      } else {
         BlockPos base = airPos.below();
         BlockState baseState = mc.level.getBlockState(base);
         if (!baseState.is(Blocks.OBSIDIAN) && !baseState.is(Blocks.BEDROCK)) {
            return false;
         } else {
            int bx = airPos.getX();
            int by = airPos.getY();
            int bz = airPos.getZ();
            Vec3 eye = mc.player.getEyePosition(1.0F);
            if (sqDistToBox(eye, bx, by, bz, bx + 1, by + 1, bz + 1) > 36.0) {
               return false;
            } else {
               double cx = bx + 0.5;
               double cy = by;
               double cz = bz + 0.5;
               if (!this.canReachBox(new AABB(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0))) {
                  return false;
               } else {
                  Vec3 crystalCenter = new Vec3(cx, cy, cz);
                  float playerHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
                  if (!this.selfDamageOk(crystalCenter, null, this.maxSelfDamage.getValue(), playerHealth)) {
                     return false;
                  } else {
                     TargetsModule targets = KetamineClub.moduleManager.getModuleByClass(TargetsModule.class);
                     List<AutoCrystalModule.TargetCache> potentialTargets = this.collectTargets(targets, 18.0);
                     int i = 0;

                     for (int n = potentialTargets.size(); i < n; i++) {
                        AutoCrystalModule.TargetCache tc = potentialTargets.get(i);
                        if (!(tc.pos.distanceToSqr(crystalCenter) > 144.0)) {
                           if (this.damageAccepted(tc, this.calcDamage(tc, crystalCenter))) {
                              return true;
                           }

                           if (allowAntiSurround && this.antiSurround.getValue() && this.isFeetAdjacent(tc.pos, airPos)) {
                              return true;
                           }
                        }
                     }

                     return false;
                  }
               }
            }
         }
      }
   }

   private boolean isFeetAdjacent(Vec3 targetPos, BlockPos airPos) {
      BlockPos feet = BlockPos.containing(targetPos.x, targetPos.y, targetPos.z);
      if (feet.getY() != airPos.getY()) {
         return false;
      } else {
         int dx = airPos.getX() - feet.getX();
         int dz = airPos.getZ() - feet.getZ();
         return Math.abs(dx) == 1 && dz == 0 || Math.abs(dz) == 1 && dx == 0;
      }
   }

   private void ageCrystalPlaces() {
      long now = System.currentTimeMillis();
      LongIterator it = this.crystalPlaces.keySet().iterator();

      while (it.hasNext()) {
         long key = it.nextLong();
         if (now - this.crystalPlaces.get(key) > 1750L) {
            it.remove();
         }
      }

      long nowNanos = System.nanoTime();
      this.instantAttacks.entrySet().removeIf(entry -> nowNanos - entry.getValue() > 500000000L);
      if (mc.player != null && mc.player.tickCount % 100 == 0) {
         this.deadIds.clear();
      }
   }

   private float calcDamage(AutoCrystalModule.TargetCache tc, Vec3 explosionPos) {
      return this.calcDamage(tc, explosionPos, null);
   }

   private float calcDamage(AutoCrystalModule.TargetCache tc, Vec3 explosionPos, BlockPos phantomObsidianPos) {
      double dist = tc.pos.distanceTo(explosionPos);
      if (dist > 12.0) {
         return 0.0F;
      } else {
         double exposure = this.calcExposure(explosionPos, tc.box, phantomObsidianPos);
         return this.targetDamage(tc, dist, exposure);
      }
   }

   private float maxDamage(AutoCrystalModule.TargetCache tc, Vec3 explosionPos) {
      double dist = tc.pos.distanceTo(explosionPos);
      return dist > 12.0 ? 0.0F : this.targetDamage(tc, dist, 1.0);
   }

   private float targetDamage(AutoCrystalModule.TargetCache tc, double dist, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      } else {
         double impact = (1.0 - dist / 12.0) * exposure;
         if (impact <= 0.0) {
            return 0.0F;
         } else {
            float dmg = this.baseExplosionDamage(impact);
            dmg = this.armorAbsorb(dmg, tc.armor, tc.toughness);
            dmg = CombatRules.getDamageAfterMagicAbsorb(dmg, tc.protPoints);
            return Math.max(dmg, 0.0F);
         }
      }
   }

   private float maxDamageSelf(Vec3 explosionPos) {
      double dist = mc.player.position().distanceTo(explosionPos);
      return dist > 12.0 ? 0.0F : this.selfDamage(dist, 1.0);
   }

   private float calcDamageSelf(Vec3 explosionPos, BlockPos phantomObsidianPos) {
      double dist = mc.player.position().distanceTo(explosionPos);
      if (dist > 12.0) {
         return 0.0F;
      } else {
         double exposure = this.calcExposure(explosionPos, mc.player.getBoundingBox(), phantomObsidianPos);
         return this.selfDamage(dist, exposure);
      }
   }

   private float selfDamage(double dist, double exposure) {
      if (exposure <= 0.0) {
         return 0.0F;
      } else {
         double impact = (1.0 - dist / 12.0) * exposure;
         if (impact <= 0.0) {
            return 0.0F;
         } else {
            float dmg = this.baseExplosionDamage(impact);
            float armor = (float)mc.player.getAttributeValue(Attributes.ARMOR);
            float toughness = (float)mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            dmg = this.armorAbsorb(dmg, armor, toughness);
            dmg = this.selfProtectionAbsorb(dmg);
            return Math.max(dmg, 0.0F);
         }
      }
   }

   private float baseExplosionDamage(double impact) {
      float dmg = (float)((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
      switch (mc.level.getDifficulty()) {
         case EASY:
            dmg = Math.min(dmg / 2.0F + 1.0F, dmg);
            break;
         case HARD:
            dmg *= 1.5F;
      }

      return dmg;
   }

   private boolean selfDamageOk(Vec3 crystalCenter, BlockPos phantomObsidianPos, double maxSelf, float playerHealth) {
      float optimistic = this.maxDamageSelf(crystalCenter);
      if (optimistic <= maxSelf && optimistic + 1.5F < playerHealth) {
         return true;
      } else {
         float real = this.calcDamageSelf(crystalCenter, phantomObsidianPos);
         return real > maxSelf ? false : !(real + 1.5F >= playerHealth);
      }
   }

   private float armorAbsorb(float dmg, float armor, float toughness) {
      float i = 2.0F + toughness / 4.0F;
      float j = Mth.clamp(armor - dmg / i, armor * 0.2F, 20.0F);
      return dmg * (1.0F - j / 25.0F);
   }

   private float selfProtectionAbsorb(float dmg) {
      int epf = 0;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = mc.player.getItemBySlot(slot);
         if (!stack.isEmpty()) {
            epf += EnchantmentUtil.getLevel(Enchantments.PROTECTION, stack);
            epf += 2 * EnchantmentUtil.getLevel(Enchantments.BLAST_PROTECTION, stack);
         }
      }

      if (epf > 20) {
         epf = 20;
      }

      return CombatRules.getDamageAfterMagicAbsorb(dmg, epf);
   }

   private double calcExposure(Vec3 source, AABB box, BlockPos phantomObsidianPos) {
      double dx = box.getXsize();
      double dy = box.getYsize();
      double dz = box.getZsize();
      int steps = 2;
      int total = 0;
      int unblocked = 0;
      AutoCrystalModule.ExposureContext ctx = this.exposureCtx;
      BiFunction<AutoCrystalModule.ExposureContext, BlockPos, BlockHitResult> tester = (c, pos) -> {
         Vec3 from = c.start;
         Vec3 to = c.end;
         if (phantomObsidianPos != null && pos.equals(phantomObsidianPos)) {
            return Shapes.block().clip(from, to, pos);
         } else if (!this.seeThrough.isEmpty() && this.seeThrough.contains(pos)) {
            return null;
         } else {
            BlockState state = this.getStateFast(pos);
            if (state.isAir()) {
               return null;
            } else if (state.getBlock().getExplosionResistance() < 600.0F) {
               return null;
            } else {
               VoxelShape shape = state.getCollisionShape(mc.level, pos);
               return shape.isEmpty() ? null : shape.clip(from, to, pos);
            }
         }
      };

      for (int xi = 0; xi <= steps; xi++) {
         for (int yi = 0; yi <= steps; yi++) {
            for (int zi = 0; zi <= steps; zi++) {
               Vec3 point = new Vec3(box.minX + dx * xi / steps, box.minY + dy * yi / steps, box.minZ + dz * zi / steps);
               ctx.set(point, source);
               if (BlockGetter.traverseBlocks(point, source, ctx, tester, c -> null) == null) {
                  unblocked++;
               }

               total++;
            }
         }
      }

      return total == 0 ? 0.0 : (double)unblocked / total;
   }

   private double getDynamicMin(float health, float absorption, boolean armorBroken) {
      double min = this.minDamage.getValue();
      float hp = health + absorption;
      if (hp / 36.0F <= 0.2) {
         min /= 4.0;
      }

      if (armorBroken) {
         min /= 4.0;
      }

      return min;
   }

   private boolean isAnyArmorBroken(Player player) {
      int threshold = 20;

      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = player.getItemBySlot(slot);
         if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
            int durabilityPct = 100 - stack.getDamageValue() * 100 / stack.getMaxDamage();
            if (durabilityPct < threshold) {
               return true;
            }
         }
      }

      return false;
   }

   private Vec3 getClosestPointToEye(Vec3 eye, AABB box) {
      double x = eye.x;
      double y = eye.y;
      double z = eye.z;
      double VEC = 0.0625;
      double EPS = 1.0E-9;
      if (eye.x < box.minX) {
         x = box.minX;
      } else if (eye.x > box.maxX) {
         x = box.maxX;
      }

      if (eye.y < box.minY) {
         y = box.minY;
      } else if (eye.y > box.maxY) {
         y = box.maxY;
      }

      if (eye.z < box.minZ) {
         z = box.minZ;
      } else if (eye.z > box.maxZ) {
         z = box.maxZ;
      }

      if (Math.abs(x - box.minX) < 1.0E-9) {
         x = Math.min(box.minX + 0.0625, box.maxX - 1.0E-9);
      } else if (Math.abs(x - box.maxX) < 1.0E-9) {
         x = Math.max(box.maxX - 0.0625, box.minX + 1.0E-9);
      }

      if (Math.abs(z - box.minZ) < 1.0E-9) {
         z = Math.min(box.minZ + 0.0625, box.maxZ - 1.0E-9);
      } else if (Math.abs(z - box.maxZ) < 1.0E-9) {
         z = Math.max(box.maxZ - 0.0625, box.minZ + 1.0E-9);
      }

      return new Vec3(x, y, z);
   }

   private Vec3 getLookVector(float yaw, float pitch) {
      float f = (float)Math.cos(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float g = (float)Math.sin(-yaw * (float) (Math.PI / 180.0) - Math.PI);
      float h = -((float)Math.cos(-pitch * (float) (Math.PI / 180.0)));
      float i = (float)Math.sin(-pitch * (float) (Math.PI / 180.0));
      return new Vec3(g * h, i, f * h);
   }

   public float getLastBestDamage() {
      return this.lastBestDamage;
   }

   public double getMinDamage() {
      return this.minDamage.getValue();
   }

   @Override
   public String getDisplayInfo() {
      return this.lastBestDamage > 0.0F ? String.format("%.1f", this.lastBestDamage) : null;
   }

   @Subscribe
   private void onExplodePacket(PacketEvent.Receive event) {
      if (event.getPacket() instanceof ClientboundExplodePacket) {
         long now = System.currentTimeMillis();
         synchronized (this.explodeTimes) {
            this.explodeTimes.addLast(now);
            this.pruneExplodeTimes(now);
         }
      }
   }

   @Override
   public String getMeta() {
      long now = System.currentTimeMillis();
      synchronized (this.explodeTimes) {
         this.pruneExplodeTimes(now);
         return String.valueOf(this.explodeTimes.size());
      }
   }

   private void pruneExplodeTimes(long now) {
      Long first;
      while ((first = this.explodeTimes.peekFirst()) != null && now - first > 1000L) {
         this.explodeTimes.removeFirst();
      }
   }

   private BlockState getStateFast(BlockPos pos) {
      if (mc.level.isOutsideBuildHeight(pos.getY())) {
         return Blocks.VOID_AIR.defaultBlockState();
      } else {
         int cx = pos.getX() >> 4;
         int cz = pos.getZ() >> 4;
         if (this.cachedChunkX != cx || this.cachedChunkZ != cz || this.cachedChunk == null || this.cachedChunkLevel != mc.level) {
            this.cachedChunk = mc.level.getChunk(cx, cz);
            this.cachedChunkLevel = mc.level;
            this.cachedChunkX = cx;
            this.cachedChunkZ = cz;
         }

         ChunkAccess chunk = this.cachedChunk;
         if (chunk == null) {
            return Blocks.AIR.defaultBlockState();
         } else {
            int sectionIdx = mc.level.getSectionIndex(pos.getY());
            LevelChunkSection[] sections = chunk.getSections();
            if (sectionIdx >= 0 && sectionIdx < sections.length) {
               LevelChunkSection section = sections[sectionIdx];
               return section != null && !section.hasOnlyAir()
                  ? section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15)
                  : Blocks.AIR.defaultBlockState();
            } else {
               return Blocks.AIR.defaultBlockState();
            }
         }
      }
   }

   private Vec3 bestReachEye(AABB box) {
      Vec3[] possibleEyes = this.possibleReachEyes();
      Vec3 bestEye = possibleEyes[0];
      double bestSq = sqDistToBox(bestEye, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);

      for (int i = 1; i < possibleEyes.length; i++) {
         Vec3 eye = possibleEyes[i];
         double sq = sqDistToBox(eye, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
         if (sq < bestSq) {
            bestSq = sq;
            bestEye = eye;
         }
      }

      return bestEye;
   }

   private Vec3[] possibleReachEyes() {
      return this.stanceAbuse.getValue() ? GrimUtil.getPossibleEyePositions(mc.player) : new Vec3[]{mc.player.getEyePosition(1.0F)};
   }

   private boolean canReachBox(AABB box) {
      double rangeSq = 9.0;

      for (Vec3 eye : this.possibleReachEyes()) {
         if (sqDistToBox(eye, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ) <= rangeSq) {
            return true;
         }
      }

      return false;
   }

   private double breakSearchRange() {
      if (!this.stanceAbuse.getValue()) {
         return 3.0;
      } else {
         Vec3 realEye = mc.player.getEyePosition(1.0F);
         double maxOffset = 0.0;

         for (Vec3 eye : GrimUtil.getPossibleEyePositions(mc.player)) {
            maxOffset = Math.max(maxOffset, Math.abs(eye.y - realEye.y));
         }

         return 3.0 + maxOffset;
      }
   }

   private static enum DamageTextFont {
      VANILLA,
      CLICKGUI;

      private DamageTextFont() {
      }
   }

   private static final class ExposureContext {
      private Vec3 start = Vec3.ZERO;
      private Vec3 end = Vec3.ZERO;

      private ExposureContext() {
      }

      void set(Vec3 start, Vec3 end) {
         this.start = start;
         this.end = end;
      }
   }

   private static enum FacePlaceMode {
      SLOW,
      NORMAL,
      FORCE;

      private FacePlaceMode() {
      }
   }

   public record PingBypassRenderEntry(BlockPos pos, float damage, long remainingMs) {
   }

   private record PlaceCandidate(BlockPos base, BlockPos airPos, Vec3 crystalCenter, float bound) {
   }

   private record PlaceTarget(BlockPos base, float damage) {
   }

   private record RenderEntry(BlockPos pos, float damage, long startMs) {
   }

   private record TargetCache(Vec3 pos, AABB box, float hp, float abs, boolean armorBroken, float armor, float toughness, int protPoints) {
   }
}
