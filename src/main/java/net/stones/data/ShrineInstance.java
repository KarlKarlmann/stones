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
import net.stones.network.PacketSyncShrineMirror; // WICHTIG: Import der neuen Klasse

import java.util.*;

/**
 * Erweitere Schrein-Instanz mit Netzwerk-Synchronisation.
 * Implementiert das "Dirty-Flag" Prinzip für effiziente Client-Mirror Updates.
 */
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

    // Das Inventar triggert nun bei jeder Änderung eine Synchronisation
    private ItemStackHandler inventory = new ItemStackHandler(5) {
        @Override
        protected void onContentsChanged(int slot) {
            if (ShrineSavedData.get() != null) {
                ShrineSavedData.get().setDirty();
            }
            // NEU: Sofortige Spiegelung an alle verbundenen Spieler (Mirror-Modell)
            syncToAllOwners();
        }
    };
    
    private final List<SlotConfig> slotLayout = new ArrayList<>();
    
    private static final List<Integer> RUNE_UNLOCK_LEVELS = Arrays.asList(1, 2, 3, 4, 7, 11, 18, 29, 47, 76, 100);
    private static final List<Integer> POOL_MILESTONE = Arrays.asList(5, 8, 13, 21, 34, 55, 89);

    public ShrineInstance(UUID id) {
        this.id = id;
    }

    // --- TEAM LOGIK & SYNC ---
    
    public void addOwner(UUID playerUUID) {
        if (owners.add(playerUUID)) {
            ShrineSavedData.get().setDirty();
            syncToAllOwners(); // Neuen Besitzer initial synchronisieren
        }
    }
    
    public void removeOwner(UUID playerUUID) {
        if (owners.remove(playerUUID)) {
            ShrineSavedData.get().setDirty();
        }
    }

    /**
     * Sendet den aktuellen Zustand des Schreins (Inventar + Layout) an alle Besitzer.
     * Dies ist die Basis für die Client-seitige Simulation ohne Netzwerk-Verzögerung.
     */
    public void syncToAllOwners() {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        for (UUID ownerId : owners) {
            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(ownerId);
            if (player != null) {
                // FIXED: Benutze die separate PacketSyncShrineMirror Klasse
                StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncShrineMirror(this.inventory, this.slotLayout)
                );
            }
        }
    }
    
    // ... Rest der Klasse (Getter, Setter, serializeNBT, etc.) bleibt unverändert ...
    
    public boolean isOwner(UUID playerUUID) { return owners.contains(playerUUID); }
    public Set<UUID> getOwners() { return Collections.unmodifiableSet(owners); }
    public void setLocation(GlobalPos pos) { this.worldPosition = pos; ShrineSavedData.get().setDirty(); }
    public GlobalPos getLocation() { return worldPosition; }

    public void generateRandomLayout() {
        slotLayout.clear();
        long seed = (id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        Random rand = new Random(seed);
        int currentIndex = 0;
        List<Integer> sortedLevels = RUNE_UNLOCK_LEVELS.stream().sorted().toList();
        for (int lvl : sortedLevels) {
            float roll = rand.nextFloat();
            if (lvl == 1) {
                if (roll < 0.90f) slotLayout.add(new SlotConfig(SlotType.MINOR, lvl, currentIndex++));
                else slotLayout.add(new SlotConfig(SlotType.MAJOR, lvl, currentIndex++));
                continue;
            }
            if (roll < 0.45f) slotLayout.add(new SlotConfig(SlotType.MINOR, lvl, currentIndex++));
            else if (roll < 0.60f) slotLayout.add(new SlotConfig(SlotType.MAJOR, lvl, currentIndex++));
        }
        int milestoneCount = rand.nextInt(8);
        List<Integer> milestoneSelection = new ArrayList<>(POOL_MILESTONE);
        Collections.shuffle(milestoneSelection, rand);
        List<Integer> selectedMilestones = milestoneSelection.subList(0, Math.min(milestoneCount, milestoneSelection.size()));
        selectedMilestones.sort(Integer::compareTo);
        for (int lvl : selectedMilestones) slotLayout.add(new SlotConfig(SlotType.MILESTONE, lvl, currentIndex++));
        resizeInventory(currentIndex);
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