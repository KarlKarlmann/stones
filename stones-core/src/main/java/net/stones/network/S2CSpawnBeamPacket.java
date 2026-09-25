package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stones.client.fx.BeamInstance;
import net.stones.client.fx.SpriteInstance;
import net.stones.client.fx.StonesBeamRenderer;

import java.util.function.Supplier;

public class S2CSpawnBeamPacket {

    private final Vec3 start;
    private final Vec3 end;
    private final float width;
    private final String textureId;
    private final float r, g, b, a;
    private final float uvScrollSpeed;
    private final float uvRepeat;
    private final SpriteInstance.BlendMode blendMode;
    private final int lifetime;

    public S2CSpawnBeamPacket(Vec3 start, Vec3 end, float width, String textureId,
                              float r, float g, float b, float a,
                              float uvScrollSpeed, float uvRepeat,
                              SpriteInstance.BlendMode blendMode, int lifetime) {
        this.start = start;
        this.end = end;
        this.width = width;
        this.textureId = textureId;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.uvScrollSpeed = uvScrollSpeed;
        this.uvRepeat = uvRepeat;
        this.blendMode = blendMode;
        this.lifetime = lifetime;
    }

    public S2CSpawnBeamPacket(FriendlyByteBuf buf) {
        this.start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.width = buf.readFloat();
        this.textureId = buf.readUtf();
        this.r = buf.readFloat(); this.g = buf.readFloat(); this.b = buf.readFloat(); this.a = buf.readFloat();
        this.uvScrollSpeed = buf.readFloat();
        this.uvRepeat = buf.readFloat();
        this.blendMode = SpriteInstance.BlendMode.values()[buf.readByte() % SpriteInstance.BlendMode.values().length];
        this.lifetime = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(start.x); buf.writeDouble(start.y); buf.writeDouble(start.z);
        buf.writeDouble(end.x); buf.writeDouble(end.y); buf.writeDouble(end.z);
        buf.writeFloat(width);
        buf.writeUtf(textureId != null ? textureId : "minecraft:textures/particle/glint.png");
        buf.writeFloat(r); buf.writeFloat(g); buf.writeFloat(b); buf.writeFloat(a);
        buf.writeFloat(uvScrollSpeed);
        buf.writeFloat(uvRepeat);
        buf.writeByte(blendMode.ordinal());
        buf.writeVarInt(lifetime);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                BeamInstance beam = new BeamInstance(
                    start, end, width, textureId, r, g, b, a, uvScrollSpeed, uvRepeat, blendMode, lifetime
                );
                StonesBeamRenderer.spawnBeam(beam);
            });
        });
        ctx.setPacketHandled(true);
    }
}