package net.stones.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineInstance.SlotConfig;
import net.stones.data.ShrineSavedData;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.init.StonesModTags;
import net.stones.item.ClusterJewelItem;
import net.stones.item.StoneItem;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MATHEMATISCHE SINGLE SOURCE OF TRUTH.
 * Diese Klasse berechnet ausschließlich Werte. Sie ist die einzige Autorität für die
 * Resonanz-Mathematik und wird von Tooltips, dem Server-System und dem Action-System genutzt.
 */
public class RuneCalculator {

    public static class CachedMilestone {
        public final RuneEnchantment rune;
        public final int runeLevel;
        public final int socketLevel;
        public final double mult;

        public CachedMilestone(RuneEnchantment rune, int runeLevel, int socketLevel, double mult) {
            this.rune = rune;
            this.runeLevel = runeLevel;
            this.socketLevel = socketLevel;
            this.mult = mult;
        }
    }
	
	public static final Map<UUID, List<CachedMilestone>> ACTIVE_MILESTONES = new HashMap<>();
	
	
    public static List<CachedMilestone> getActiveMilestones(ServerPlayer player) {
        return ACTIVE_MILESTONES.getOrDefault(player.getUUID(), new ArrayList<>());
    }
    private static UUID getUniqueModifierID(int mainSlot, int subSlot, String attributeName) {
        String seed = "Runestone_" + mainSlot + "_" + subSlot + "_" + attributeName;
        return UUID.nameUUIDFromBytes(seed.getBytes());
    }

    // --- NEU: Consumer Interface für zentrale Logik ---
    @FunctionalInterface
    public interface ActiveRuneConsumer {
        void accept(RuneEnchantment rune, int runeLevel, int socketLevel, double multiplier, int mainSlot, int subSlot);
    }

