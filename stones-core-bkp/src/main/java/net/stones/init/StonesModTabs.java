package net.stones.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.stones.StonesMod;

public class StonesModTabs {
    // Registry für Creative Tabs (Minecraft 1.20+)
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StonesMod.MODID);

    // Unser eigener Tab, der in der Creative GUI auftaucht
    public static final RegistryObject<CreativeModeTab> STONES_TAB = REGISTRY.register("stones_tab",
            () -> CreativeModeTab.builder()
                    // Der Name des Tabs (Muss in deiner de_de.json / en_us.json übersetzt werden)
                    .title(Component.translatable("itemGroup.stones"))
                    // Das Icon für den Tab (hier: Der Runenstein)
                    .icon(() -> new ItemStack(StonesModBlocks.RUNESTONE.get()))
                    // Reihenfolge und Inhalt der Items im Tab
                    .displayItems((parameters, output) -> {
                        // Blöcke
                        output.accept(StonesModItems.RUNESTONE.get());
                        // Runen
                        output.accept(StonesModItems.RUNE_MINOR.get());
                        output.accept(StonesModItems.RUNE_MAJOR.get());
                        output.accept(StonesModItems.RUNE_MILESTONE.get());
                        
                        // Cluster Jewels
                        output.accept(StonesModItems.CLUSTER_JEWEL_MINOR.get());
                        output.accept(StonesModItems.CLUSTER_JEWEL_MAJOR.get());
                        output.accept(StonesModItems.CLUSTER_JEWEL_MILESTONE.get());
                    }).build());
}