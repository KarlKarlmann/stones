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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.stones.data.ShrineInstance.SlotConfig;
import net.stones.data.ShrineInstance.SlotType;
import net.stones.init.StonesModMenus;
import net.stones.init.StonesModTags;
import net.stones.item.ClusterJewelItem;
import net.stones.item.StoneItem;
import net.stones.util.RuneCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.TreeMap;

public class RunestoneMenu extends AbstractContainerMenu {

    private final IItemHandler shrineInventory;
    private final int slotCount;
    public final List<SlotConfig> layoutData = new ArrayList<>();
    private final Player player;
    
    // Eindeutige ID des aktuell geöffneten Schreins
    private final UUID shrineId;
    
    // Gruppierung der Slot-Indizes nach Level-Zeilen (geordnete Stufen)
    private final List<Integer> distinctLevels = new ArrayList<>();
    private final Map<Integer, List<Integer>> levelToLayoutIndices = new TreeMap<>();

    // --- CLIENT KONSTRUKTOR ---
    public RunestoneMenu(int containerId, Inventory playerInv, FriendlyByteBuf data) {
        super(StonesModMenus.RUNESTONE_MENU.get(), containerId);
        
        this.shrineInventory = new ItemStackHandler(data.readInt());
        this.slotCount = this.shrineInventory.getSlots();
        this.player = playerInv.player;
        
        int layoutSize = data.readInt();
        for (int i = 0; i < layoutSize; i++) {
            SlotType type = data.readEnum(SlotType.class);
            int lvl = data.readInt();
            int idx = data.readInt();
            layoutData.add(new SlotConfig(type, lvl, idx));
        }
        
        this.shrineId = data.readUUID(); 
        
        buildLevelIndex();
        initSlots();
        addPlayerInventory(playerInv);
    }

    // --- SERVER KONSTRUKTOR ---
    public RunestoneMenu(int containerId, Inventory playerInv, IItemHandler shrineInventory, List<SlotConfig> layout, UUID shrineId) {
        super(StonesModMenus.RUNESTONE_MENU.get(), containerId);
        this.shrineInventory = shrineInventory;
        this.slotCount = shrineInventory.getSlots();
        this.layoutData.addAll(layout);
        this.player = playerInv.player;
        
        this.shrineId = shrineId; 
        
        buildLevelIndex();
        initSlots();
        addPlayerInventory(playerInv);
    }

    public UUID getShrineId() {
        return this.shrineId;
    }

    public int getBoundPlayerLevel() {
        return this.player != null ? this.player.experienceLevel : 0;
    }

    private void buildLevelIndex() {
        this.distinctLevels.clear();
        this.levelToLayoutIndices.clear();
        for (int i = 0; i < layoutData.size(); i++) {
            SlotConfig cfg = layoutData.get(i);
            levelToLayoutIndices.computeIfAbsent(cfg.requiredLevel, k -> new ArrayList<>()).add(i);
        }
        this.distinctLevels.addAll(levelToLayoutIndices.keySet());
    }

    public int getTotalRowCount() {
        return this.distinctLevels.size();
    }

    public int getRequiredLevelForRow(int row) {
        if (row >= 0 && row < this.distinctLevels.size()) {
            return this.distinctLevels.get(row);
        }
        return 0;
    }

    public List<Integer> getLayoutIndicesInRow(int row) {
        if (row >= 0 && row < this.distinctLevels.size()) {
            return this.levelToLayoutIndices.getOrDefault(this.distinctLevels.get(row), List.of());
        }
        return List.of();
    }

    private void initSlots() {
        // Keine Spiral-Berechnung mehr auf dem Server! 
        // Initialkoordinaten werden geparkt; der Screen positioniert sichtbare Slots dynamisch.
        for (int i = 0; i < layoutData.size(); i++) {
            SlotConfig cfg = layoutData.get(i);
            
            this.addSlot(new SlotItemHandler(shrineInventory, cfg.inventoryIndex, -2000, -2000) {
                
                @Override
                public boolean mayPlace(ItemStack stack) {
                    boolean isTypeAllowed = switch (cfg.type) {
                        case MINOR -> stack.is(StonesModTags.RUNE_MINOR);
                        case MAJOR -> stack.is(StonesModTags.RUNE_MAJOR) || stack.is(StonesModTags.RUNE_MINOR);
                        case MILESTONE -> stack.is(StonesModTags.RUNE_MILESTONE);
                    };
                    
                    if (!isTypeAllowed && stack.getItem() instanceof ClusterJewelItem cluster) {
                        isTypeAllowed = switch(cfg.type) {
                            case MINOR -> cluster.getType() == StoneItem.Type.MINOR;
                            case MAJOR -> cluster.getType() == StoneItem.Type.MAJOR || cluster.getType() == StoneItem.Type.MINOR;
                            case MILESTONE -> cluster.getType() == StoneItem.Type.MILESTONE;
                        };
                    }

                    if (!isTypeAllowed) return false;

                    int runeReq = RuneCalculator.getRequiredLevel(stack);
                    return cfg.requiredLevel >= runeReq;
                }

                @Override
                public int getMaxStackSize() { return 1; }
                
                @Override
                public boolean mayPickup(Player playerIn) {
                    ItemStack stack = this.getItem();
                    if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, stack) > 0) {
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
            
            if (!cursor.isEmpty() && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)) {
                if (slot.mayPlace(cursor)) {
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
        int xOffset = 79;
        int yStartMain = 153; 
        int yStartHotbar = 211;

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