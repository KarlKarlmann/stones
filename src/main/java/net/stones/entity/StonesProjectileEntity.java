package net.stones.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import net.stones.enchantment.behavior.ActionContext;
import net.stones.init.MilestoneActionRegistry;
import net.stones.init.StonesModEntities;

public class StonesProjectileEntity extends Projectile implements IEntityAdditionalSpawnData {

    private int lifetime = 80;
    private float gravity = 0.0f;
    private String renderMode = "BILLBOARD";
    private String textureData = "";

    private float hitboxWidth = 0.25f;
    private float hitboxHeight = 0.25f;

    // Verschachtelte Action-Payloads für das Backend
    private JsonArray onHitEntityActions = null;
    private JsonArray onHitBlockActions = null;
    private JsonArray onTickActions = null;

    public StonesProjectileEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    public StonesProjectileEntity(Level level, double x, double y, double z) {
        this(StonesModEntities.STONES_PROJECTILE.get(), level);
        this.setPos(x, y, z);
    }

    public void setup(Vec3 direction, float speed, float gravity, int lifetime, 
                      float hitboxWidth, float hitboxHeight,
                      String renderMode, String textureData,
                      JsonArray onHitEntity, JsonArray onHitBlock, JsonArray onTick) {
        this.setDeltaMovement(direction.normalize().scale(speed));
        this.gravity = gravity;
        this.lifetime = lifetime;
        this.hitboxWidth = hitboxWidth;
        this.hitboxHeight = hitboxHeight;
        this.renderMode = renderMode;
        this.textureData = textureData;
        this.onHitEntityActions = onHitEntity;
        this.onHitBlockActions = onHitBlock;
        this.onTickActions = onTick;

        // Aktualisiert die Hitbox-Dimensionen direkt beim Spawnen
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.hitboxWidth, this.hitboxHeight);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount >= this.lifetime) {
            this.discard();
            return;
        }

        // 1. Pro Tick Actions ausführen (z. B. Partikelspuren oder kontinuierlicher Flächenschaden)
        if (!this.level().isClientSide && onTickActions != null && !onTickActions.isEmpty() && this.getOwner() instanceof ServerPlayer serverPlayer) {
            ActionContext tickCtx = new ActionContext(serverPlayer, null, new JsonObject(), "projectile");
            tickCtx.setVariable("pos", this.position());
            tickCtx.setVariable("projectile", this);
			tickCtx.setVariable("age", this.tickCount);
            MilestoneActionRegistry.executeActionList(tickCtx, onTickActions);
        }

        Vec3 movement = this.getDeltaMovement();
        Vec3 currentPos = this.position();
        Vec3 nextPos = currentPos.add(movement);

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
        }

        if (this.gravity > 0) {
            movement = movement.subtract(0, this.gravity, 0);
            this.setDeltaMovement(movement);
        }

        this.setPos(nextPos);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide) {
            if (onHitEntityActions != null && this.getOwner() instanceof ServerPlayer serverPlayer) {
                // Context für das Treffer-Event mit 4 Argumenten erstellen
                ActionContext hitCtx = new ActionContext(serverPlayer, null, new JsonObject(), "projectile");
                hitCtx.setVariable("target", result.getEntity());
                hitCtx.setVariable("hitPos", result.getLocation());

                // Führt alle verschachtelten Actions aus deinem JSON aus!
                MilestoneActionRegistry.executeActionList(hitCtx, onHitEntityActions);
            }
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            if (onHitBlockActions != null && this.getOwner() instanceof ServerPlayer serverPlayer) {
                ActionContext hitCtx = new ActionContext(serverPlayer, null, new JsonObject(), "projectile");
                hitCtx.setVariable("hitPos", result.getLocation());
                hitCtx.setVariable("blockPos", result.getBlockPos());

                MilestoneActionRegistry.executeActionList(hitCtx, onHitBlockActions);
            }
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData() {}

    // --- FORGE NETWORK SPAWN SYNC (Für Client-Rendering) ---
    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.renderMode != null ? this.renderMode : "BILLBOARD");
        buffer.writeUtf(this.textureData != null ? this.textureData : "");
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.renderMode = buffer.readUtf();
        this.textureData = buffer.readUtf();
    }

    public String getRenderMode() { return renderMode; }
    public String getTextureData() { return textureData; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {}
}