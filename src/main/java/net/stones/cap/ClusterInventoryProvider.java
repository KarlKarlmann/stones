package net.stones.cap;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.stones.item.ClusterJewelItem;
import net.stones.item.StoneItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClusterInventoryProvider implements ICapabilitySerializable<CompoundTag> {

    private final ItemStackHandler inventory;
    private final LazyOptional<IItemHandler> optional;
    private final ItemStack ownerStack;

    public ClusterInventoryProvider(ItemStack stack) {
        this.ownerStack = stack;
        
        // Feste technische Größe 9 (für UI Layout), logische Größe wird begrenzt
        this.inventory = new ItemStackHandler(9) {
            
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack insertedStack) {
                // 1. REKURSIONS-SCHUTZ
                if (insertedStack.getItem() instanceof ClusterJewelItem) return false;
                
                // 2. Nur Runen
                if (!(insertedStack.getItem() instanceof StoneItem)) return false;

                // 3. Typ-Prüfung gegen die RNG-Stats (Strikter Check!)
                return checkSlotTypeCompatibility(slot, insertedStack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                // Speichert Inventar sofort im Item-NBT (wichtig!)
                CompoundTag nbt = ownerStack.getOrCreateTag();
                nbt.put("ClusterInventory", serializeNBT());
            }
            
            @Override
            public int getSlotLimit(int slot) {
                // Begrenzt Zugriff auf Slots, die laut RNG nicht existieren
                if (ownerStack.hasTag() && ownerStack.getTag().contains("ClusterStats")) {
                    int maxSlots = ownerStack.getTag().getCompound("ClusterStats").getInt("SlotCount");
                    if (slot >= maxSlots) return 0;
                }
                return 1;
            }

            // NEU: Verhindert das Herausnehmen von Items mit Curse of Binding
            @Override
            public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack current = getStackInSlot(slot);
                if (!current.isEmpty()) {
                    // Prüfe auf Fluch der Bindung
                    if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, current) > 0) {
                        // Wenn Creative Mode Bypass gewünscht wäre, könnte man hier Player checken,
                        // aber im ItemHandler haben wir keinen Player-Kontext.
                        // Fluch ist Fluch -> Kein Rausnehmen!
                        return ItemStack.EMPTY;
                    }
                }
                return super.extractItem(slot, amount, simulate);
            }
        };
        this.optional = LazyOptional.of(() -> inventory);
    }
    
    private boolean checkSlotTypeCompatibility(int slot, ItemStack stack) {
        if (!ownerStack.hasTag()) return true; // Sollte nicht passieren durch ensureInitialized
        
        CompoundTag stats = ownerStack.getTag().getCompound("ClusterStats");
        ListTag types = stats.getList("SlotTypes", Tag.TAG_COMPOUND);
        
        if (slot >= types.size()) return false; 
        
        String requiredType = types.getCompound(slot).getString("Type");
        
        if (stack.getItem() instanceof StoneItem stone) {
            // FIX: Wir vergleichen hier direkt den Enum-Namen.
            return stone.getType().name().equals(requiredType);
        }
        return false;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return inventory.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        inventory.deserializeNBT(nbt);
    }
}