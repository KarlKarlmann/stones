package net.stones.features;

import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.client.cache.ClientShrineCache;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.logic.RuneCalculator;
import net.stones.network.PacketPerformAction;
import org.lwjgl.glfw.GLFW;
import net.stones.enchantment.behavior.TriggerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ACTION SYSTEM (CLIENT SIDE)
 * Verwaltet die Zuweisung von Fähigkeiten (Runen/Zauber) zu den Tasten R, G und V.
 * Beinhaltet das Rendering für das Inventar-UI und das Tooltip-Management.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
public class ActionSystem {

    public static KeyMapping KEY_ACTION_1, KEY_ACTION_2, KEY_ACTION_3;
    
    // Speicher für die aktuell belegten IDs (Index 1-3)
    private static final String[] CLIENT_SLOTS = new String[]{"", "", "", ""};
    
    // Cache für alle aktuell verfügbaren Aktionen (Runen & via Mixin auch Zauber)
    private static final List<ResourceLocation> CALCULATED_ACTIONS_CACHE = new ArrayList<>();
    
    // Cache für die Level der Aktionen (wichtig für die Tooltip-Generierung)
    private static final Map<ResourceLocation, Integer> ACTION_LEVEL_CACHE = new HashMap<>();

    @Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            String cat = "key.categories.stones";
            KEY_ACTION_1 = new KeyMapping("key.stones.action_1", GLFW.GLFW_KEY_R, cat);
            KEY_ACTION_2 = new KeyMapping("key.stones.action_2", GLFW.GLFW_KEY_G, cat);
            KEY_ACTION_3 = new KeyMapping("key.stones.action_3", GLFW.GLFW_KEY_V, cat);
            event.register(KEY_ACTION_1); 
            event.register(KEY_ACTION_2); 
            event.register(KEY_ACTION_3);
        }
    }

    /**
     * Aktualisiert die Liste der verfügbaren Aktionen basierend auf dem Schrein-Inventar.
     * Wird bei Inventar-Änderungen oder Level-Ups aufgerufen.
     */
    public static void refreshCalculatedActions() {
        CALCULATED_ACTIONS_CACHE.clear();
        ACTION_LEVEL_CACHE.clear(); 
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Sammle alle aktiven Runen aus dem Stones-System
        RuneCalculator.collectActiveRunes(ClientShrineCache.INVENTORY, ClientShrineCache.LAYOUT, mc.player.experienceLevel, 
            (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                if (rune.getBehaviors().stream().anyMatch(b -> b.trigger == TriggerType.ON_ACTION_BUTTON)) {
                    ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                    if (id != null) {
                        if (!CALCULATED_ACTIONS_CACHE.contains(id)) {
                            CALCULATED_ACTIONS_CACHE.add(id);
                        }
                        // Merke dir das höchste Level für korrekte Tooltips
                        ACTION_LEVEL_CACHE.put(id, Math.max(ACTION_LEVEL_CACHE.getOrDefault(id, 0), runeLevel));
                    }
                }
            }
        );

        // --- HIER GREIFT DAS MIXIN DER BRIDGE EIN ---
        // (onRefreshCalculatedActionsBeforeGhostFix wird hier injiziert)

        // 2. Geister-Fix: Entferne belegte IDs, die nicht mehr verfügbar sind
        for (int i = 1; i <= 3; i++) {
            String currentId = CLIENT_SLOTS[i];
            if (!currentId.isEmpty()) {
                ResourceLocation loc = ResourceLocation.tryParse(currentId);
                if (loc == null || !CALCULATED_ACTIONS_CACHE.contains(loc)) {
                    CLIENT_SLOTS[i] = ""; 
                }
            }
        }

        // 3. Auto-Fill: Belege leere Slots automatisch mit neuen Funden
        for (ResourceLocation actionId : CALCULATED_ACTIONS_CACHE) {
            String idStr = actionId.toString();
            boolean isAssigned = false;
            for (int i = 1; i <= 3; i++) {
                if (CLIENT_SLOTS[i].equals(idStr)) {
                    isAssigned = true;
                    break;
                }
            }

            if (!isAssigned) {
                for (int i = 1; i <= 3; i++) {
                    if (CLIENT_SLOTS[i].isEmpty()) {
                        CLIENT_SLOTS[i] = idStr;
                        break; 
                    }
                }
            }
        }
    }

    public static String getClientSlot(int slot) { return CLIENT_SLOTS[slot]; }
    public static List<ResourceLocation> getAvailableActions() { return CALCULATED_ACTIONS_CACHE; }
    
    public static void setSlotRuneId(int slot, String id) { 
        if (slot < 1 || slot > 3) return;
        // Verhindere Duplikate auf den Tasten
        for(int i=1; i<=3; i++) if(CLIENT_SLOTS[i].equals(id)) CLIENT_SLOTS[i] = "";
        CLIENT_SLOTS[slot] = id; 
    }

    public static RuneEnchantment getRuneById(String id) {
        if (id == null || id.isEmpty()) return null;
        var e = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(id));
        return (e instanceof RuneEnchantment r) ? r : null;
    }

    public static ResourceLocation getActionIcon(String id) {
        RuneEnchantment rune = getRuneById(id);
        if (rune != null && rune.getIconPath() != null) {
            return new ResourceLocation(rune.getIconPath());
        }
        return null; // Bridge-Mixin liefert hier ISS-Icons
    }

    /**
     * Liefert die verbleibende Cooldown-Zeit in Sekunden.
     */
    public static int getActionCooldown(String id) {
        ResourceLocation actionLoc = ResourceLocation.tryParse(id);
        if (actionLoc != null && actionLoc.getNamespace().equals(StonesMod.MODID)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var cdEffect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(StonesMod.MODID, "cooldown_" + actionLoc.getPath()));
                if (cdEffect != null) {
                    var cdInstance = mc.player.getEffect(cdEffect);
                    if (cdInstance != null) {
                        return (cdInstance.getDuration() / 20) + 1;
                    }
                }
            }
        }
        return 0; // Bridge-Mixin injiziert hier ISS-Cooldowns
    }

    /**
     * Erstellt die Tooltip-Struktur für eine Aktion.
     * Nutzt Either, um Text und Bilder zu mischen.
     */
    public static List<Either<FormattedText, TooltipComponent>> getActionTooltip(String id) {
        List<Either<FormattedText, TooltipComponent>> tooltip = new ArrayList<>();
        RuneEnchantment rune = getRuneById(id);
        
        if (rune != null) {
            int level = ACTION_LEVEL_CACHE.getOrDefault(new ResourceLocation(id), 1);
            // 1. Name mit römischen Ziffern
            tooltip.add(Either.left((FormattedText) net.minecraft.network.chat.Component.empty().append(rune.getFullname(level)).withStyle(ChatFormatting.GOLD)));
            
            // 2. Beschreibung
            net.minecraft.network.chat.Component desc = rune.getCustomDescription(level);
            if (desc != null && !desc.getString().isEmpty()) {
                tooltip.add(Either.left((FormattedText) net.minecraft.network.chat.Component.empty().append(desc).withStyle(ChatFormatting.GRAY)));
            }
        }
        return tooltip; // Bridge-Mixin klinkt sich für ISS-Zauber ein
    }

    public static String getKeyName(int slot) {
        if (KEY_ACTION_1 == null) return "?"; 
        return switch(slot) {
            case 1 -> KEY_ACTION_1.getTranslatedKeyMessage().getString();
            case 2 -> KEY_ACTION_2.getTranslatedKeyMessage().getString();
            case 3 -> KEY_ACTION_3.getTranslatedKeyMessage().getString();
            default -> "?";
        };
    }

    /**
     * INTEGRATION IN DAS INVENTAR-SCREEN
     */
    @Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
    public static class InventoryIntegration {
        private static int activeMenuSlot = -1;

        @SubscribeEvent
        public static void onInventoryRender(ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof InventoryScreen inv)) return;
            GuiGraphics gui = event.getGuiGraphics();
            int xBase = inv.width / 2 + 95; 
            int yBase = inv.height - 22;

            String tooltipToRender = null; 

            // 1. Zeichne die drei Haupt-Slots
            for (int i = 1; i <= 3; i++) {
                int x = xBase + (i - 1) * 22;
                boolean hovered = event.getMouseX() >= x && event.getMouseX() <= x + 20 && event.getMouseY() >= yBase && event.getMouseY() <= yBase + 20;
                
                gui.fill(x, yBase, x + 20, yBase + 20, hovered ? 0xCC444444 : 0xAA222222);
                gui.renderOutline(x, yBase, 20, 20, activeMenuSlot == i ? 0xFF00FFFF : 0xFFFFFFFF);

                String currentActionId = CLIENT_SLOTS[i];
                if (!currentActionId.isEmpty()) {
                    ResourceLocation icon = getActionIcon(currentActionId);
                    if (icon != null) {
                        gui.blit(icon, x + 2, yBase + 2, 0, 0, 16, 16, 16, 16);
                    }
                    if (hovered) tooltipToRender = currentActionId;
                }

                // 2. Zeichne das Dropdown-Menü
                if (activeMenuSlot == i) {
                    String hoveredSkill = renderSkillList(gui, event.getMouseX(), event.getMouseY(), x, yBase);
                    if (hoveredSkill != null) {
                        tooltipToRender = hoveredSkill;
                    }
                }
            }

            // 3. Render den Tooltip (VIRTUAL ITEM STRATEGY)
            // Wir nutzen ein Dummy-Item, um das Forge-Tooltip-Event sauber zu triggern.
            // Dies ist die einzige Methode, die in jedem Environment kompiliert und
            // die Kontrolle über die Reihenfolge der Komponenten behält.
            if (tooltipToRender != null) {
                ItemStack dummyStack = new ItemStack(Items.PAPER);
                dummyStack.getOrCreateTag().putString("stones_action_tooltip", tooltipToRender);
                
                // Nutzt die sicherste Vanilla-Signatur: gui.renderTooltip(Font, ItemStack, int, int)
                gui.renderTooltip(Minecraft.getInstance().font, dummyStack, event.getMouseX(), event.getMouseY());
            }
        }

        /**
         * Event-Handler für das Dummy-Item.
         * Füllt den Tooltip des virtuellen Items mit den echten Runen-Daten.
         */
        @SubscribeEvent
        public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();
            if (stack.hasTag() && stack.getTag().contains("stones_action_tooltip")) {
                String actionId = stack.getTag().getString("stones_action_tooltip");
                
                // 1. Entferne den Standard-Text ("Paper")
                event.getTooltipElements().clear();
                
                // 2. Füge Runen-Daten hinzu (für normale Stones-Runen)
                List<Either<FormattedText, TooltipComponent>> data = getActionTooltip(actionId);
                event.getTooltipElements().addAll(data);
                
                // Hinweis: Die Bridge-Mod hört ebenfalls auf dieses Event und fügt
                // ihre ISS-Zauberdaten hinzu, falls die ID mit "irons_spellbooks:" beginnt.
                // Da wir die Elemente einfach anfügen, bleiben Bilder (TooltipComponents) am Ende!
            }
        }

        private static String renderSkillList(GuiGraphics gui, int mx, int my, int x, int y) {
            int row = 1;
            String hoveredId = null;
            for (ResourceLocation skillId : CALCULATED_ACTIONS_CACHE) {
                int sy = y - (row * 20) - 2;
                boolean h = mx >= x && mx <= x + 18 && my >= sy && my <= sy + 18;
                
                gui.fill(x, sy, x + 18, sy + 18, h ? 0xDD666666 : 0xDD333333);
                
                ResourceLocation icon = getActionIcon(skillId.toString());
                if (icon != null) {
                    gui.blit(icon, x + 1, sy + 1, 0, 0, 16, 16, 16, 16);
                }
                
                if (h) hoveredId = skillId.toString();
                row++;
            }
            return hoveredId;
        }

        @SubscribeEvent
        public static void onInventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
            if (!(event.getScreen() instanceof InventoryScreen inv)) return;
            int xBase = inv.width / 2 + 95; 
            int yBase = inv.height - 22;

            if (activeMenuSlot != -1) {
                int row = 1;
                for (ResourceLocation skillId : CALCULATED_ACTIONS_CACHE) {
                    int sy = yBase - (row * 20) - 2;
                    int sx = xBase + (activeMenuSlot - 1) * 22;
                    if (event.getMouseX() >= sx && event.getMouseX() <= sx + 18 && event.getMouseY() >= sy && event.getMouseY() <= sy + 18) {
                        setSlotRuneId(activeMenuSlot, skillId.toString());
                        activeMenuSlot = -1;
                        return;
                    }
                    row++;
                }
            }

            for (int i = 1; i <= 3; i++) {
                int x = xBase + (i - 1) * 22;
                if (event.getMouseX() >= x && event.getMouseX() <= x + 20 && event.getMouseY() >= yBase && event.getMouseY() <= yBase + 20) {
                    activeMenuSlot = (activeMenuSlot == i) ? -1 : i;
                    return;
                }
            }
            activeMenuSlot = -1;
        }
    }

    @Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
    public static class InputHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.player == null) return;
            
            if (KEY_ACTION_1.consumeClick()) trigger(1);
            if (KEY_ACTION_2.consumeClick()) trigger(2);
            if (KEY_ACTION_3.consumeClick()) trigger(3);
        }
    }

    public static void trigger(int slot) {
        String id = CLIENT_SLOTS[slot];
        if (id != null && !id.isEmpty()) {
            StonesMod.PACKET_HANDLER.sendToServer(new PacketPerformAction(id, slot));
        }
    }
}