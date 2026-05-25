package net.stones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer; 
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.ItemStackHandler;
import net.stones.item.ClusterJewelItem;

import java.util.ArrayList;
import java.util.List;

public class ClusterJewelRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ClusterJewelRenderer INSTANCE = new ClusterJewelRenderer();

    public ClusterJewelRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();

        poseStack.pushPose();

        // 1. ZENTRIERUNG
        poseStack.translate(0.5D, 0.5D, 0.5D);

        // 2. GUI SKALIERUNG
        boolean isGui = (displayContext == ItemDisplayContext.GUI) || (displayContext == ItemDisplayContext.FIXED);
        if (isGui) {
            float scaleFactor = 0.8f; 
            poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        }

        // 3. BASIS-ITEM RENDERN
        poseStack.pushPose();
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        
        // Hole das korrekte visuelle Modell (z.B. _1 oder _legendary)
        BakedModel modelToRender = getVisualModel(stack, mc);
        
        if (modelToRender != null && !modelToRender.isCustomRenderer()) { // Sicherheitscheck: Nur rendern wenn es Quads hat
            renderModelManual(modelToRender, stack, packedLight, packedOverlay, poseStack, buffer);
        } else {
            // Fallback auf _1 wenn alles schiefgeht
            BakedModel fallback = getFallbackModel(stack, mc);
            if (fallback != null) renderModelManual(fallback, stack, packedLight, packedOverlay, poseStack, buffer);
        }
        
        poseStack.popPose();

        // 4. ORBIT (Atom-Style)
        renderOrbit(stack, poseStack, buffer, packedLight, packedOverlay, itemRenderer);

        poseStack.popPose();
    }

    private BakedModel getVisualModel(ItemStack stack, Minecraft mc) {
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return null;

        String variant = determineVariant(stack); 
        ResourceLocation modelLoc = new ResourceLocation(itemId.getNamespace(), "item/" + itemId.getPath() + "_" + variant);
        
        return mc.getModelManager().getModel(modelLoc);
    }
    
    private BakedModel getFallbackModel(ItemStack stack, Minecraft mc) {
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return null;
        return mc.getModelManager().getModel(new ResourceLocation(itemId.getNamespace(), "item/" + itemId.getPath() + "_1"));
    }

    private String determineVariant(ItemStack stack) {
        int maxSlots = 2;
        
        if (stack.hasTag() && stack.getTag().contains("ClusterStats")) {
            maxSlots = stack.getTag().getCompound("ClusterStats").getInt("SlotCount");
        }

        if (maxSlots == 0) return "1";

        
        if (maxSlots == 5) return "legendary";
        if (maxSlots == 4) return "4";
        if (maxSlots == 3) return "3";
        if (maxSlots == 2) return "2";
        return "1";
    }

    private void renderOrbit(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, ItemRenderer itemRenderer) {
        List<ItemStack> subItems = getClientSubItems(stack);
        if (subItems.isEmpty()) return;

        // Zeitfaktor für die Animation
        float time = (System.currentTimeMillis() % 360000) / 40.0f;
        
        // Verhindert Flackern bei vielen Items, indem wir den Radius leicht variieren oder fixieren
        float radius = 0.55f; 

        for (int i = 0; i < subItems.size(); i++) {
            poseStack.pushPose();
            
            // Aufteilung in 2 Bahnen (Gerade vs Ungerade Indizes)
            boolean orbitTwo = (i % 2 != 0); 

            // -- 1. ORBIT EBENE DEFINIEREN --
            // Hier kippen wir das Koordinatensystem, um die Kreuz-Form zu erhalten.
            
            // Orbit 1: "unten vorne" nach "oben hinten"
            // Orbit 2: "oben vorne" nach "unten hinten"
            // Wir nutzen die X-Achse zum Kippen (vor/zurück) und ein wenig Z-Achse (seitlich) für Tiefe.
            
            float xTilt = orbitTwo ? 45.0f : -45.0f; 
            float zTilt = orbitTwo ? 15.0f : -15.0f; // Leichter seitlicher Tilt für 3D Effekt

            poseStack.mulPose(Axis.ZP.rotationDegrees(zTilt));
            poseStack.mulPose(Axis.XP.rotationDegrees(xTilt));

            // -- 2. ROTATION AUF DER BAHN --
            // Berechne den Winkel auf dem Kreis
            // Wir verteilen die Items gleichmäßig auf 360 Grad, aber geteilt durch Anzahl Items pro Bahn wäre besser.
            // Vereinfacht: Einfach Index * konstanter Abstand.
            float angleOffset = (360.0f / subItems.size()) * i;
            
            // Orbit 2 dreht sich ggf. andersherum für cooleren Effekt?
            // Hier lassen wir beide "vorwärts" laufen, aber da die Bahnen gekippt sind, wirkt es komplex.
            float currentAngle = (time * 2.5f) + angleOffset; 

            poseStack.mulPose(Axis.YP.rotationDegrees(currentAngle));

            // -- 3. VERSCHIEBUNG NACH AUSSEN --
            poseStack.translate(radius, 0, 0);

            // -- 4. GEGEN-ROTATION (Billboard Effekt) --
            // Damit die Items nicht platt auf der Bahn liegen, sondern den Betrachter "anschauen" 
            // oder zumindest aufrecht stehen, müssen wir die Rotationen rückgängig machen.
            
            // Erst die Y-Rotation auf der Bahn zurückdrehen
            poseStack.mulPose(Axis.YP.rotationDegrees(-currentAngle));
            
            // Dann die Tilts in umgekehrter Reihenfolge zurückdrehen
            poseStack.mulPose(Axis.XP.rotationDegrees(-xTilt));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-zTilt));

            // -- 5. RENDERN --
            float electronScale = 0.4f;
            poseStack.scale(electronScale, electronScale, electronScale);

            itemRenderer.renderStatic(
                subItems.get(i), 
                ItemDisplayContext.FIXED, 
                light, 
                overlay, 
                poseStack, 
                buffer, 
                Minecraft.getInstance().level, 
                0
            );
            
            poseStack.popPose();
        }
    }

    private List<ItemStack> getClientSubItems(ItemStack stack) {
        List<ItemStack> items = new ArrayList<>();
        if (stack.hasTag() && stack.getTag().contains("ClusterInventory")) {
            CompoundTag invTag = stack.getTag().getCompound("ClusterInventory");
            ItemStackHandler handler = new ItemStackHandler();
            handler.deserializeNBT(invTag);
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (!s.isEmpty()) items.add(s);
            }
        }
        return items;
    }

    private void renderModelManual(BakedModel model, ItemStack stack, int light, int overlay, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(buffer, RenderType.cutout(), true, stack.hasFoil());
        RandomSource random = RandomSource.create();
        
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            renderQuadList(poseStack, vertexConsumer, model.getQuads(null, direction, random), light, overlay);
        }
        random.setSeed(42L);
        renderQuadList(poseStack, vertexConsumer, model.getQuads(null, null, random), light, overlay);
    }

    private void renderQuadList(PoseStack poseStack, VertexConsumer buffer, List<BakedQuad> quads, int light, int overlay) {
        PoseStack.Pose entry = poseStack.last();
        for (BakedQuad quad : quads) {
            buffer.putBulkData(entry, quad, 1.0F, 1.0F, 1.0F, light, overlay);
        }
    }
}