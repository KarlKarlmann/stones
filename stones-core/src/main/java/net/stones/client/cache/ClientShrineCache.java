package net.stones.client.cache;

import net.minecraftforge.items.ItemStackHandler;
import net.stones.data.ShrineInstance;
import java.util.ArrayList;
import java.util.List;

/**
 * DEDIZIERTE CACHE KLASSE (Client-Only)
 * Hält den aktuellen Zustand des Schreins für alle UI-Komponenten bereit.
 * * WICHTIG: Enthält keine Netzwerk-Logik mehr, um Server-Crashes zu vermeiden.
 */
public class ClientShrineCache {

    // Der zentrale Mirror für das Inventar
    public static final ItemStackHandler INVENTORY = new ItemStackHandler(15);
    // Das Layout des Schreins
    public static final List<ShrineInstance.SlotConfig> LAYOUT = new ArrayList<>();

}