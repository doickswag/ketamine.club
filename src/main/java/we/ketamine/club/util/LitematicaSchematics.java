package we.ketamine.club.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import we.ketamine.club.KetamineClub;

public final class LitematicaSchematics {
   private static final String MOD_ID = "litematica";
   private static final String[] SCHEMATIC_WORLD_HANDLER_CLASSES = new String[]{
      "fi.dy.masa.litematica.world.SchematicWorldHandler", "litematica.world.SchematicWorldHandler"
   };
   private static final String[] DATA_MANAGER_CLASSES = new String[]{"fi.dy.masa.litematica.data.DataManager", "litematica.data.DataManager"};
   private static final String[] LAYER_RANGE_CLASSES = new String[]{
      "malilib.util.position.LayerRange", "fi.dy.masa.litematica.util.LayerRange", "litematica.util.position.LayerRange"
   };
   private static boolean attemptedInit;
   private static boolean hooksActive;
   private static MethodHandle getSchematicWorldHandle;
   private static MethodHandle getRenderLayerRangeHandle;
   private static MethodHandle isPositionWithinRangeHandle;
   private static MethodHandle dataManagerSupplierHandle;
   private static boolean renderLayerRangeNeedsReceiver;
   private static boolean layerRangeUsesBlockPos;
   private static boolean attemptedRangeBinding;
   private static boolean layerRangeSupported;
   private static boolean boundingBoxSupported;
   private static MethodHandle getPlacementManagerHandle;
   private static boolean placementMgrNeedsReceiver;
   private static MethodHandle getAllPlacementsHandle;
   private static MethodHandle placementIsEnabledHandle;
   private static MethodHandle getBoxesWithinChunkHandle;
   private static Object requiredEnabledArg;
   private static boolean getBoxesNeedsEnabledArg;
   private static Field boxMinX;
   private static Field boxMinY;
   private static Field boxMinZ;
   private static Field boxMaxX;
   private static Field boxMaxY;
   private static Field boxMaxZ;

   private LitematicaSchematics() {
   }

   public static boolean isAvailable() {
      ensureInitialized();
      return hooksActive;
   }

   public static boolean isInstalled() {
      return FabricLoader.getInstance().isModLoaded("litematica");
   }

   public static boolean isMismatched(BlockPos pos, BlockState currentState) {
      return isMismatched(pos, currentState, false, false);
   }

   public static boolean isMismatched(BlockPos pos, BlockState currentState, boolean includeAir, boolean restrictToVisibleLayers) {
      BlockState schematicState = getSchematicState(pos, restrictToVisibleLayers);
      if (schematicState == null) {
         return false;
      } else if (schematicState.isAir()) {
         return !includeAir ? false : !currentState.isAir();
      } else {
         return !schematicState.equals(currentState);
      }
   }

   @Nullable
   public static BlockState getSchematicBlockState(BlockPos pos) {
      return getSchematicState(pos, true);
   }

   @Nullable
   public static BlockState getSchematicBlockState(BlockPos pos, boolean restrictToVisibleLayers) {
      return getSchematicState(pos, restrictToVisibleLayers);
   }

   public static boolean isInsideLoadedSchematicBox(BlockPos pos) {
      ensureInitialized();
      if (hooksActive && boundingBoxSupported) {
         try {
            Object mgr;
            if (placementMgrNeedsReceiver) {
               Object dm = dataManagerSupplierHandle != null ? (Object)dataManagerSupplierHandle.invoke() : null;
               if (dm == null) {
                  return false;
               }

               mgr = (Object)getPlacementManagerHandle.invoke((Object)dm);
            } else {
               mgr = (Object)getPlacementManagerHandle.invoke();
            }

            if (mgr == null) {
               return false;
            } else {
               List<?> placements = (List)getAllPlacementsHandle.invoke((Object)mgr);
               if (placements != null && !placements.isEmpty()) {
                  int chunkX = pos.getX() >> 4;
                  int chunkZ = pos.getZ() >> 4;

                  for (Object pl : placements) {
                     if (placementIsEnabledHandle == null || (boolean)placementIsEnabledHandle.invoke((Object)pl)) {
                        Object boxMap;
                        try {
                           boxMap = getBoxesNeedsEnabledArg
                              ? (Object)getBoxesWithinChunkHandle.invoke((Object)pl, (int)chunkX, (int)chunkZ, (Object)requiredEnabledArg)
                              : (Object)getBoxesWithinChunkHandle.invoke((Object)pl, (int)chunkX, (int)chunkZ);
                        } catch (WrongMethodTypeException var11) {
                           if (getBoxesNeedsEnabledArg) {
                              throw var11;
                           }

                           KetamineClub.LOGGER
                              .warn("getBoxesWithinChunk needs more args than expected, attempting to resolve RequiredEnabled at runtime.", var11);
                           resolveRequiredEnabledFromPlacement(pl);
                           boxMap = getBoxesNeedsEnabledArg
                              ? (Object)getBoxesWithinChunkHandle.invoke((Object)pl, (int)chunkX, (int)chunkZ, (Object)requiredEnabledArg)
                              : null;
                        }

                        if (boxMap != null) {
                           Map<?, ?> map = (Map<?, ?>)boxMap;

                           for (Object box : map.values()) {
                              if (boxContains(box, pos)) {
                                 return true;
                              }
                           }
                        }
                     }
                  }

                  return false;
               } else {
                  return false;
               }
            }
         } catch (Throwable var12) {
            boundingBoxSupported = false;
            KetamineClub.LOGGER.warn("Failed to check Litematica placement bounds; disabling bound checks.", var12);
            return false;
         }
      } else {
         return false;
      }
   }

