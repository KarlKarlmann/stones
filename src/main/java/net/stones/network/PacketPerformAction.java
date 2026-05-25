package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.enchantment.behavior.ActionContext;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.logic.RuneCalculator;
import com.google.gson.JsonObject;
import net.stones.enchantment.behavior.TriggerType;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Server-Side Logic für Action Skills.
 * Ersetzt die innere Klasse ActionSystem.ActionPacket, um Client-Code-Abhängigkeiten zu entfernen.
 */
public class PacketPerformAction {
    private final String runeId;
    private final int slot;

    public PacketPerformAction(String id, int s) { this.runeId = id; this.slot = s; }
    public PacketPerformAction(FriendlyByteBuf b) { this.runeId = b.readUtf(); this.slot = b.readInt(); }
    public void encode(FriendlyByteBuf b) { b.writeUtf(runeId); b.writeInt(slot); }

    public static void handle(PacketPerformAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) validateAndExecute(p, msg.runeId, msg.slot);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void validateAndExecute(ServerPlayer player, String runeId, int slot) {
        player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
            UUID id = cap.getLinkedShrine();
            if (id == null) return;
            ShrineInstance shrine = ShrineSavedData.get(player.serverLevel()).getShrine(id);
            if (shrine == null) return;

            AtomicBoolean executed = new AtomicBoolean(false);
            RuneCalculator.collectActiveRunes(shrine.getInventory(), shrine.getLayout(), player.experienceLevel, 
                (rune, rLvl, sLvl, mult, mSlot, sub) -> {
                    if (executed.get()) return;
                    ResourceLocation rId = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                    if (rId != null && rId.toString().equalsIgnoreCase(runeId.trim())) {
                        ActionContext c = new ActionContext(player, null, new JsonObject(), rId.getPath());
                        c.setContextLevels(rune, rLvl, sLvl, mult);
                        c.setVariable("action_slot", slot);
                        for (RuneBehavior b : rune.getBehaviors()) {
                            if (b.trigger == TriggerType.ON_ACTION_BUTTON) {
                                b.execute(c);
                                executed.set(true);
                                return;
                            }
                        }
                    }
                }
            );
        });
    }
}