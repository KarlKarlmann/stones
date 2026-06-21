package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.entity.EchoTraderEntity;
import net.stones.init.StonesModItems;

import java.util.function.Supplier;

public class PacketBuyEcho {
    private final int slotId;
    private final int entityId;

    public PacketBuyEcho(int slotId, int entityId) {
        this.slotId = slotId;
        this.entityId = entityId;
    }

    public PacketBuyEcho(FriendlyByteBuf buf) {
        this.slotId = buf.readInt();
        this.entityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(slotId);
        buf.writeInt(entityId);
    }

    public static void handle(PacketBuyEcho msg, Supplier<net.minecraftforge.network.NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            EchoTraderEntity trader = (EchoTraderEntity) player.level().getEntity(msg.entityId);
            if (trader == null) return;

            if (msg.slotId >= 0 && msg.slotId < trader.getStock().size()) {
                ItemStack stockItem = trader.getStock().get(msg.slotId);

                // --- FALL A: OPFERGABE ---
                if (stockItem.hasTag() && stockItem.getTag().getBoolean("EchoSacrifice")) {
                    applySacrifice(player, stockItem.getTag().getInt("SacrificeType"));
                    trader.consumeResonance();
                } 
                // --- FALL B: NORMALER KAUF ---
                else {
                    int cost = getXpCost(stockItem);
                    if (player.isCreative() || player.experienceLevel >= cost) {
                        if (!player.isCreative()) player.giveExperienceLevels(-cost);
                        ItemStack buy = stockItem.copy();
                        
                        // Den Custom Cost Tag vor dem Hinzufügen ins Inventar bereinigen, damit der Stack wieder Vanilla ist
                        if (buy.hasTag()) {
                            buy.getTag().remove("EchoCustomCost");
                            if (buy.getTag().isEmpty()) buy.setTag(null);
                        }

                        if (!player.getInventory().add(buy)) player.drop(buy, false);
                        trader.consumeResonance();
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void applySacrifice(ServerPlayer player, int type) {
        float healthPct = player.getMaxHealth();
        int xpPoints = 0;
        float damage = 0;

        switch (type) {
            case 0 -> { damage = healthPct * 0.1f; xpPoints = 30 + player.getRandom().nextInt(20); }
            case 1 -> { damage = healthPct * (0.3f + player.getRandom().nextFloat() * 0.3f); xpPoints = 400 + player.getRandom().nextInt(300); }
            case 2 -> { damage = healthPct * (0.75f + player.getRandom().nextFloat() * 0.30f); xpPoints = 4000 + player.getRandom().nextInt(2000); }
        }

        player.hurt(player.damageSources().magic(), damage);
        player.giveExperiencePoints(xpPoints);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    public static int getXpCost(ItemStack stack) {
        // --- 0. Custom Config-based Cost ---
        // Wenn das Item vom Echo Trader ein NBT-Preisschild erhalten hat, nutzen wir dieses!
        if (stack.hasTag() && stack.getTag().contains("EchoCustomCost")) {
            return stack.getTag().getInt("EchoCustomCost");
        }

        // --- 1. Resonanz Boxen ---
        if (stack.getItem() == StonesModItems.RESONANCE_BOX.get()) {
            int tier = stack.getOrCreateTag().getInt("ResonanceLootTier");
            if (tier <= 0) tier = 1;
            return net.stones.init.StonesModConfig.TRADER_COST_BOX_BASE.get() + (tier * net.stones.init.StonesModConfig.TRADER_COST_BOX_PER_TIER.get());
        }
        
        // --- 2. Runen ---
        if (stack.getItem() instanceof net.stones.item.StoneItem) {
            int base = net.stones.init.StonesModConfig.TRADER_COST_RUNE_MINOR.get();
            if (stack.getItem() == StonesModItems.RUNE_MAJOR.get()) {
                base = net.stones.init.StonesModConfig.TRADER_COST_RUNE_MAJOR.get();
            } else if (stack.getItem() == StonesModItems.RUNE_MILESTONE.get()) {
                base = net.stones.init.StonesModConfig.TRADER_COST_RUNE_MILESTONE.get();
            }
            int amp = EnchantmentHelper.getItemEnchantmentLevel(ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("stones", "amplify")), stack);
            
            return base + (amp / net.stones.init.StonesModConfig.TRADER_COST_RUNE_AMP_DIVISOR.get());
        }
        
        // --- 3. Cluster ---
        if (stack.getItem() == StonesModItems.CLUSTER_JEWEL_MILESTONE.get()) 
            return net.stones.init.StonesModConfig.TRADER_COST_CLUSTER_MILESTONE.get();
        if (stack.getItem() == StonesModItems.CLUSTER_JEWEL_MAJOR.get()) 
            return net.stones.init.StonesModConfig.TRADER_COST_CLUSTER_MAJOR.get();
        if (stack.getItem() == StonesModItems.CLUSTER_JEWEL_MINOR.get()) 
            return net.stones.init.StonesModConfig.TRADER_COST_CLUSTER_MINOR.get();
        
        // --- 4. Fallback Ressourcen ---
        int count = stack.getCount();
        return Math.max(net.stones.init.StonesModConfig.TRADER_COST_RESOURCE_BASE.get(), count / net.stones.init.StonesModConfig.TRADER_COST_RESOURCE_DIVISOR.get()); 
    }
}