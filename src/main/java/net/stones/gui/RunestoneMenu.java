package net.stones.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.stones.gui.layout.ShrineLayout;
import net.stones.data.ShrineInstance.SlotConfig;
import net.stones.data.ShrineInstance.SlotType;
import net.stones.init.StonesModMenus;
import net.stones.init.StonesModTags;
import net.stones.item.ClusterJewelItem;
import net.stones.item.StoneItem;
import net.stones.logic.RuneCalculator;

import java.util.ArrayList;
import java.util.List;

public class RunestoneMenu extends AbstractContainerMenu {

    private final IItemHandler shrineInventory;
    private final int slotCount;
    public final List<SlotConfig> layoutData = new ArrayList<>();
    private final Player player;

    public RunestoneMenu(int containerId, Inventory playerInv, FriendlyByteBuf data) {
        this(containerId, playerInv, new ItemStackHandler(data.readInt()), false);
        int layoutSize = data.readInt();
        for (int i = 0; i < layoutSize; i++) {
            SlotType type = data.readEnum(SlotType.class);
            int lvl = data.readInt();
            int idx = data.readInt();
            layoutData.add(new SlotConfig(type, lvl, idx));
        }
        initSlots();
        addPlayerInventory(playerInv);
    }

    public RunestoneMenu(int containerId, Inventory playerInv, IItemHandler shrineInventory, List<SlotConfig> layout) {
        super(StonesModMenus.RUNESTONE_MENU.get(), containerId);
        this.shrineInventory = shrineInventory;
        this.slotCount = shrineInventory.getSlots();
        this.layoutData.addAll(layout);
        this.player = playerInv.player;
        initSlots();
        addPlayerInventory(playerInv);
    }

    private RunestoneMenu(int containerId, Inventory playerInv, IItemHandler shrineInventory, boolean dummy) {
        super(StonesModMenus.RUNESTONE_MENU.get(), containerId);
        this.shrineInventory = shrineInventory;
        this.slotCount = shrineInventory.getSlots();
        this.player = playerInv.player;
    }

    private void initSlots() {
        List<Vec2> positions = ShrineLayout.generateSpiralPositions(layoutData.size());

        for (int i = 0; i < layoutData.size(); i++) {
            SlotConfig cfg = layoutData.get(i);
            Vec2 pos = positions.get(i);
            
            this.addSlot(new SlotItemHandler(shrineInventory, cfg.inventoryIndex, (int)pos.x, (int)pos.y) {
                
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 1. Tag Check für normale Runen
                    boolean isTypeAllowed = switch (cfg.type) {
                        case MINOR -> stack.is(StonesModTags.RUNE_MINOR);
                        case MAJOR -> stack.is(StonesModTags.RUNE_MAJOR) || stack.is(StonesModTags.RUNE_MINOR);
                        case MILESTONE -> stack.is(StonesModTags.RUNE_MILESTONE);
                    };
                    
                    // 2. Check für Cluster Jewels
                    if (!isTypeAllowed && stack.getItem() instanceof ClusterJewelItem cluster) {
                        isTypeAllowed = switch(cfg.type) {
                            case MINOR -> cluster.getType() == StoneItem.Type.MINOR;
                            case MAJOR -> cluster.getType() == StoneItem.Type.MAJOR || cluster.getType() == StoneItem.Type.MINOR;
                            case MILESTONE -> cluster.getType() == StoneItem.Type.MILESTONE;
                        };
                    }

                    if (!isTypeAllowed) return false;

                    // 3. Dynamische Level-Abfrage
                    // Der Slot muss ein höheres oder gleiches "Level" haben als das Item benötigt.
                    // Das verhindert, dass man mächtige Items in die allerersten Slots steckt.
                    int runeReq = RuneCalculator.getRequiredLevel(stack);
                    return cfg.requiredLevel >= runeReq;
                }

                @Override
                public int getMaxStackSize() { return 1; }
                
                // NEU: Verhindert das Herausnehmen bei Curse of Binding
                @Override
                public boolean mayPickup(Player playerIn) {
                    ItemStack stack = this.getItem();
                    if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, stack) > 0) {
                        // Im Creative Mode erlauben wir es trotzdem (Standard Minecraft Verhalten)
                        return playerIn.isCreative();
                    }
                    return super.mayPickup(playerIn);
                }
                
                @Override
                public void onTake(Player pPlayer, ItemStack pStack) {
                    tryApplyLevelCost(pPlayer);
                    super.onTake(pPlayer, pStack);
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    if (player instanceof ServerPlayer serverPlayer) {
                        RuneCalculator.updatePlayer(serverPlayer);
                    }
                }
            });
        }
    }
    
    // --- KOSTEN LOGIK ---
    private boolean tryApplyLevelCost(Player pPlayer) {
        if (pPlayer.isCreative()) return true;
        
        if (pPlayer.experienceLevel > 16) {
            if (!pPlayer.level().isClientSide) {
                pPlayer.giveExperienceLevels(-1);
                pPlayer.level().playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), 
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.5f);
            }
            return true;
        }
        return false; 
    }

    // --- KLICK INTERCEPTION (Insert) ---
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < this.slotCount) {
            Slot slot = this.slots.get(slotId);
            ItemStack cursor = this.getCarried();
            
            // Nur wenn wir wirklich versuchen, etwas reinzulegen (Cursor voll)
            if (!cursor.isEmpty() && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                if (slot.mayPlace(cursor)) {
                    // Wenn ja, ziehen wir Kosten ab (wir gehen davon aus, dass der Rest klappt)
                    tryApplyLevelCost(player);
                }
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    // --- SHIFT-KLICK LOGIK (Quick Move) ---
    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            
            // Schrein -> Inventar (Entnahme)
            if (index < this.slotCount) {
                // Check auf Binding Curse bei Shift-Click Entnahme
                if (!slot.mayPickup(playerIn)) {
                    return ItemStack.EMPTY;
                }

                if (!this.moveItemStackTo(itemstack1, this.slotCount, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                tryApplyLevelCost(playerIn);
            } 
            // Inventar -> Schrein (Einfügen)
            else {
                if (!this.moveItemStackTo(itemstack1, 0, this.slotCount, false)) {
                    return ItemStack.EMPTY;
                }
                tryApplyLevelCost(playerIn);
            }
            
            if (itemstack1.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    private void addPlayerInventory(Inventory playerInv) {
        int xOffset = 48;
        int yStartMain = 140; 
        int yStartHotbar = 198;

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, xOffset + j * 18, yStartMain + i * 18));
            }
        }
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInv, k, xOffset + k * 18, yStartHotbar));
        }
    }
}