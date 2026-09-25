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
import net.stones.init.StonesModEntities;
import net.stones.StonesMod;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class StonesProjectileEntity extends Projectile implements IEntityAdditionalSpawnData {

    private int lifetime = 80;
    private float gravity = 0.0f;
    private String renderMode = "BILLBOARD";
    private String textureData = "";

    private float hitboxWidth = 0.25f;
    private float hitboxHeight = 0.25f;

    // KubeJS SAM-Callbacks
    public Consumer<StonesProjectileEntity> onTickConsumer = null;
    public BiConsumer<StonesProjectileEntity, EntityHitResult> onHitEntityConsumer = null;
    public BiConsumer<StonesProjectileEntity, BlockHitResult> onHitBlockConsumer = null;

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

        // Pro Tick Actions ausführen (KubeJS SAM-Consumer)
        if (!this.level().isClientSide) {
            if (this.onTickConsumer != null) {
                try {
                    this.onTickConsumer.accept(this);
                } catch (Throwable t) {
                    StonesMod.LOGGER.error("[Stones Projectile] Fehler in onTick Consumer:", t);
                    this.onTickConsumer = null;
                }
            }
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
            if (this.onHitEntityConsumer != null) {
                try {
                    this.onHitEntityConsumer.accept(this, result);
                } catch (Throwable t) {
                    StonesMod.LOGGER.error("[Stones Projectile] Fehler in onHitEntity Consumer:", t);
                }
            }
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            if (this.onHitBlockConsumer != null) {
                try {
                    this.onHitBlockConsumer.accept(this, result);
                } catch (Throwable t) {
                    StonesMod.LOGGER.error("[Stones Projectile] Fehler in onHitBlock Consumer:", t);
                }
            }
            this.discard();
        }
    }

    @Override
    protected void defineSynchedData() {}

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