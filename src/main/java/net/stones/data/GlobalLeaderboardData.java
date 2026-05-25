package net.stones.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Speichert Highscores inklusive Todesursache.
 * FIX: Nutzt .contains() statt .has() für NBT-Checks.
 */
public class GlobalLeaderboardData extends SavedData {

    private static final String FILE_NAME = "stones_leaderboard";
    private final List<LeaderboardEntry> entries = new ArrayList<>();
    private static final int MAX_ENTRIES = 50;

    public record LeaderboardEntry(String name, int score, String deathReason) {
        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putInt("score", score);
            tag.putString("reason", deathReason);
            return tag;
        }
        public static LeaderboardEntry fromNBT(CompoundTag tag) {
            return new LeaderboardEntry(
                tag.getString("name"), 
                tag.getInt("score"), 
                tag.contains("reason") ? tag.getString("reason") : "Unbekannt"
            );
        }
    }

    public void addScore(String name, int score, String reason) {
        entries.add(new LeaderboardEntry(name, score, reason));
        entries.sort(Comparator.comparingInt(LeaderboardEntry::score).reversed());
        if (entries.size() > MAX_ENTRIES) {
            entries.subList(MAX_ENTRIES, entries.size()).clear();
        }
        this.setDirty();
    }

    /**
     * Sucht den letzten Eintrag des Spielers mit dem exakten Score und überschreibt die Todesursache.
     * Ermöglicht das nachträgliche Speichern des benutzerdefinierten Epitaphs.
     */
    public void updateLastRunReason(String playerName, int score, String newReason) {
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            if (entry.name().equals(playerName) && entry.score() == score) {
                // Ersetze den Eintrag mit der neuen Inschrift
                entries.set(i, new LeaderboardEntry(playerName, score, newReason));
                this.setDirty();
                break;
            }
        }
    }

    public List<LeaderboardEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public static GlobalLeaderboardData get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(GlobalLeaderboardData::load, GlobalLeaderboardData::new, FILE_NAME);
    }

    public static GlobalLeaderboardData load(CompoundTag nbt) {
        GlobalLeaderboardData data = new GlobalLeaderboardData();
        ListTag list = nbt.getList("scores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.entries.add(LeaderboardEntry.fromNBT(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (LeaderboardEntry entry : entries) {
            list.add(entry.toNBT());
        }
        nbt.put("scores", list);
        return nbt;
    }
}