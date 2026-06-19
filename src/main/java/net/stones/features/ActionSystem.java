package net.stones.features;

import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.client.cache.ClientShrineCache;
import net.stones.client.integration.ActionbarCompat;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.behavior.TriggerType;
import net.stones.logic.RuneCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BACKEND FÜR DIE ACTIONBAR MOD (CLIENT SIDE) - CRASH SICHER
 * Hat absolut KEINE direkten Importe zur Actionbar-Mod!
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ActionSystem {

    // --- SHADOW TARGETS FÜR MIXINS ---
    private static final List<ResourceLocation> CALCULATED_ACTIONS_CACHE = new ArrayList<>();
    private static final Map<ResourceLocation, Integer> ACTION_LEVEL_CACHE = new HashMap<>();
    private static final String[] CLIENT_SLOTS = new String[]{"", "", "", ""};
    
    public static boolean isSyncingWithActionbar = false;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // SICHERHEITSCHECK: Nur wenn die Actionbar installiert ist, laden wir die Brücke!
            if (ModList.get().isLoaded("actionbar")) {
                ActionbarCompat.register();
            }
        });
    }

    public static void refreshCalculatedActions() {
        CALCULATED_ACTIONS_CACHE.clear();
        ACTION_LEVEL_CACHE.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RuneCalculator.collectActiveRunes(ClientShrineCache.INVENTORY, ClientShrineCache.LAYOUT, mc.player.experienceLevel, 
                (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                    if (rune.getBehaviors().stream().anyMatch(b -> b.trigger == TriggerType.ON_ACTION_BUTTON)) {
                        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                        if (id != null) {
                            if (!CALCULATED_ACTIONS_CACHE.contains(id)) CALCULATED_ACTIONS_CACHE.add(id);
                            ACTION_LEVEL_CACHE.put(id, Math.max(ACTION_LEVEL_CACHE.getOrDefault(id, 0), runeLevel));
                        }
                    }
                }
            );
        }

        // --- MIXIN ANKER (Ganz wichtig für die Bridge Mod) ---
        // Der Mixin sucht nach dem Zugriff auf CLIENT_SLOTS.
        for (int i = 1; i <= 3; i++) {
            String currentId = CLIENT_SLOTS[i]; 
            if (currentId != null) currentId.trim(); // Dummy read
        }

        // Actionbar aktualisieren, FALLS sie installiert ist
        if (!isSyncingWithActionbar && ModList.get().isLoaded("actionbar")) {
            isSyncingWithActionbar = true;
            ActionbarCompat.refresh();
            isSyncingWithActionbar = false;
        }
    }

    public static ResourceLocation getActionIcon(String idStr) {
        // Der @Inject(at = @At("HEAD")) der Bridge Mod schaltet sich vor diese Zeile
        RuneEnchantment rune = getRuneById(idStr);
        if (rune != null && rune.getIconPath() != null) return new ResourceLocation(rune.getIconPath());
        return null; 
    }

    public static int getActionCooldown(String id) {
        // Der @Inject(at = @At("HEAD")) der Bridge Mod schaltet sich vor diese Zeile
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation actionLoc = ResourceLocation.tryParse(id);
        if (actionLoc != null && actionLoc.getNamespace().equals(StonesMod.MODID) && mc.player != null) {
            var cdEffect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(StonesMod.MODID, "cooldown_" + actionLoc.getPath()));
            if (cdEffect != null) {
                var cdInstance = mc.player.getEffect(cdEffect);
                if (cdInstance != null) return (cdInstance.getDuration() / 20) + 1;
            }
        }
        return 0; 
    }

    public static List<Either<FormattedText, TooltipComponent>> getActionTooltip(String id) {
        // Der @Inject(at = @At("HEAD")) der Bridge Mod schaltet sich vor diese Zeile
        List<Either<FormattedText, TooltipComponent>> tooltip = new ArrayList<>();
        RuneEnchantment rune = getRuneById(id);
        if (rune != null) {
            int level = ACTION_LEVEL_CACHE.getOrDefault(new ResourceLocation(id), 1);
            tooltip.add(Either.left((FormattedText) Component.empty().append(rune.getFullname(level)).withStyle(ChatFormatting.GOLD)));
            Component desc = rune.getCustomDescription(level);
            if (desc != null && !desc.getString().isEmpty()) tooltip.add(Either.left((FormattedText) Component.empty().append(desc).withStyle(ChatFormatting.GRAY)));
            return tooltip;
        }
        return null; 
    }

    public static RuneEnchantment getRuneById(String id) {
        if (id == null || id.isEmpty()) return null;
        var e = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(id));
        return (e instanceof RuneEnchantment r) ? r : null;
    }

    // --- GETTER FÜR DIE INTEGRATION ---
    public static List<ResourceLocation> getCalculatedActionsCache() { return CALCULATED_ACTIONS_CACHE; }
    public static Map<ResourceLocation, Integer> getActionLevelCache() { return ACTION_LEVEL_CACHE; }
}