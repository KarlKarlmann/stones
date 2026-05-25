package net.stones.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.stones.cap.ClusterInventoryProvider;
import net.stones.client.renderer.ClusterJewelRenderer;
import net.stones.util.ClusterTooltipHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ClusterJewelItem extends StoneItem {

    public ClusterJewelItem(Type type, Properties props) {
        super(type); 
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ClusterJewelRenderer.INSTANCE;
            }
        });
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ClusterInventoryProvider(stack);
    }

    // --- BUNDLE INTERAKTION ---
@Override
public boolean overrideOtherStackedOnMe(
        ItemStack cluster,
        ItemStack cursorStack,
        Slot slot,
        ClickAction action,
        Player player,
        SlotAccess access
) {
    if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
        return false;
    }

    ensureInitialized(cluster, player.level().random);

    if (cursorStack.isEmpty()) {
        extractLastItem(cluster, access, player);
    } else {
        insertItem(cluster, cursorStack, player);
    }

    return true; // <- DAS ist der entscheidende Punkt
}


    /**
     * Fall 2: Man klickt mit dem Cluster-Juwel (Cursor) auf einen Slot.
     * - Slot hat Item: Versuche Item vom Slot ins Cluster zu ziehen.
     * - Slot leer: Versuche Item aus Cluster in den Slot zu legen.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack cluster, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }

        ensureInitialized(cluster, player.level().random);

        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) {
            // IN SLOT LEGEN
            return extractLastItemToSlot(cluster, slot, player);
        } else {
            // VOM SLOT NEHMEN
            if (slot.mayPickup(player)) {
                ItemStack remainder = insertItemResult(cluster, slotStack, player);
                // Wenn wir etwas nehmen konnten, updaten wir den Slot
                if (remainder.getCount() != slotStack.getCount()) {
                    slot.set(remainder);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean insertItem(ItemStack cluster, ItemStack toInsert, Player player) {
        ItemStack remainder = insertItemResult(cluster, toInsert, player);
        if (remainder.getCount() != toInsert.getCount()) {
            // Es wurde etwas eingefügt -> Cursor Stack updaten
            toInsert.setCount(remainder.getCount());
            return true;
        }
        return false;
    }

    private ItemStack insertItemResult(ItemStack cluster, ItemStack toInsert, Player player) {
        AtomicBoolean success = new AtomicBoolean(false);
        
        cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            // Wir versuchen, den Stack in jeden Slot einzufügen, bis er weg ist
            ItemStack remaining = toInsert.copy();
            
            for (int i = 0; i < handler.getSlots(); i++) {
                if (remaining.isEmpty()) break;
                // insertItem handhabt Validierung (StoneItem only) und Limits (1 pro Slot) automatisch
                remaining = handler.insertItem(i, remaining, false);
            }

            if (remaining.getCount() < toInsert.getCount()) {
                playInsertSound(player);
                success.set(true);
            }
            
            // Rückgabe des Rest-Stacks für das aufrufende System
            // WICHTIG: Das Original toInsert darf hier nicht direkt modifiziert werden, 
            // sondern das Ergebnis muss zurückgegeben werden.
            toInsert.setCount(remaining.getCount());
        });
        
        return toInsert;
    }

    private boolean extractLastItem(ItemStack cluster, SlotAccess access, Player player) {
        return cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
            // Rückwärts suchen (LIFO)
            for (int i = handler.getSlots() - 1; i >= 0; i--) {
                ItemStack extracted = handler.extractItem(i, 64, false);
                if (!extracted.isEmpty()) {
                    access.set(extracted);
                    playRemoveSound(player);
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean extractLastItemToSlot(ItemStack cluster, Slot targetSlot, Player player) {
        return cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).map(handler -> {
            for (int i = handler.getSlots() - 1; i >= 0; i--) {
                // Simuliere Extraktion
                ItemStack simulated = handler.extractItem(i, 64, true);
                if (!simulated.isEmpty() && targetSlot.mayPlace(simulated)) {
                    // Echte Extraktion
                    ItemStack extracted = handler.extractItem(i, 64, false);
                    targetSlot.set(extracted);
                    playRemoveSound(player);
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean tryInsertFromCursor(ItemStack cluster, ItemStack cursorStack, Player player) {
        AtomicBoolean success = new AtomicBoolean(false);
        cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, cursorStack, false);
            if (remaining.getCount() < cursorStack.getCount()) {
                playInsertSound(player);
                cursorStack.setCount(remaining.getCount());
                success.set(true);
            }
        });
        return success.get();
    }

    private boolean tryInsertFromSlot(ItemStack cluster, Slot slot, Player player) {
        if (!slot.mayPickup(player)) return false;
        ItemStack slotStack = slot.getItem();
        AtomicBoolean success = new AtomicBoolean(false);
        cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, slotStack, true);
            if (remaining.getCount() < slotStack.getCount()) {
                ItemStack finalRemaining = ItemHandlerHelper.insertItemStacked(handler, slotStack, false);
                slot.set(finalRemaining);
                playInsertSound(player);
                success.set(true);
            }
        });
        return success.get();
    }

    private boolean tryExtractToCursor(ItemStack cluster, SlotAccess cursorAccess, Player player) {
        AtomicBoolean success = new AtomicBoolean(false);
        cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (int i = handler.getSlots() - 1; i >= 0; i--) {
                ItemStack extracted = handler.extractItem(i, 64, false);
                if (!extracted.isEmpty()) {
                    cursorAccess.set(extracted);
                    playRemoveSound(player);
                    success.set(true);
                    return;
                }
            }
        });
        return success.get();
    }

    private boolean tryExtractToSlot(ItemStack cluster, Slot targetSlot, Player player) {
        AtomicBoolean success = new AtomicBoolean(false);
        cluster.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (int i = handler.getSlots() - 1; i >= 0; i--) {
                ItemStack extractedSim = handler.extractItem(i, 64, true);
                if (!extractedSim.isEmpty() && targetSlot.mayPlace(extractedSim)) {
                    ItemStack extracted = handler.extractItem(i, 64, false);
                    targetSlot.set(extracted);
                    playRemoveSound(player);
                    success.set(true);
                    return;
                }
            }
        });
        return success.get();
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8f, 0.8f + entity.level().getRandom().nextFloat() * 0.4f);
    }

    private void playRemoveSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 0.8f + entity.level().getRandom().nextFloat() * 0.4f);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
            .map(h -> {
                int filled = 0;
                int max = 3;
                if (stack.hasTag() && stack.getTag().contains("ClusterStats")) {
                    max = stack.getTag().getCompound("ClusterStats").getInt("SlotCount");
                }
                for(int i=0; i<max; i++) if(!h.getStackInSlot(i).isEmpty()) filled++;
                return Math.round((float)filled * 13.0F / (float)max);
            }).orElse(0);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00FFFF; 
    }

    private void ensureInitialized(ItemStack stack, net.minecraft.util.RandomSource random) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("ClusterStats")) {
            CompoundTag stats = new CompoundTag();
            int slots;
            if (this.getType() == Type.MILESTONE) slots = 4 + random.nextInt(2); 
            else if (this.getType() == Type.MAJOR) slots = 3 + random.nextInt(2);
            else slots = 3; 
            
            stats.putInt("SlotCount", slots);
            ListTag types = new ListTag();
            for(int i=0; i<slots; i++) {
                CompoundTag t = new CompoundTag();
                float roll = random.nextFloat();
                String typeName;
                if (this.getType() == Type.MILESTONE) {
                    typeName = roll < 0.3 ? "MILESTONE" : (roll < 0.8 ? "MAJOR" : "MINOR");
                } else if (this.getType() == Type.MAJOR) {
                    typeName = roll < 0.05 ? "MILESTONE" : (roll < 0.6 ? "MAJOR" : "MINOR");
                } else {
                    typeName = roll < 0.01 ? "MILESTONE" : (roll < 0.2 ? "MAJOR" : "MINOR");
                }
                t.putString("Type", typeName);
                types.add(t);
            }
            stats.put("SlotTypes", types);
            tag.put("ClusterStats", stats);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ClusterTooltipHandler.appendClusterInfo(stack, tooltip);
    }
}