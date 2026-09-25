package net.stones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import net.stones.client.renderer.combo.ClientComboRenderer;
import net.stones.entity.StonesProjectileEntity;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StonesProjectileRenderer extends EntityRenderer<StonesProjectileEntity> {

    private final ItemRenderer itemRenderer;
    private final BlockRenderDispatcher blockRenderer;
    private final ModelBlockRenderer rawModelRenderer;
    
    // CACHE: Speichert die ermittelte Render-Strategie EINMALIG pro ID. Zero-Allocation im Loop!
    private final Map<String, RenderStrategy> strategyCache = new ConcurrentHashMap<>();

    @FunctionalInterface
    private interface RenderStrategy {
        void render(StonesProjectileEntity entity, float yaw, float pitch, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight);
    }

    public StonesProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.rawModelRenderer = context.getBlockRenderDispatcher().getModelRenderer();
    }

    @Override
    public void render(StonesProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        String mode = entity.getRenderMode();

        if ("MODEL".equals(mode)) {
            String texKey = entity.getTextureData();

            float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

            // Holt die Strategie aus dem Cache (O(1)) oder löst sie beim 1. Aufruf einmalig auf
            RenderStrategy strategy = this.strategyCache.computeIfAbsent(texKey, id -> resolveStrategy(id));
            strategy.render(entity, yaw, pitch, partialTicks, poseStack, buffer, packedLight);

        } else if ("BILLBOARD".equals(mode)) {
            ResourceLocation texture = ClientComboRenderer.resolveTexture(entity.getTextureData());
            VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(texture));

            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

            Matrix4f matrix = poseStack.last().pose();
            float half = 0.5f;
            int light = 15728880;

            builder.vertex(matrix, -half, -half, 0).color(1f, 1f, 1f, 0.9f).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
            builder.vertex(matrix, -half,  half, 0).color(1f, 1f, 1f, 0.9f).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
            builder.vertex(matrix,  half,  half, 0).color(1f, 1f, 1f, 0.9f).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
            builder.vertex(matrix,  half, -half, 0).color(1f, 1f, 1f, 0.9f).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * Wird NUR EINMALIG beim ersten Erscheinen einer ID aufgerufen.
     */
    private RenderStrategy resolveStrategy(String id) {
        ResourceLocation resLoc = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("minecraft", id);

        // STUFE 1: ENTITIES (Vanilla, Mobs, GeckoLib, AzureLib)
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(resLoc)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(resLoc);
            Entity dummy = type.create(Minecraft.getInstance().level);
            if (dummy != null) {
                return (entity, yaw, pitch, partialTicks, poseStack, buffer, packedLight) -> {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
                    
                    dummy.tickCount = entity.tickCount;
                    EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                    dispatcher.render(dummy, 0.0D, 0.0D, 0.0D, yaw, partialTicks, poseStack, buffer, packedLight);
                    poseStack.popPose();
                };
            }
        }

        // STUFE 2: ITEMS (Waffen, Schwerter, 3D-Items)
        if (BuiltInRegistries.ITEM.containsKey(resLoc)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(resLoc));
            if (!stack.isEmpty()) {
                return (entity, yaw, pitch, partialTicks, poseStack, buffer, packedLight) -> {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
                    poseStack.scale(1.2f, 1.2f, 1.2f);

                    this.itemRenderer.renderStatic(
                        stack,
                        ItemDisplayContext.FIXED,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        buffer,
                        entity.level(),
                        entity.getId()
                    );
                    poseStack.popPose();
                };
            }
        }

        // STUFE 3: BLÖCKE (TNT, Amboss, 3D-Blöcke)
        if (BuiltInRegistries.BLOCK.containsKey(resLoc)) {
            BlockState state = BuiltInRegistries.BLOCK.get(resLoc).defaultBlockState();
            return (entity, yaw, pitch, partialTicks, poseStack, buffer, packedLight) -> {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
                poseStack.translate(-0.5, -0.5, -0.5);

                this.blockRenderer.renderSingleBlock(
                    state,
                    poseStack,
                    buffer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            };
        }

        // STUFE 4: RAW BAKED MODELS (Reine JSON-3D-Modelle ohne Wrapper)
        ModelManager modelManager = Minecraft.getInstance().getModelManager();
        ModelResourceLocation modelLoc = new ModelResourceLocation(resLoc, "inventory");
        BakedModel bakedModel = modelManager.getModel(modelLoc);
        if (bakedModel == modelManager.getMissingModel()) {
            bakedModel = modelManager.getModel(resLoc);
        }

        if (bakedModel != null && bakedModel != modelManager.getMissingModel()) {
            BakedModel finalModel = bakedModel;
            return (entity, yaw, pitch, partialTicks, poseStack, buffer, packedLight) -> {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
                poseStack.scale(1.2f, 1.2f, 1.2f);

                RenderType renderType = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
                VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

                this.rawModelRenderer.renderModel(
                    poseStack.last(),
                    vertexConsumer,
                    null,
                    finalModel,
                    1.0f, 1.0f, 1.0f,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY,
                    renderType
                );
                poseStack.popPose();
            };
        }

        // Leeres Fallback-Dummy
        return (entity, yaw, pitch, partialTicks, poseStack, buffer, packedLight) -> {};
    }

    @Override
    public ResourceLocation getTextureLocation(StonesProjectileEntity entity) {
        return ClientComboRenderer.resolveTexture(entity.getTextureData());
    }
}