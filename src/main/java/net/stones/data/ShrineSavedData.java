package net.stones.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.stones.StonesMod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShrineSavedData extends SavedData {
    
    private static final String DATA_NAME = StonesMod.MODID + "_shrines";
    private final Map<UUID, ShrineInstance> shrineMap = new HashMap<>();

    private static ShrineSavedData activeInstance; 

    public ShrineSavedData() {
        activeInstance = this;
    }

    public static ShrineSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        ShrineSavedData data = overworld.getDataStorage().computeIfAbsent(
                ShrineSavedData::load, 
                ShrineSavedData::create, 
                DATA_NAME
        );
        activeInstance = data; 
        return data;
    }
    
    public static ShrineSavedData get() {
        return activeInstance;
    }

    public ShrineInstance getShrine(UUID id) {
        return shrineMap.get(id);
    }

    // Neue Zufalls-ID
    public ShrineInstance createShrine() {
        return createShrine(UUID.randomUUID());
    }

    // Spezifische ID (Deterministisch)
    public ShrineInstance createShrine(UUID id) {
        ShrineInstance instance = new ShrineInstance(id);
        
        // Layout basierend auf UUID generieren
        instance.generateRandomLayout(); 
        
        shrineMap.put(id, instance);
        setDirty();
        return instance;
    }

    public void removeShrine(UUID id) {
        if (shrineMap.containsKey(id)) {
            shrineMap.remove(id);
            setDirty();
            StonesMod.LOGGER.info("Schrein gelöscht: " + id);
        }
    }

    public static ShrineSavedData create() {
        return new ShrineSavedData();
    }

    public static ShrineSavedData load(CompoundTag nbt) {
        ShrineSavedData data = new ShrineSavedData();
        if (nbt.contains("shrines", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("shrines", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                CompoundTag shrineTag = (CompoundTag) t;
                UUID id = shrineTag.getUUID("id");
                ShrineInstance instance = new ShrineInstance(id);
                instance.deserializeNBT(shrineTag);
                data.shrineMap.put(id, instance);
            }
        }
        activeInstance = data;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (ShrineInstance instance : shrineMap.values()) {
            list.add(instance.serializeNBT());
        }
        nbt.put("shrines", list);
        return nbt;
    }
}