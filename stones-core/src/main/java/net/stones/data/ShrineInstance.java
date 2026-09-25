package net.stones.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.stones.StonesMod;
import net.stones.gui.layout.ShrineLayout;
import net.stones.init.StonesModConfig;
import net.stones.network.PacketSyncShrineMirror;

import java.util.*;

public class ShrineInstance implements INBTSerializable<CompoundTag> {

    public enum SlotType { MINOR, MAJOR, MILESTONE }

    public static class SlotConfig {
        public SlotType type;
        public int requiredLevel;
        public int inventoryIndex;

        public SlotConfig(SlotType type, int requiredLevel, int inventoryIndex) {
            this.type = type;
            this.requiredLevel = requiredLevel;
            this.inventoryIndex = inventoryIndex;
        }
    }

    private final UUID id;
    private final Set<UUID> owners = new HashSet<>();
    private GlobalPos worldPosition;
    private int maxLevel = 100;

    private ItemStackHandler inventory = new ItemStackHandler(5) {
        @Override
        protected void onContentsChanged(int slot) {
            if (ShrineSavedData.get() != null) {
                ShrineSavedData.get().setDirty();
            }
            syncToAllOwners();
        }
    };
    
    private final List<SlotConfig> slotLayout = new ArrayList<>();

    public ShrineInstance(UUID id) {
        this.id = id;
    }

    public void addOwner(UUID playerUUID) {
        if (owners.add(playerUUID)) {
            ShrineSavedData.get().setDirty();
            syncToAllOwners();
        }
    }
    
    public void removeOwner(UUID playerUUID) {
        if (owners.remove(playerUUID)) {
            ShrineSavedData.get().setDirty();
        }
    }

    public void syncToAllOwners() {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;
        for (UUID ownerId : owners) {
            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerId);
            if (player != null) {
                StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncShrineMirror(this.inventory, this.slotLayout)
                );
            }
        }
    }
    
    public boolean isOwner(UUID playerUUID) { return owners.contains(playerUUID); }
    public Set<UUID> getOwners() { return Collections.unmodifiableSet(owners); }
    public void setLocation(GlobalPos pos) { this.worldPosition = pos; ShrineSavedData.get().setDirty(); }
    public GlobalPos getLocation() { return worldPosition; }
    public int getMaxLevel() { return this.maxLevel; }

    /**
     * Nutzt jetzt die saubere "Single Source of Truth" Klasse.
     */
    public void generateRandomLayout() {
        int configMax = StonesModConfig.GLOBAL_MAX_SHRINE_LEVEL.get();
        if (configMax < 1) configMax = 100;
        this.maxLevel = configMax;

        this.slotLayout.clear();
        this.slotLayout.addAll(ShrineLayout.generateDeterministicLayout(this.id, this.maxLevel));

        resizeInventory(this.slotLayout.size());
    }

    private void resizeInventory(int size) {
        ItemStackHandler oldInv = this.inventory;
        if (size == 0) size = 1;
        this.inventory = new ItemStackHandler(size) {
            @Override protected void onContentsChanged(int slot) {
                if (ShrineSavedData.get() != null) ShrineSavedData.get().setDirty();
                syncToAllOwners();
            }
        };
        if (oldInv != null) {
            for (int i = 0; i < Math.min(oldInv.getSlots(), size); i++) this.inventory.setStackInSlot(i, oldInv.getStackInSlot(i));
        }
    }

    public UUID getId() { return id; }
    public IItemHandler getInventory() { return inventory; }
    public List<SlotConfig> getLayout() { return slotLayout; }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putInt("maxLevel", this.maxLevel);
        ListTag ownerList = new ListTag();
        for (UUID owner : owners) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putUUID("uuid", owner);
            ownerList.add(ownerTag);
        }
        tag.put("owners", ownerList);
        if (worldPosition != null) {
            tag.put("pos", NbtUtils.writeBlockPos(worldPosition.pos()));
            tag.putString("dim", worldPosition.dimension().location().toString());
        }
        tag.put("inventory", inventory.serializeNBT());
        ListTag layoutList = new ListTag();
        for (SlotConfig cfg : slotLayout) {
            CompoundTag slotTag = new CompoundTag();
            slotTag.putInt("type", cfg.type.ordinal());
            slotTag.putInt("lvl", cfg.requiredLevel);
            slotTag.putInt("idx", cfg.inventoryIndex);
            layoutList.add(slotTag);
        }
        tag.put("layout", layoutList);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        owners.clear();
        if (nbt.contains("owner")) owners.add(nbt.getUUID("owner"));
        if (nbt.contains("owners", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("owners", Tag.TAG_COMPOUND);
            for (Tag t : list) owners.add(((CompoundTag)t).getUUID("uuid"));
        }
        if (nbt.contains("maxLevel")) {
            this.maxLevel = nbt.getInt("maxLevel");
        } else {
            this.maxLevel = 100;
        }
        if (nbt.contains("pos") && nbt.contains("dim")) {
            BlockPos bp = NbtUtils.readBlockPos(nbt.getCompound("pos"));
            ResourceLocation dimLoc = new ResourceLocation(nbt.getString("dim"));
            this.worldPosition = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimLoc), bp);
        }
        slotLayout.clear();
        if (nbt.contains("layout", Tag.TAG_LIST)) {
            ListTag layoutList = nbt.getList("layout", Tag.TAG_COMPOUND);
            int maxIndex = 0;
            for (Tag t : layoutList) {
                CompoundTag slotTag = (CompoundTag) t;
                SlotConfig cfg = new SlotConfig(SlotType.values()[slotTag.getInt("type")], slotTag.getInt("lvl"), slotTag.getInt("idx"));
                slotLayout.add(cfg);
                if (cfg.inventoryIndex > maxIndex) maxIndex = cfg.inventoryIndex;
            }
            resizeInventory(slotLayout.isEmpty() ? 5 : maxIndex + 1);
        } else {
            resizeInventory(5);
        }
        if (nbt.contains("inventory")) inventory.deserializeNBT(nbt.getCompound("inventory"));
    }
}