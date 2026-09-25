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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.gui.RunestoneMenu;
import net.stones.init.StonesModBlockEntities;

import javax.annotation.Nullable;
import java.util.*;

public class RunestoneBlockEntity extends BlockEntity implements MenuProvider {

    private UUID shrineId;
    private final Set<UUID> clientOwners = new HashSet<>();
    private final List<Vec3> guardianSpots = new ArrayList<>();
	private long lastAttackWarningTick = 0;    
    // Client-seitiger Cache für die Item-Icons (wird für die Textur-Generierung genutzt)
    private final List<ItemStack> clientInventory = new ArrayList<>();
    private boolean textureDirty = true;
    
    // Nur maxLevel wird im NBT gehalten. Standard ist 100. Kein SlotCount-Quatsch mehr!
    private int clientMaxLevel = 100;

    public RunestoneBlockEntity(BlockPos pos, BlockState state) {
        super(StonesModBlockEntities.RUNESTONE.get(), pos, state);
    }

	public List<Vec3> getGuardianSpots() {
		if (guardianSpots.isEmpty()) {
			if (level instanceof ServerLevel serverLevel && shrineId != null) {
				ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(shrineId);
				if (shrine != null && !shrine.getOwners().isEmpty()) {
					calculateSpotsForCount(shrine.getOwners().size());
				}
			} else if (!clientOwners.isEmpty()) {
				calculateSpotsForCount(clientOwners.size());
			}
		}
		return guardianSpots;
	}

	private void calculateSpotsForCount(int maxGuardians) {
		guardianSpots.clear();
		if (level == null) return;
		BlockPos center = this.worldPosition;
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
				for (int yOff = 3; yOff >= -4; yOff--) {
					BlockPos candidate = pos.above(yOff);
					if (level.getBlockState(candidate).isFaceSturdy(level, candidate, Direction.UP)) {
						BlockPos foot = candidate.above();
						BlockPos head = candidate.above(2);
						if (level.getBlockState(foot).getCollisionShape(level, foot).isEmpty() &&
							level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
							
							double hoverOffset = 0.1 + level.random.nextDouble() * 0.4;
							guardianSpots.add(new Vec3(foot.getX() + 0.5, foot.getY() + hoverOffset, foot.getZ() + 0.5));
							found++;
							placed = true;
							break;
						}
					}
				}
				
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

	public long getLastAttackWarningTick() {
		return this.lastAttackWarningTick;
	}

	public void setLastAttackWarningTick(long tick) {
		this.lastAttackWarningTick = tick;
		this.setChanged(); // Funktioniert hier sauber, da BlockEntity!
	}

    public void setShrineId(UUID id) {
        this.shrineId = id;
        if (level instanceof ServerLevel serverLevel && id != null) {
            ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(id);
            if (shrine != null) {
                shrine.setLocation(GlobalPos.of(level.dimension(), worldPosition));
                this.clientMaxLevel = shrine.getMaxLevel();
            }
        }
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public UUID getShrineId() { return shrineId; }
    public Set<UUID> getClientOwners() { return Collections.unmodifiableSet(clientOwners); }
    public List<ItemStack> getClientInventory() { return clientInventory; }
    
    public boolean isTextureDirty() { return textureDirty; }
    public void markTextureClean() { this.textureDirty = false; }
    
    public int getClientMaxLevel() { return this.clientMaxLevel; }

    @Override
    public Component getDisplayName() { 
        return Component.translatable("container.stones.runestone"); 
    }

    public void openMenu(ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel && shrineId != null) {
            ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(shrineId);
            if (shrine != null) {
                NetworkHooks.openScreen(player, this, buffer -> {
                    buffer.writeInt(shrine.getInventory().getSlots());
                    buffer.writeInt(shrine.getLayout().size());
                    for (ShrineInstance.SlotConfig cfg : shrine.getLayout()) {
                        buffer.writeEnum(cfg.type);
                        buffer.writeInt(cfg.requiredLevel);
                        buffer.writeInt(cfg.inventoryIndex);
                    }
                    buffer.writeUUID(this.shrineId);
                });
            }
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (level instanceof ServerLevel serverLevel && shrineId != null) {
            ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(shrineId);
            if (shrine != null) return new RunestoneMenu(containerId, playerInventory, shrine.getInventory(), shrine.getLayout(), this.shrineId);
        }
        return null;
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("shrineId")) this.shrineId = nbt.getUUID("shrineId");
        
        // Liest ausschließlich maxLevel aus den NBTs. Fehlt es, wird 100 genommen
        if (nbt.contains("maxLevel")) {
            this.clientMaxLevel = nbt.getInt("maxLevel");
        } else {
            this.clientMaxLevel = 100;
        }

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
            this.textureDirty = true;
        }
        guardianSpots.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.shrineId != null) nbt.putUUID("shrineId", this.shrineId);
        nbt.putInt("maxLevel", this.clientMaxLevel);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (this.shrineId != null) {
            tag.putUUID("shrineId", this.shrineId);
            tag.putInt("maxLevel", this.clientMaxLevel);
            
            if (level instanceof ServerLevel serverLevel) {
                ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(shrineId);
                if (shrine != null) {
                    tag.putInt("maxLevel", shrine.getMaxLevel());
                    
                    ListTag ownerList = new ListTag();
                    for (UUID owner : shrine.getOwners()) {
                        CompoundTag t = new CompoundTag();
                        t.putUUID("uuid", owner);
                        ownerList.add(t);
                    }
                    tag.put("owners", ownerList);

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