   private static void resolveRequiredEnabledFromPlacement(Object placement) {
      try {
         Lookup lookup = MethodHandles.lookup();
         Class<?> cls = placement.getClass();

         for (Method m : cls.getMethods()) {
            if ("getBoxesWithinChunk".equals(m.getName())) {
               Class<?>[] params = m.getParameterTypes();
               if (params.length == 3 && params[0] == int.class && params[1] == int.class && params[2].isEnum()) {
                  m.setAccessible(true);
                  getBoxesWithinChunkHandle = lookup.unreflect(m);
                  Class<?> enumClass = params[2];
                  Object[] enumConstants = enumClass.getEnumConstants();
                  requiredEnabledArg = null;

                  for (Object ec : enumConstants) {
                     if (ec.toString().contains("ENABLED")) {
                        requiredEnabledArg = ec;
                        break;
                     }
                  }

                  if (requiredEnabledArg == null && enumConstants.length > 0) {
                     requiredEnabledArg = enumConstants[0];
                  }

                  getBoxesNeedsEnabledArg = true;
                  KetamineClub.LOGGER.info("Resolved getBoxesWithinChunk at runtime: {} with arg {} from {}", m, requiredEnabledArg, enumClass.getName());
                  return;
               }
            }
         }

         KetamineClub.LOGGER.warn("Could not resolve getBoxesWithinChunk with RequiredEnabled at runtime on {}", cls.getName());
      } catch (Throwable var14) {
         KetamineClub.LOGGER.warn("Error resolving RequiredEnabled at runtime", var14);
      }
   }

   private static boolean boxContains(Object box, BlockPos pos) throws Throwable {
      int x = pos.getX();
      int y = pos.getY();
      int z = pos.getZ();
      return x >= boxMinX.getInt(box)
         && x <= boxMaxX.getInt(box)
         && y >= boxMinY.getInt(box)
         && y <= boxMaxY.getInt(box)
         && z >= boxMinZ.getInt(box)
         && z <= boxMaxZ.getInt(box);
   }

