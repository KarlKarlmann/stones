package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stones.client.fx.BeamInstance;
import net.stones.client.fx.BeamType;
import net.stones.client.fx.StonesBeamRenderer;

import java.util.function.Supplier;

public class S2CSpawnBeamPacket {

    private final Vec3 start;
    private final Vec3 end;
    private final BeamType beamType;
    private final String textureId;
    private final float coreWidth;
    private final float coronaWidth;
    private final float r, g, b, a;
    private final float uvScrollSpeed;
    private final float uvRepeat;
    private final float helixRadius;
    private final float helixFrequency;
    private final float helixSpeed;
    private final int maxAge;

    public S2CSpawnBeamPacket(BeamInstance instance) {
        this.start = instance.start;
        this.end = instance.end;
        this.beamType = instance.beamType;
        this.textureId = instance.textureId;
        this.coreWidth = instance.coreWidth;
        this.coronaWidth = instance.coronaWidth;
        this.r = instance.r; this.g = instance.g; this.b = instance.b; this.a = instance.a;
        this.uvScrollSpeed = instance.uvScrollSpeed;
        this.uvRepeat = instance.uvRepeat;
        this.helixRadius = instance.helixRadius;
        this.helixFrequency = instance.helixFrequency;
        this.helixSpeed = instance.helixSpeed;
        this.maxAge = instance.maxAge;
    }

    public S2CSpawnBeamPacket(FriendlyByteBuf buf) {
        this.start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.beamType = buf.readEnum(BeamType.class);
        this.textureId = buf.readUtf();
        this.coreWidth = buf.readFloat();
        this.coronaWidth = buf.readFloat();
        this.r = buf.readFloat(); this.g = buf.readFloat(); this.b = buf.readFloat(); this.a = buf.readFloat();
        this.uvScrollSpeed = buf.readFloat();
        this.uvRepeat = buf.readFloat();
        this.helixRadius = buf.readFloat();
        this.helixFrequency = buf.readFloat();
        this.helixSpeed = buf.readFloat();
        this.maxAge = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(start.x); buf.writeDouble(start.y); buf.writeDouble(start.z);
        buf.writeDouble(end.x); buf.writeDouble(end.y); buf.writeDouble(end.z);
        buf.writeEnum(beamType);
        buf.writeUtf(textureId != null ? textureId : "minecraft:textures/particle/glint.png");
        buf.writeFloat(coreWidth);
        buf.writeFloat(coronaWidth);
        buf.writeFloat(r); buf.writeFloat(g); buf.writeFloat(b); buf.writeFloat(a);
        buf.writeFloat(uvScrollSpeed);
        buf.writeFloat(uvRepeat);
        buf.writeFloat(helixRadius);
        buf.writeFloat(helixFrequency);
        buf.writeFloat(helixSpeed);
        buf.writeVarInt(maxAge);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                BeamInstance beam = new BeamInstance(
                    start, end, beamType, textureId, coreWidth, coronaWidth,
                    r, g, b, a, uvScrollSpeed, uvRepeat,
                    helixRadius, helixFrequency, helixSpeed, maxAge
                );
                StonesBeamRenderer.spawnBeam(beam);
            });
        });
        ctx.setPacketHandled(true);
    }
}