    /**
     * Ermittelt den aktuellen Verstärkungs-Multiplikator eines ItemStacks.
     * Nutzt Instance-Check um Registry-Probleme zu umgehen.
     */
    public static double getAmplifyMultiplier(ItemStack stack) {
        int lvl = 0;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof AmplifyEnchantment) {
                lvl = entry.getValue();
                break;
            }
        }
        return AmplifyEnchantment.getMultiplier(lvl);
    }

    /**
     * Zentralisierte Berechnung für Attribut-Boni (Minor/Major Runen).
     */
    public static double calculateAttributeBonus(RuneEnchantment rune, int runeLevel, int playerLevel, int socketLevel, double amplifyMultiplier) {
        double baseBonus = rune.calculateBonus(runeLevel, playerLevel, socketLevel);
        return baseBonus * amplifyMultiplier;
    }

    /**
     * Zentralisierte Berechnung für dynamische Milestone-Werte.
     */
    public static float calculateStatValue(RuneStat stat, int runeLvl, int sockLvl, int playerLvl, double amplifyMultiplier) {
        return stat.calculate(runeLvl, sockLvl, playerLvl, amplifyMultiplier);
    }

    /**
     * NEU: Zentrale Methode zum Sammeln aller aktiven Runen (inkl. Cluster).
     * Wird vom GUI und vom Server genutzt.
     * FIX: Prüft nun auch die Level-Anforderung der Rune selbst, nicht nur des Slots.
     */
    public static void collectActiveRunes(IItemHandler inventory, List<SlotConfig> layout, int playerLevel, ActiveRuneConsumer action) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            SlotConfig config = null;
            for (SlotConfig c : layout) {
                if (c.inventoryIndex == i) {
                    config = c;
                    break;
                }
            }
            if (config == null) continue;

            // 1. Slot-Anforderung prüfen
            if (playerLevel < config.requiredLevel) continue;

            // A: Cluster Jewel Logic
            if (stack.getItem() instanceof ClusterJewelItem) {
                final int mainSlotIdx = i;
                final int socketLvl = config.requiredLevel;
                
                IItemHandler activeHandler = null;
                if (stack.hasTag() && stack.getTag().contains("ClusterInventory")) {
                    CompoundTag invTag = stack.getTag().getCompound("ClusterInventory");
                    ItemStackHandler nbtHandler = new ItemStackHandler();
                    nbtHandler.deserializeNBT(invTag);
                    activeHandler = nbtHandler;
                } 
                else if (stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
                    activeHandler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                }

                if (activeHandler != null) {
                    int clusterReq = calculateClusterRequirement(activeHandler);
                    if (playerLevel >= clusterReq) {
                        for (int sub = 0; sub < activeHandler.getSlots(); sub++) {
                            ItemStack subStack = activeHandler.getStackInSlot(sub);
                            // FIX: Prüfe Rune-Anforderung im Cluster
                            if (!subStack.isEmpty() && isRune(subStack) && playerLevel >= getRequiredLevel(subStack)) {
                                processSingleStack(subStack, socketLvl, playerLevel, mainSlotIdx, sub, action);
                            }
                        }
                    }
                }
            } 
            // B: Standard Rune Logic
            // FIX: Prüfe Rune-Anforderung direkt
            else if (isRune(stack) && playerLevel >= getRequiredLevel(stack)) {
                processSingleStack(stack, config.requiredLevel, playerLevel, i, -1, action);
            }
        }
    }

    private static void processSingleStack(ItemStack stack, int socketLvl, int playerLvl, int mainSlot, int subSlot, ActiveRuneConsumer action) {
        double mult = getAmplifyMultiplier(stack);
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment rune) {
                action.accept(rune, entry.getValue(), socketLvl, mult, mainSlot, subSlot);
            }
        }
    }

    /**
     * Wendet alle aktiven Schrein-Effekte auf den Spieler an (Server-Side).
     */
    public static void updatePlayer(ServerPlayer player) {
        // 1. Liste GANZ OBEN initialisieren, damit sie IMMER existiert
        List<CachedMilestone> currentMilestones = new ArrayList<>();

        player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
            UUID shrineId = cap.getLinkedShrine();
            if (shrineId != null) {
                ShrineInstance shrine = ShrineSavedData.get(player.serverLevel()).getShrine(shrineId);
                if (shrine != null) {
                    // 1. Alle alten Modifier entfernen
                    for (int i = 0; i < shrine.getInventory().getSlots(); i++) {
                        removeAllModifiersFromSlot(player, i);
                    }

                    // 2. Neue Modifier berechnen und anwenden
                    collectActiveRunes(shrine.getInventory(), shrine.getLayout(), player.experienceLevel, 
                        (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                            
                            // KORREKTUR: Milestones mit dem kompletten Kontext in den Cache packen!
                            if (rune.type == RuneEnchantment.Type.MILESTONE) {
                                currentMilestones.add(new CachedMilestone(rune, runeLevel, socketLevel, mult));
                            }

                            if (rune.targetAttribute != null) {
                                double bonus = calculateAttributeBonus(rune, runeLevel, player.experienceLevel, socketLevel, mult);
                                if (bonus != 0) {
                                    AttributeInstance inst = player.getAttribute(rune.targetAttribute);
                                    if (inst != null) {
                                        UUID modifierId = getUniqueModifierID(mainSlot, subSlot, rune.targetAttribute.getDescriptionId());
                                        String modName = "Runestone Bonus " + mainSlot + (subSlot >= 0 ? "_" + subSlot : "");
                                        AttributeModifier mod = new AttributeModifier(modifierId, modName, bonus, rune.operation);
                                        if (!inst.hasModifier(mod)) inst.addTransientModifier(mod);
                                    }
                                }
                            }
                        }
                    );
                }
            }
        });

        // 3. Cache IMMER aktualisieren! Auch wenn der Schrein null ist.
        // Wenn der Schrein fehlt (z.B. abgebaut), ist die Liste leer und der Cache wird fehlerfrei bereinigt.
            ACTIVE_MILESTONES.remove(player.getUUID());
            ACTIVE_MILESTONES.put(player.getUUID(), currentMilestones);
    }

    /**
     * NEU: Berechnet die Boni lokal auf dem Client ohne Entity-Bezug.
     * Ermöglicht dem Client, die Toasts selbst zu generieren (Mirror-Modell).
     */
    public static List<Component> calculateBonusesLocally(IItemHandler inventory, List<SlotConfig> layout, int playerLevel) {
        List<Component> summary = new ArrayList<>();
        collectActiveRunes(inventory, layout, playerLevel, (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
            if (rune.targetAttribute != null) {
                double bonus = calculateAttributeBonus(rune, runeLevel, playerLevel, socketLevel, mult);
                
                // FORMATIERUNG ANPASSUNG für saubere Toasts:
                // Statt StoneItem.formatAttributeLine (das "Active Bonus" schreibt), formatieren wir direkt.
                boolean amplified = mult > 1.0;
                String sign = bonus >= 0 ? "+" : "";
                String valStr = (rune.operation != AttributeModifier.Operation.ADDITION) 
                    ? String.format("%.1f%%", bonus * 100) 
                    : String.format("%.1f", bonus);
                
                MutableComponent line = Component.literal(sign + valStr).withStyle(amplified ? ChatFormatting.AQUA : ChatFormatting.GOLD);
                line.append(" ");
                line.append(Component.translatable(rune.targetAttribute.getDescriptionId()).withStyle(ChatFormatting.WHITE));
                
                if (amplified) {
                    line.append(Component.literal(" ✦").withStyle(ChatFormatting.AQUA));
                }
                
                summary.add(line);

            } else if (rune.type == RuneEnchantment.Type.MILESTONE) {
                summary.add(Component.literal(" ➤ ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(rune.getFullname(runeLevel).copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal(" (Aktiv)").withStyle(ChatFormatting.GRAY))
                );
            }
        });
        return summary;
    }

    /**
     * Berechnet die Anforderungen für ein Cluster Jewel.
     */
    private static int calculateClusterRequirement(IItemHandler handler) {
        int maxLvl = 0;
        int count = 0;
        for(int i=0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if(!s.isEmpty()) {
                count++;
                maxLvl = Math.max(maxLvl, getRequiredLevel(s));
            }
        }
        return maxLvl + (count * 2);
    }

    /**
     * Berechnet die Levelanforderung eines Steins.
     */
    public static int getRequiredLevel(ItemStack stack) {
        if (stack.isEmpty()) return 1;

        float totalRequirement = 0.0f;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int lvl = entry.getValue();

            if (ench instanceof RuneEnchantment rune) {
                totalRequirement += (rune.baseRequiredLevel * lvl);
            } else if (ench instanceof AmplifyEnchantment) {
                totalRequirement += (0.0f * lvl);
            } else if (ench == Enchantments.VANISHING_CURSE) {
                totalRequirement -= 15.0f;
            } else if (ench == Enchantments.BINDING_CURSE) {
                totalRequirement -= 10.0f;
            }
        }

        return Math.max(1, (int) Math.ceil(totalRequirement));
    }

    private static void removeAllModifiersFromSlot(Player player, int slotIndex) {
        ForgeRegistries.ATTRIBUTES.getValues().forEach(attr -> {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                inst.getModifiers().stream()
                    .filter(mod -> mod.getName().startsWith("Runestone Bonus " + slotIndex))
                    .toList()
                    .forEach(inst::removeModifier);
            }
        });
    }
    
    private static boolean isRune(ItemStack stack) {
        return stack.is(StonesModTags.RUNE_MINOR) || 
               stack.is(StonesModTags.RUNE_MAJOR) || 
               stack.is(StonesModTags.RUNE_MILESTONE);
    }
}