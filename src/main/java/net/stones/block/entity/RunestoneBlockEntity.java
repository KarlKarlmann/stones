package net.stones.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.gui.RunestoneMenu;
import net.stones.init.StonesModBlockEntities;
import net.stones.client.renderer.ClientRunestoneTextureManager;

import javax.annotation.Nullable;
import java.util.*;

public class RunestoneBlockEntity extends BlockEntity implements MenuProvider {

    private UUID shrineId;
    private final Set<UUID> clientOwners = new HashSet<>();
    private final List<Vec3> guardianSpots = new ArrayList<>();
    
    // Client-seitiger Cache für die Item-Icons (wird für die Textur-Generierung genutzt)
    private final List<ItemStack> clientInventory = new ArrayList<>();
    private boolean textureDirty = true;

    public RunestoneBlockEntity(BlockPos pos, BlockState state) {
        super(StonesModBlockEntities.RUNESTONE.get(), pos, state);
    }

    public List<Vec3> getGuardianSpots() {
        if (guardianSpots.isEmpty() && !clientOwners.isEmpty()) {
            calculateSpots();
        }
        return guardianSpots;
    }

    private void calculateSpots() {
        guardianSpots.clear();
        if (level == null) return;
        BlockPos center = this.worldPosition;
        int maxGuardians = clientOwners.size();
        int found = 0;
        for (int r = 2; r <= 5 && found < maxGuardians; r++) {
            List<BlockPos> ring = new ArrayList<>();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) == r || Math.abs(z) == r) ring.add(center.offset(x, 0, z));
                }
            }
            Collections.shuffle(ring);
            for (BlockPos pos : ring) {
                if (found >= maxGuardians) break;
                
                boolean placed = false;
                
                // Wir scannen von leicht oben nach unten nach einem soliden Untergrund
                for (int yOff = 3; yOff >= -4; yOff--) {
                    BlockPos candidate = pos.above(yOff);
                    
                    if (level.getBlockState(candidate).isFaceSturdy(level, candidate, Direction.UP)) {
                        BlockPos foot = candidate.above();
                        BlockPos head = candidate.above(2);
                        
                        // Prüfen ob die 2 Blöcke darüber wirklich frei von Hitboxen/Kollisionen sind
                        if (level.getBlockState(foot).getCollisionShape(level, foot).isEmpty() &&
                            level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                            
                            // Leichtes Schweben knapp über dem Boden für den Creepy-Faktor (10cm - 50cm hoch)
                            double hoverOffset = 0.1 + level.random.nextDouble() * 0.4;
                            guardianSpots.add(new Vec3(foot.getX() + 0.5, foot.getY() + hoverOffset, foot.getZ() + 0.5));
                            found++;
                            placed = true;
                            break;
                        }
                    }
                }
                
                // Fallback: Falls wirklich kein Boden da ist, aber zumindest direkt neben dem Stein Platz in der Luft ist
                if (!placed) {
                    BlockPos foot = pos;
                    BlockPos head = pos.above();
                    if (level.getBlockState(foot).getCollisionShape(level, foot).isEmpty() &&
                        level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
                        
                        guardianSpots.add(new Vec3(foot.getX() + 0.5, foot.getY() + 0.2, foot.getZ() + 0.5));
                        found++;
                    }
                }
            }
        }
    }

    public void setShrineId(UUID id) {
        this.shrineId = id;
        setChanged();
        if (level != null) {
            if (level instanceof ServerLevel serverLevel && id != null) {
                ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(id);
                if (shrine != null) shrine.setLocation(GlobalPos.of(level.dimension(), worldPosition));
            }
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public UUID getShrineId() { return shrineId; }
    public Set<UUID> getClientOwners() { return Collections.unmodifiableSet(clientOwners); }
    public List<ItemStack> getClientInventory() { return clientInventory; }
    
    public boolean isTextureDirty() { return textureDirty; }
    public void markTextureClean() { this.textureDirty = false; }

@Override
public Component getDisplayName() { 
    return Component.translatable("container.stones.runestone"); 
}
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (level instanceof ServerLevel serverLevel && shrineId != null) {
            ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(shrineId);
            if (shrine != null) return new RunestoneMenu(containerId, playerInventory, shrine.getInventory(), shrine.getLayout());
        }
        return null;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("shrineId")) this.shrineId = nbt.getUUID("shrineId");
        
        if (nbt.contains("owners", Tag.TAG_LIST)) {
            clientOwners.clear();
            ListTag list = nbt.getList("owners", Tag.TAG_COMPOUND);
            for (Tag t : list) clientOwners.add(((CompoundTag)t).getUUID("uuid"));
        }

        if (nbt.contains("inventory", Tag.TAG_LIST)) {
            clientInventory.clear();
            ListTag list = nbt.getList("inventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                clientInventory.add(ItemStack.of(list.getCompound(i)));
            }
            this.textureDirty = true; // Neu zeichnen, wenn sich das Inventar ändert
        }
        guardianSpots.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.shrineId != null) nbt.putUUID("shrineId", this.shrineId);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (this.shrineId != null) {
            tag.putUUID("shrineId", this.shrineId);
            if (level instanceof ServerLevel serverLevel) {
                ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(shrineId);
                if (shrine != null) {
                    // Owners sync
                    ListTag ownerList = new ListTag();
                    for (UUID owner : shrine.getOwners()) {
                        CompoundTag t = new CompoundTag();
                        t.putUUID("uuid", owner);
                        ownerList.add(t);
                    }
                    tag.put("owners", ownerList);

                    // Inventory sync für den Mirror-Effekt
                    ListTag invList = new ListTag();
                    for (int i = 0; i < shrine.getInventory().getSlots(); i++) {
                        ItemStack s = shrine.getInventory().getStackInSlot(i);
                        if (!s.isEmpty()) invList.add(s.save(new CompoundTag()));
                    }
                    tag.put("inventory", invList);
                }
            }
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}