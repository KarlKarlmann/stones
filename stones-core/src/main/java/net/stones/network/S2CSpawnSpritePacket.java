package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stones.client.fx.SpriteInstance;
import net.stones.client.fx.StonesFxRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CSpawnSpritePacket {

    private final Vec3 pos;
    private final Vec3 velocity;
    private final float drag;
    private final float gravity;
    private final float initialScale;
    private final float growthRate;
    private final float spinSpeed;
    private final SpriteInstance.Facing facing;
    private final SpriteInstance.BlendMode blendMode;
    private final int lifetime;
    private final String textureId;
    private final List<SpriteInstance.Keyframe> keyframes;

    public S2CSpawnSpritePacket(Vec3 pos, Vec3 velocity, float drag, float gravity,
                                float initialScale, float growthRate, float spinSpeed,
                                SpriteInstance.Facing facing, SpriteInstance.BlendMode blendMode,
                                int lifetime, String textureId, List<SpriteInstance.Keyframe> keyframes) {
        this.pos = pos;
        this.velocity = velocity;
        this.drag = drag;
        this.gravity = gravity;
        this.initialScale = initialScale;
        this.growthRate = growthRate;
        this.spinSpeed = spinSpeed;
        this.facing = facing;
        this.blendMode = blendMode;
        this.lifetime = lifetime;
        this.textureId = textureId;
        this.keyframes = keyframes != null ? keyframes : new ArrayList<>();
    }

    public S2CSpawnSpritePacket(FriendlyByteBuf buf) {
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.velocity = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        this.drag = buf.readFloat();
        this.gravity = buf.readFloat();
        this.initialScale = buf.readFloat();
        this.growthRate = buf.readFloat();
        this.spinSpeed = buf.readFloat();
        this.facing = SpriteInstance.Facing.values()[buf.readByte() % SpriteInstance.Facing.values().length];
        this.blendMode = SpriteInstance.BlendMode.values()[buf.readByte() % SpriteInstance.BlendMode.values().length];
        this.lifetime = buf.readVarInt();
        this.textureId = buf.readUtf();

        // N-Keyframes aus dem Buffer lesen
        int kfCount = buf.readByte();
        this.keyframes = new ArrayList<>(kfCount);
        for (int i = 0; i < kfCount; i++) {
            float time = (buf.readByte() & 0xFF) / 255.0f;
            int argb = buf.readInt();
            float a = ((argb >> 24) & 0xFF) / 255.0f;
            float r = ((argb >> 16) & 0xFF) / 255.0f;
            float g = ((argb >> 8) & 0xFF) / 255.0f;
            float b = (argb & 0xFF) / 255.0f;
            this.keyframes.add(new SpriteInstance.Keyframe(time, r, g, b, a));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);

        buf.writeFloat((float) velocity.x);
        buf.writeFloat((float) velocity.y);
        buf.writeFloat((float) velocity.z);

        buf.writeFloat(drag);
        buf.writeFloat(gravity);
        buf.writeFloat(initialScale);
        buf.writeFloat(growthRate);
        buf.writeFloat(spinSpeed);

        buf.writeByte(facing.ordinal());
        buf.writeByte(blendMode.ordinal());
        buf.writeVarInt(lifetime);
        buf.writeUtf(textureId != null ? textureId : "minecraft:textures/particle/glint.png");

        // Keyframes extrem komprimiert schreiben (1 Byte Time + 4 Bytes Gepacktes ARGB)
        buf.writeByte(keyframes.size());
        for (SpriteInstance.Keyframe k : keyframes) {
            buf.writeByte((int) (k.time * 255));
            int a = (int) (k.a * 255) & 0xFF;
            int r = (int) (k.r * 255) & 0xFF;
            int g = (int) (k.g * 255) & 0xFF;
            int b = (int) (k.b * 255) & 0xFF;
            int argb = (a << 24) | (r << 16) | (g << 8) | b;
            buf.writeInt(argb);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                SpriteInstance sprite = new SpriteInstance(
                    pos, velocity, drag, gravity,
                    initialScale, growthRate, spinSpeed,
                    facing, textureId, blendMode,
                    lifetime, keyframes
                );
                StonesFxRenderer.spawnSprite(sprite);
            });
        });
        ctx.setPacketHandled(true);
    }
}