   @Nullable
   private static BlockState getSchematicState(BlockPos pos, boolean restrictToVisibleLayers) {
      Object world = getSchematicWorld();
      if (world == null) {
         return null;
      } else {
         if (restrictToVisibleLayers && layerRangeSupported) {
            Object range = getLayerRange();
            if (range != null && !isWithinRange(range, pos)) {
               return null;
            }
         }

         if (world instanceof Level level) {
            return !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) ? null : level.getBlockState(pos);
         } else {
            disableHooks("use schematic world", new IllegalStateException("Unexpected schematic world type: " + world.getClass().getName()));
            return null;
         }
      }
   }

   @Nullable
   private static Object getSchematicWorld() {
      ensureInitialized();
      if (!hooksActive) {
         return null;
      } else {
         try {
            return (Object)getSchematicWorldHandle.invoke();
         } catch (Throwable var1) {
            disableHooks("query schematic world", var1);
            return null;
         }
      }
   }

   @Nullable
   private static Object getLayerRange() {
      if (!layerRangeSupported) {
         return null;
      } else {
         try {
            Object range;
            if (renderLayerRangeNeedsReceiver) {
               Object dm = dataManagerSupplierHandle != null ? (Object)dataManagerSupplierHandle.invoke() : null;
               if (dm == null) {
                  return null;
               }

               range = (Object)getRenderLayerRangeHandle.invoke((Object)dm);
            } else {
               range = (Object)getRenderLayerRangeHandle.invoke();
            }

            if (range == null) {
               return null;
            } else {
               return isPositionWithinRangeHandle == null && !bindRangeInspector(range) ? null : range;
            }
         } catch (Throwable var2) {
            layerRangeSupported = false;
            KetamineClub.LOGGER.warn("Failed to query Litematica render layer range, continuing without range filtering.", var2);
            return null;
         }
      }
   }

   private static boolean isWithinRange(Object range, BlockPos pos) {
      if (!layerRangeSupported) {
         return true;
      } else if (isPositionWithinRangeHandle == null && !bindRangeInspector(range)) {
         return true;
      } else {
         try {
            return layerRangeUsesBlockPos
               ? (boolean)isPositionWithinRangeHandle.invoke((Object)range, (BlockPos)pos)
               : (boolean)isPositionWithinRangeHandle.invoke((Object)range, (int)pos.getX(), (int)pos.getY(), (int)pos.getZ());
         } catch (Throwable var3) {
            layerRangeSupported = false;
            KetamineClub.LOGGER.warn("Failed to evaluate Litematica render range, disabling range checks.", var3);
            return true;
         }
      }
   }

   private static boolean bindRangeInspector(Object range) {
      if (attemptedRangeBinding) {
         return isPositionWithinRangeHandle != null;
      } else {
         attemptedRangeBinding = true;
         if (range == null) {
            layerRangeSupported = false;
            return false;
         } else {
            try {
               Method method = null;
               Class<?> cls = range.getClass();

               try {
                  method = cls.getMethod("isPositionWithinRange", BlockPos.class);
                  layerRangeUsesBlockPos = true;
               } catch (NoSuchMethodException var4) {
                  layerRangeUsesBlockPos = false;
               }

               if (method == null) {
                  method = cls.getMethod("isPositionWithinRange", int.class, int.class, int.class);
               }

               if (method == null) {
                  layerRangeSupported = false;
                  KetamineClub.LOGGER.warn("Litematica render layer range does not expose isPositionWithinRange; disabling range filtering.");
                  return false;
               } else {
                  method.setAccessible(true);
                  isPositionWithinRangeHandle = MethodHandles.lookup().unreflect(method);
                  return true;
               }
            } catch (Throwable var5) {
               layerRangeSupported = false;
               KetamineClub.LOGGER.warn("Failed to bind Litematica render layer range inspector; disabling range filtering.", var5);
               return false;
            }
         }
      }
   }

   private static synchronized void ensureInitialized() {
      if (!attemptedInit) {
         attemptedInit = true;
         if (isInstalled()) {
            try {
               Lookup lookup = MethodHandles.lookup();
               Class<?> handlerClass = loadRequiredClass(SCHEMATIC_WORLD_HANDLER_CLASSES);
               Class<?> dataManagerClass = loadRequiredClass(DATA_MANAGER_CLASSES);
               Method getSchematicWorld = handlerClass.getDeclaredMethod("getSchematicWorld");
               getSchematicWorld.setAccessible(true);
               getSchematicWorldHandle = lookup.unreflect(getSchematicWorld);
               Method getRenderLayerRange = dataManagerClass.getDeclaredMethod("getRenderLayerRange");
               getRenderLayerRange.setAccessible(true);
               renderLayerRangeNeedsReceiver = !Modifier.isStatic(getRenderLayerRange.getModifiers());
               if (renderLayerRangeNeedsReceiver) {
                  try {
                     Method supplier = dataManagerClass.getDeclaredMethod("getInstance");
                     supplier.setAccessible(true);
                     dataManagerSupplierHandle = lookup.unreflect(supplier);
                  } catch (NoSuchMethodException var22) {
                  }
               }

               getRenderLayerRangeHandle = lookup.unreflect(getRenderLayerRange);
               layerRangeSupported = true;
               Class<?> layerRangeClass = loadOptionalClass(LAYER_RANGE_CLASSES);
               if (layerRangeClass != null) {
                  Method isPositionWithinRange = null;

                  try {
                     isPositionWithinRange = layerRangeClass.getMethod("isPositionWithinRange", BlockPos.class);
                     layerRangeUsesBlockPos = true;
                  } catch (NoSuchMethodException var21) {
                     layerRangeUsesBlockPos = false;
                  }

                  if (isPositionWithinRange == null) {
                     isPositionWithinRange = layerRangeClass.getMethod("isPositionWithinRange", int.class, int.class, int.class);
                  }

                  isPositionWithinRangeHandle = lookup.unreflect(isPositionWithinRange);
                  attemptedRangeBinding = true;
               }

               hooksActive = true;

               try {
                  Method getPlMgr = findMethod(dataManagerClass, "getSchematicPlacementManager");
                  if (getPlMgr == null) {
                     throw new NoSuchMethodException(
                        "getSchematicPlacementManager not found on " + dataManagerClass.getName() + "; available: " + listMethodNames(dataManagerClass)
                     );
                  }

                  getPlMgr.setAccessible(true);
                  placementMgrNeedsReceiver = !Modifier.isStatic(getPlMgr.getModifiers());
                  getPlacementManagerHandle = lookup.unreflect(getPlMgr);
                  if (placementMgrNeedsReceiver && dataManagerSupplierHandle == null) {
                     try {
                        Method supplier = dataManagerClass.getDeclaredMethod("getInstance");
                        supplier.setAccessible(true);
                        dataManagerSupplierHandle = lookup.unreflect(supplier);
                     } catch (NoSuchMethodException var20) {
                     }
                  }

                  Class<?> plMgrClass = getPlMgr.getReturnType();
                  Method allPl = findMethod(plMgrClass, "getAllSchematicsPlacements", "getSchematicPlacements", "getAllPlacements");
                  if (allPl == null) {
                     throw new NoSuchMethodException(
                        "No getAllPlacements variant found on " + plMgrClass.getName() + "; available: " + listMethodNames(plMgrClass)
                     );
                  }

                  allPl.setAccessible(true);
                  getAllPlacementsHandle = lookup.unreflect(allPl);
                  Class<?> plClass = loadOptionalClass(
                     "fi.dy.masa.litematica.schematic.placement.SchematicPlacement", "litematica.schematic.placement.SchematicPlacement"
                  );
                  if (plClass == null) {
                     throw new ClassNotFoundException("Could not find SchematicPlacement class");
                  }

                  try {
                     Method isEn = findMethod(plClass, "isEnabled");
                     if (isEn != null) {
                        isEn.setAccessible(true);
                        placementIsEnabledHandle = lookup.unreflect(isEn);
                     }
                  } catch (Throwable var19) {
                  }

                  Method getBoxes = null;
                  Method getBoxesFallback = null;

                  for (Method m : plClass.getMethods()) {
                     if ("getBoxesWithinChunk".equals(m.getName())) {
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 3 && p[0] == int.class && p[1] == int.class && p[2].isEnum()) {
                           getBoxes = m;
                           break;
                        }

                        if (p.length == 2 && p[0] == int.class && p[1] == int.class) {
                           getBoxesFallback = m;
                        }
                     }
                  }

                  if (getBoxes == null) {
                     for (Class<?> c = plClass; c != null && c != Object.class; c = c.getSuperclass()) {
                        for (Method mx : c.getDeclaredMethods()) {
                           if ("getBoxesWithinChunk".equals(mx.getName())) {
                              Class<?>[] px = mx.getParameterTypes();
                              if (px.length == 3 && px[0] == int.class && px[1] == int.class && px[2].isEnum()) {
                                 getBoxes = mx;
                                 break;
                              }

                              if (getBoxesFallback == null && px.length == 2 && px[0] == int.class && px[1] == int.class) {
                                 getBoxesFallback = mx;
                              }
                           }
                        }

                        if (getBoxes != null) {
                           break;
                        }
                     }
                  }

                  if (getBoxes == null) {
                     getBoxes = getBoxesFallback;
                  }

                  if (getBoxes == null) {
                     throw new NoSuchMethodException(
                        "No getBoxesWithinChunk found on " + plClass.getName() + " or its supertypes; available: " + listMethodNames(plClass)
                     );
                  }

                  KetamineClub.LOGGER.info("Bound getBoxesWithinChunk: {} (params: {})", getBoxes, Arrays.toString((Object[])getBoxes.getParameterTypes()));
                  getBoxes.setAccessible(true);
                  getBoxesWithinChunkHandle = lookup.unreflect(getBoxes);
                  Class<?>[] paramTypes = getBoxes.getParameterTypes();
                  if (paramTypes.length >= 3 && paramTypes[2].isEnum()) {
                     Class<?> enumClass = paramTypes[2];
                     Object[] enumConstants = enumClass.getEnumConstants();
                     requiredEnabledArg = null;
                     Object[] var37 = enumConstants;
                     int var39 = enumConstants.length;
                     int var40 = 0;

                     while (true) {
                        if (var40 < var39) {
                           Object ec = var37[var40];
                           if (!ec.toString().contains("ENABLED")) {
                              var40++;
                              continue;
                           }

                           requiredEnabledArg = ec;
                        }

                        if (requiredEnabledArg == null && enumConstants.length > 0) {
                           requiredEnabledArg = enumConstants[0];
                        }

                        getBoxesNeedsEnabledArg = true;
                        KetamineClub.LOGGER.info("getBoxesWithinChunk requires RequiredEnabled arg: {} (from {})", requiredEnabledArg, enumClass.getName());
                        break;
                     }
                  } else {
                     getBoxesNeedsEnabledArg = false;
                  }

                  Class<?> boxClass = loadOptionalClass(
                     "fi.dy.masa.malilib.util.IntBoundingBox", "malilib.util.IntBoundingBox", "malilib.util.position.IntBoundingBox"
                  );
                  if (boxClass == null) {
                     throw new ClassNotFoundException("Could not find IntBoundingBox class");
                  }

                  boxMinX = findField(boxClass, "minX");
                  boxMinY = findField(boxClass, "minY");
                  boxMinZ = findField(boxClass, "minZ");
                  boxMaxX = findField(boxClass, "maxX");
                  boxMaxY = findField(boxClass, "maxY");
                  boxMaxZ = findField(boxClass, "maxZ");
                  if (boxMinX == null || boxMaxX == null || boxMinY == null || boxMaxY == null || boxMinZ == null || boxMaxZ == null) {
                     throw new NoSuchFieldException("Missing fields on " + boxClass.getName() + "; available: " + listFieldNames(boxClass));
                  }

                  boundingBoxSupported = true;
                  KetamineClub.LOGGER.info("Litematica bounding box support initialized (using getBoxesWithinChunk on {}).", plClass.getName());
               } catch (Throwable var23) {
                  boundingBoxSupported = false;
                  KetamineClub.LOGGER
                     .warn("Could not bind Litematica placement bounding box API; extra-block filtering will not be limited to placement bounds.", var23);
               }

               KetamineClub.LOGGER.info("Initialized Litematica hooks for schematic-aware features.");
            } catch (Throwable var24) {
               disableHooks("initialize", var24);
            }
         }
      }
   }

   @Nullable
   private static Method findMethod(Class<?> cls, String... names) {
      for (String name : names) {
         for (Method m : cls.getMethods()) {
            if (m.getName().equals(name)) {
               return m;
            }
         }

         for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method mx : c.getDeclaredMethods()) {
               if (mx.getName().equals(name)) {
                  return mx;
               }
            }
         }
      }

      return null;
   }

   @Nullable
   private static Field findField(Class<?> cls, String name) {
      try {
         return cls.getField(name);
      } catch (NoSuchFieldException var5) {
         for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
               Field f = c.getDeclaredField(name);
               f.setAccessible(true);
               return f;
            } catch (NoSuchFieldException var4) {
            }
         }

         return null;
      }
   }

   private static String listMethodNames(Class<?> cls) {
      Set<String> names = new TreeSet<>();

      for (Method m : cls.getMethods()) {
         names.add(m.getName());
      }

      for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
         for (Method m : c.getDeclaredMethods()) {
            names.add(m.getName());
         }
      }

      return names.toString();
   }

   private static String listFieldNames(Class<?> cls) {
      Set<String> names = new TreeSet<>();

      for (Field f : cls.getFields()) {
         names.add(f.getName());
      }

      for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
         for (Field f : c.getDeclaredFields()) {
            names.add(f.getName());
         }
      }

      return names.toString();
   }

   private static void disableHooks(String stage, Throwable t) {
      hooksActive = false;
      layerRangeSupported = false;
      KetamineClub.LOGGER.warn("Failed to {} Litematica hooks. Disabling schematic integration.", stage, t);
   }

   private static Class<?> loadRequiredClass(String... candidates) throws ClassNotFoundException {
      Class<?> clazz = loadOptionalClass(candidates);
      if (clazz == null) {
         throw new ClassNotFoundException("Could not find any of the classes " + Arrays.toString((Object[])candidates));
      } else {
         return clazz;
      }
   }

   @Nullable
   private static Class<?> loadOptionalClass(String... candidates) {
      for (String name : candidates) {
         if (name != null && !name.isEmpty()) {
            try {
               return Class.forName(name);
            } catch (ClassNotFoundException var6) {
            }
         }
      }

      return null;
   }
}
