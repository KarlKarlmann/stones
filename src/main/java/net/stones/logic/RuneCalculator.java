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
import net.stones.data.ShrineInstance.SlotType;
import net.stones.data.ShrineSavedData;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.init.StonesModTags;
import net.stones.item.ClusterJewelItem;
import net.minecraft.resources.ResourceLocation;
import net.stones.item.StoneItem;
import net.stones.network.PacketSyncCombo;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Mathematik hinter den Rune-Boni (Tooltips, Server-System, Action-System).
 * Aktualisiert für periodische Server-Decay-Prüfungen auf Mobs und Spieler-Combos.
 */
public class RuneCalculator {

    public static class CachedMilestone {
        public final RuneEnchantment rune;
        public final int runeLevel;
        public final int socketLevel;
        public final double mult;
        public final ResourceLocation runeId;

        public CachedMilestone(RuneEnchantment rune, int runeLevel, int socketLevel, double mult, ResourceLocation runeId) {
            this.rune = rune;
            this.runeLevel = runeLevel;
            this.socketLevel = socketLevel;
            this.mult = mult;
            this.runeId = runeId;
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

    @FunctionalInterface
    public interface ActiveRuneConsumer {
        void accept(RuneEnchantment rune, int runeLevel, int socketLevel, double multiplier, int mainSlot, int subSlot);
    }

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

    public static double calculateAttributeBonus(RuneEnchantment rune, int runeLevel, int playerLevel, int socketLevel, double amplifyMultiplier) {
        double baseBonus = rune.calculateBonus(runeLevel, playerLevel, socketLevel);
        return baseBonus * amplifyMultiplier;
    }

    public static float calculateStatValue(RuneStat stat, int runeLvl, int sockLvl, int playerLvl, double amplifyMultiplier) {
        return stat.calculate(runeLvl, sockLvl, playerLvl, amplifyMultiplier);
    }

    /**
     * Prüft periodisch im Player-Tick, ob eine Combo abgelaufen ist und bereinigt sie.
     * Ignoriert unendliche Combos (mit Ablaufwert -1) fehlerfrei.
     */
    public static void tickCombos(ServerPlayer player) {
        CompoundTag persist = player.getPersistentData();
        long now = player.level().getGameTime();
        boolean changed = false;
        
        List<String> comboIds = new ArrayList<>();
        for (String key : persist.getAllKeys()) {
            if (key.startsWith("stones_combo_") && key.endsWith("_expire")) {
                String id = key.substring("stones_combo_".length(), key.length() - "_expire".length());
                comboIds.add(id);
            }
        }
        
        for (String id : comboIds) {
            long expire = persist.getLong("stones_combo_" + id + "_expire");
            
            // expire == -1L signalisiert eine unendliche Combo, die nicht von selbst verfällt
            if (expire != -1L && expire > 0L && now >= expire) {
                persist.putFloat("stones_combo_" + id + "_count", 0.0f);
                persist.putLong("stones_combo_" + id + "_expire", 0L);
                persist.remove("stones_combo_" + id + "_max");
                persist.remove("stones_combo_" + id + "_texture");
                persist.remove("stones_combo_" + id + "_size");
                persist.remove("stones_combo_" + id + "_radius");
                persist.remove("stones_combo_" + id + "_speed");
                persist.remove("stones_combo_" + id + "_color");
                
                // Client-HUD-Reset erzwingen
                if (!player.level().isClientSide) {
                    net.minecraftforge.network.PacketDistributor.PacketTarget target = 
                        net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player);
                    net.stones.StonesMod.PACKET_HANDLER.send(target, new net.stones.network.PacketSyncCombo(
                        id, player.getId(), 0, 100, "minecraft:textures/particle/glint.png", 0, 0, 0, 0, 0, 0, 0, 0
                    ));
                }
                changed = true;
            }
        }
        
        if (changed) {
            RuneCalculator.updatePlayer(player);
        }
    }

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

            if (playerLevel < config.requiredLevel) continue;

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
                            if (!subStack.isEmpty() && isRune(subStack) && playerLevel >= getRequiredLevel(subStack)) {
                                processSingleStack(subStack, socketLvl, playerLevel, mainSlotIdx, sub, action);
                            }
                        }
                    }
                }
            } 
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

    public static void updatePlayer(ServerPlayer player) {
        List<CachedMilestone> currentMilestones = new ArrayList<>();
        player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
            UUID shrineId = cap.getLinkedShrine();
            if (shrineId != null) {
                ShrineInstance shrine = ShrineSavedData.get(player.serverLevel()).getShrine(shrineId);
                if (shrine != null) {
                    for (int i = 0; i < shrine.getInventory().getSlots(); i++) {
                        removeAllModifiersFromSlot(player, i);
                    }
                    collectActiveRunes(shrine.getInventory(), shrine.getLayout(), player.experienceLevel, 
                        (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                            if (rune.type == RuneEnchantment.Type.MINOR) {
                                net.stones.advancement.StonesAdvancementHelper.grantAdvancement(player, "power/first_resonance");
                            } else if (rune.type == RuneEnchantment.Type.MAJOR) {
                                net.stones.advancement.StonesAdvancementHelper.grantAdvancement(player, "power/words_of_power");
                            }                             
                            if (mult > 1.0) {
                                net.stones.advancement.StonesAdvancementHelper.grantAdvancement(player, "power/amplified_echo");
                            }
                            
                            if (rune.type == RuneEnchantment.Type.MILESTONE) {
                                ResourceLocation runeId = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                                currentMilestones.add(new CachedMilestone(rune, runeLevel, socketLevel, mult, runeId));
                                net.stones.advancement.StonesAdvancementHelper.grantAdvancement(player, "power/milestone_path");
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
        ACTIVE_MILESTONES.remove(player.getUUID());
        ACTIVE_MILESTONES.put(player.getUUID(), currentMilestones);
    }

    public static List<Component> calculateBonusesLocally(IItemHandler inventory, List<SlotConfig> layout, int playerLevel) {
        List<Component> summary = new ArrayList<>();
        collectActiveRunes(inventory, layout, playerLevel, (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
            if (rune.targetAttribute != null) {
                double bonus = calculateAttributeBonus(rune, runeLevel, playerLevel, socketLevel, mult);
                
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

    public static int getRequiredLevel(ItemStack stack) {
        if (stack.isEmpty()) return 1;

        float totalRequirement = 0.0f;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment companion = entry.getKey();
            int lvl = entry.getValue();

            if (companion instanceof RuneEnchantment rune) {
                totalRequirement += (rune.baseRequiredLevel * lvl);
            } else if (companion instanceof AmplifyEnchantment) {
                totalRequirement += (0.0f * lvl);
            } else if (companion == Enchantments.VANISHING_CURSE) {
                totalRequirement -= 15.0f;
            } else if (companion == Enchantments.BINDING_CURSE) {
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
                    .filter(mod -> mod.getName().equals("Runestone Bonus " + slotIndex) 
                                || mod.getName().startsWith("Runestone Bonus " + slotIndex + "_"))
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