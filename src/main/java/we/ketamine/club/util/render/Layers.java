package we.ketamine.club.util.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import we.ketamine.club.mixin.render.RenderTypeAccessor;

public class Layers {
   private static final Layers.Layer GLOBAL_QUADS = create("global_fill", RenderSetup.builder(Pipelines.GLOBAL_QUADS_PIPELINE).createRenderSetup());
   private static final Layers.Layer GLOBAL_LINES = create("global_lines", RenderSetup.builder(Pipelines.GLOBAL_LINES_PIPELINE).createRenderSetup());

   public Layers() {
   }

   public static Layers.Layer quads() {
      return GLOBAL_QUADS;
   }

   public static Layers.Layer lines() {
      return GLOBAL_LINES;
   }

   static Layers.Layer create(String name, RenderSetup setup) {
      return new Layers.Layer(name, RenderTypeAccessor.ketamineclub$create(name, setup));
   }

   public static final class Layer {
      private final String name;
      private final RenderType renderType;

      private Layer(String name, RenderType renderType) {
         this.name = name;
         this.renderType = renderType;
      }

      public void draw(MeshData mesh) {
         if (mesh != null) {
            MeshData var2 = mesh;

            label112: {
               label113: {
                  try {
                     DrawState drawState = mesh.drawState();
                     if (drawState.indexCount() != 0
                        && this.renderType.format().equals(drawState.format())
                        && this.renderType.primitiveTopology() == drawState.primitiveTopology()
                        && RenderSystem.isOnRenderThread()) {
                        GpuDevice device = RenderSystem.tryGetDevice();
                        if (device == null) {
                           break label112;
                        }

                        GpuBuffer vertexBuffer = null;
                        GpuBuffer ownedIndexBuffer = null;

                        try {
                           vertexBuffer = device.createBuffer(() -> this.name + " vertices", 32, mesh.vertexBuffer());
                           ByteBuffer indexData = mesh.indexBuffer();
                           if (indexData != null) {
                              ownedIndexBuffer = device.createBuffer(() -> this.name + " indices", 64, indexData);
                              this.renderType.prepare().drawFromBuffer(vertexBuffer, ownedIndexBuffer, drawState.indexType(), 0, 0, drawState.indexCount());
                           } else {
                              AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());
                              this.renderType
                                 .prepare()
                                 .drawFromBuffer(vertexBuffer, sequential.getBuffer(drawState.indexCount()), sequential.type(), 0, 0, drawState.indexCount());
                           }
                           break label113;
                        } finally {
                           releaseAfterDraw(vertexBuffer, ownedIndexBuffer);
                        }
                     }
                  } catch (Throwable var15) {
                     if (mesh != null) {
                        try {
                           var2.close();
                        } catch (Throwable var13) {
                           var15.addSuppressed(var13);
                        }
                     }

                     throw var15;
                  }

                  if (mesh != null) {
                     mesh.close();
                  }

                  return;
               }

               if (mesh != null) {
                  mesh.close();
               }

               return;
            }

            if (mesh != null) {
               mesh.close();
            }
         }
      }

      void draw(StagedVertexBuffer stagedBuffer, Draw draw) {
         if (RenderSystem.isOnRenderThread()) {
            ExecuteInfo executeInfo = stagedBuffer.getExecuteInfo(draw);
            if (executeInfo != null) {
               this.renderType.prepare().drawFromBuffer(executeInfo);
            }
         }
      }

      private static void releaseAfterDraw(GpuBuffer vertexBuffer, GpuBuffer indexBuffer) {
         if (vertexBuffer != null || indexBuffer != null) {
            try {
               RenderSystem.queueFencedTask(() -> closeBuffers(vertexBuffer, indexBuffer));
            } catch (Throwable var3) {
               closeBuffers(vertexBuffer, indexBuffer);
            }
         }
      }

      private static void closeBuffers(GpuBuffer vertexBuffer, GpuBuffer indexBuffer) {
         try {
            if (vertexBuffer != null) {
               vertexBuffer.close();
            }
         } finally {
            if (indexBuffer != null) {
               indexBuffer.close();
            }
         }
      }
   }
}
