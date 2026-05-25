package net.stones.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.gui.EchoTraderMenu;
import net.stones.init.StonesModItems;
import net.stones.init.StonesModParticles;
import net.stones.item.StoneItem;
import net.stones.enchantment.RuneEnchantment;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

public class EchoTraderEntity extends WanderingTrader {

    private static final EntityDataAccessor<Integer> DESPAWN_TICKS = SynchedEntityData.defineId(EchoTraderEntity.class, EntityDataSerializers.INT);
    
    // Initialer Timer auf 10 Minuten (12000 Ticks)
    private int despawnDelay = 12000; 
    private final List<ItemStack> currentStock = new ArrayList<>();

    public EchoTraderEntity(EntityType<? extends WanderingTrader> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DESPAWN_TICKS, 12000);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag tag) {
        generateRandomStock();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
    }

    private void generateRandomStock() {
        currentStock.clear();
        List<ItemStack> pool = new ArrayList<>();

        // --- RESONANZ BOXEN ---
        for (int i = 0; i < 4; i++) {
            int tier = 1 + this.random.nextInt(10);
            ItemStack box = new ItemStack(StonesModItems.RESONANCE_BOX.get());
            box.getOrCreateTag().putInt("ResonanceLootTier", tier);
            pool.add(box);
        }

        // --- EINZEL-RUNEN ---
        pool.add(generateSpecialRune(StoneItem.Type.MINOR));
        pool.add(generateSpecialRune(StoneItem.Type.MAJOR));
        if (random.nextBoolean()) pool.add(generateSpecialRune(StoneItem.Type.MILESTONE));

        // --- CLUSTER JEWELS ---
        pool.add(new ItemStack(StonesModItems.CLUSTER_JEWEL_MINOR.get()));
        if (random.nextFloat() < 0.3f) pool.add(new ItemStack(StonesModItems.CLUSTER_JEWEL_MAJOR.get()));

        // --- RESSOURCEN ---
        pool.add(new ItemStack(Items.LAPIS_LAZULI, 16 + random.nextInt(32)));
        pool.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(3)));
        pool.add(new ItemStack(Items.ANVIL));
        pool.add(new ItemStack(Items.ENCHANTING_TABLE));

        // --- LEBENSOPFER ---
        pool.add(createSacrificeItem(0)); 
        if (random.nextBoolean()) pool.add(createSacrificeItem(1)); 
        if (random.nextFloat() < 0.2f) pool.add(createSacrificeItem(2)); 

        // Korrektur: RandomSource zu java.util.Random für shuffle
        Collections.shuffle(pool, new java.util.Random(this.random.nextLong()));
        
        for (int i = 0; i < Math.min(13, pool.size()); i++) {
            currentStock.add(pool.get(i).copy());
        }
    }

    private ItemStack generateSpecialRune(StoneItem.Type type) {
        ItemStack rune = new ItemStack(switch (type) {
            case MAJOR -> StonesModItems.RUNE_MAJOR.get();
            case MILESTONE -> StonesModItems.RUNE_MILESTONE.get();
            default -> StonesModItems.RUNE_MINOR.get();
        });

        List<Enchantment> valid = ForgeRegistries.ENCHANTMENTS.getValues().stream()
                .filter(e -> e instanceof RuneEnchantment r && r.type.name().equals(type.name()))
                .collect(Collectors.toList());

        Map<Enchantment, Integer> enchants = new HashMap<>();
        if (!valid.isEmpty()) {
            enchants.put(valid.get(random.nextInt(valid.size())), 1 + random.nextInt(10));
        }

        if (random.nextFloat() < 0.25f) {
            Enchantment amplify = ForgeRegistries.ENCHANTMENTS.getValue(new net.minecraft.resources.ResourceLocation("stones", "amplify"));
            if (amplify != null) {
                enchants.put(amplify, 20 + random.nextInt(71));
            }
        }

        EnchantmentHelper.setEnchantments(enchants, rune);
        return rune;
    }

    private ItemStack createSacrificeItem(int type) {
        ItemStack stack = switch (type) {
            case 0 -> new ItemStack(Items.RED_DYE);
            case 1 -> new ItemStack(Items.NETHER_WART);
            default -> new ItemStack(Items.WITHER_ROSE);
        };
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("EchoSacrifice", true);
        tag.putInt("SacrificeType", type);
        stack.setHoverName(Component.literal(switch (type) {
            case 0 -> "§cKleines Lebensopfer";
            case 1 -> "§4Mittleres Lebensopfer";
            default -> "§0§lGroßes Lebensopfer";
        }));
        return stack;
    }

    public List<ItemStack> getStock() { return currentStock; }

    public void consumeResonance() {
        this.despawnDelay = Math.max(200, this.despawnDelay - 1200);
        this.entityData.set(DESPAWN_TICKS, this.despawnDelay);
    }

    public int getRemainingTicks() {
        return this.entityData.get(DESPAWN_TICKS);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide) {
            this.despawnDelay--;
            this.entityData.set(DESPAWN_TICKS, this.despawnDelay);
            
            // Trader löschen wenn Zeit abgelaufen
            if (this.despawnDelay <= 0) {
                this.discard();
            }
            this.setDeltaMovement(Vec3.ZERO);
        } else {
            int remaining = getRemainingTicks();
            float bob = Mth.sin(this.tickCount * 0.1f) * 0.1f;
            int chance = remaining < 1000 ? 1 : 4; 
            if (this.random.nextInt(chance) == 0) {
                double spread = 1.4;
                double startY = this.getY() + bob + 0.1;
                this.level().addParticle(StonesModParticles.ECHO_MOTH.get(), 
                    this.getX() + (random.nextDouble() - 0.5) * spread, 
                    startY + (random.nextDouble() * 0.3), 
                    this.getZ() + (random.nextDouble() - 0.5) * spread, 
                    (random.nextDouble() - 0.5) * 0.05, 0.02 + random.nextDouble() * 0.04, (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    @Override
    public void travel(Vec3 pTravelVector) {
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new EchoTraderMenu(id, inv, this),
                    Component.literal("§3Echo Trader")
                ), buf -> buf.writeInt(this.getId()));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override protected void updateTrades() {} 
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DespawnDelay", this.despawnDelay);
        ListTag stockTag = new ListTag();
        for (ItemStack stack : currentStock) stockTag.add(stack.save(new CompoundTag()));
        tag.put("EchoStock", stockTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        
        // FIX: Nur laden, wenn der Tag wirklich vorhanden ist (verhindert 0-Wert Bug bei /summon)
        if (tag.contains("DespawnDelay")) {
            this.despawnDelay = tag.getInt("DespawnDelay");
            this.entityData.set(DESPAWN_TICKS, this.despawnDelay);
        }

        if (tag.contains("EchoStock", Tag.TAG_LIST)) {
            currentStock.clear();
            ListTag stockTag = tag.getList("EchoStock", Tag.TAG_COMPOUND);
            for (int i = 0; i < stockTag.size(); i++) currentStock.add(ItemStack.of(stockTag.getCompound(i)));
        }
    }
}