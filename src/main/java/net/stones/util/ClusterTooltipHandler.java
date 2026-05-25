package net.stones.util;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;
import net.stones.enchantment.RuneEnchantment;
import net.stones.logic.RuneCalculator;

import java.util.List;
import java.util.Map;

public class ClusterTooltipHandler {
    
    public static void appendClusterInfo(ItemStack stack, List<Component> tooltip) {
        if (!stack.hasTag() || !stack.getTag().contains("ClusterStats")) {
            tooltip.add(Component.literal("§7[Unidentifiziertes Resonanz-Cluster]").withStyle(ChatFormatting.ITALIC));
            tooltip.add(Component.literal("§8Rechtsklick zum Identifizieren & Öffnen"));
            return;
        }

        CompoundTag stats = stack.getTag().getCompound("ClusterStats");
        int slots = stats.getInt("SlotCount");
        
        tooltip.add(Component.literal("Kapazität: " + slots + " Slots").withStyle(ChatFormatting.BLUE));
        
        // Wir lesen direkt aus dem NBT, da Capabilities auf dem Client manchmal leer sind,
        // wenn sie nicht explizit gesynct wurden. Der ClusterInventoryProvider schreibt aber in "ClusterInventory".
        // Um sicherzugehen, lesen wir das NBT direkt, statt die Capability zu nutzen (Fallback).
        ItemStackHandler handler = new ItemStackHandler();
        if (stack.getTag().contains("ClusterInventory")) {
            handler.deserializeNBT(stack.getTag().getCompound("ClusterInventory"));
        } else {
            // Fallback auf Capability, falls NBT noch nicht geschrieben wurde (Server-Side)
            var cap = stack.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (cap.isPresent()) {
                // Wir können hier nicht direkt casten, also iterieren wir manuell
                // Aber für den Tooltip reicht oft der NBT-Check oben.
            }
        }

        int items = 0;
        int maxReq = 0;
        
        tooltip.add(Component.literal("Inhalt:").withStyle(ChatFormatting.GOLD));
        
        for(int i=0; i < handler.getSlots(); i++) {
            if(i >= slots) break; 
            
            ItemStack s = handler.getStackInSlot(i);
            String type = getSlotType(stats, i);
            
            String typeShort = switch(type) {
                case "MINOR" -> "MN";
                case "MAJOR" -> "MA";
                case "MILESTONE" -> "ML";
                default -> "??";
            };
            
            if(!s.isEmpty()) {
                items++;
                int req = RuneCalculator.getRequiredLevel(s);
                maxReq = Math.max(maxReq, req);
                
                // 1. Name des Items
                tooltip.add(Component.literal(" [" + typeShort + "] ")
                   .withStyle(ChatFormatting.DARK_GRAY)
                   .append(s.getHoverName())
                   .append(Component.literal(" (Lvl " + req + ")").withStyle(ChatFormatting.AQUA)));

                // 2. ENCHANTMENTS AUFLISTEN (Das fehlte dir!)
                Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(s);
                if (!enchants.isEmpty()) {
                    for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                        Enchantment ench = entry.getKey();
                        int lvl = entry.getValue();
                        Component name = ench.getFullname(lvl);
                        
                        // Farbe: Runen grau, andere (Amplify/Vanilla) dunkelviolett
                        ChatFormatting color = (ench instanceof RuneEnchantment) ? ChatFormatting.GRAY : ChatFormatting.DARK_PURPLE;
                        
                        tooltip.add(Component.literal("      - ") 
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(name.copy().withStyle(color)));
                    }
                }

            } else {
                tooltip.add(Component.literal(" [" + typeShort + "] ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("Leer").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        
        int penalty = items * 2;
        int finalReq = maxReq + penalty;
        
        tooltip.add(Component.empty());
        if (items > 0) {
            tooltip.add(Component.literal("Resonanz-Last: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("Level " + finalReq).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
            tooltip.add(Component.literal("(Max Rune " + maxReq + " + Strafe " + penalty + ")").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.literal("Resonanz-Last: 0").withStyle(ChatFormatting.GRAY));
        }
    }
    
    private static String getSlotType(CompoundTag stats, int index) {
        ListTag list = stats.getList("SlotTypes", Tag.TAG_COMPOUND);
        if (index < list.size()) {
            return list.getCompound(index).getString("Type");
        }
        return "?";
    }
}