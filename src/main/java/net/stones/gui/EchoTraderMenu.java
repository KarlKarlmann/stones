package net.stones.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.stones.StonesMod;
import net.stones.entity.EchoTraderEntity;
import net.stones.init.StonesModMenus;
import net.stones.network.PacketBuyEcho;

public class EchoTraderMenu extends AbstractContainerMenu {

    private final EchoTraderEntity trader;

    // --- GENERATED COORDINATES (From EchoTraderMenuGenerator.py) ---
    // Center: 161, 68 | RadiusStep: 21.0 | YScale: 0.92
    // Offset applied: X+1, Y+1 | Rounding: ENABLED
    private static final int[][] SLOT_POSITIONS = {
        {153, 60}, // Slot 0
        {138, 73}, // Slot 1
        {156, 33}, // Slot 2
        {175, 87}, // Slot 3
        {112, 53}, // Slot 4
        {193, 37}, // Slot 5
        {140, 106}, // Slot 6
        {127, 15}, // Slot 7
        {209, 79}, // Slot 8
        {95, 82}, // Slot 9
        {181, 5}, // Slot 10
        {174, 121}, // Slot 11
        {90, 26} // Slot 12
    };

    // Player Inventory Offsets (Offset applied: X+1, Y+1)
    private static final int INV_X_START = 36;
    private static final int INV_Y_MAIN = 144;
    private static final int INV_Y_HOTBAR = 202;

    public EchoTraderMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, (EchoTraderEntity) playerInv.player.level().getEntity(extraData.readInt()));
    }

    public EchoTraderMenu(int containerId, Inventory playerInv, EchoTraderEntity trader) {
        super(StonesModMenus.ECHO_TRADER_MENU.get(), containerId);
        this.trader = trader;

        SimpleContainer shopInv = new SimpleContainer(13);

        for (int i = 0; i < 13; i++) {
            this.addSlot(new Slot(shopInv, i, SLOT_POSITIONS[i][0], SLOT_POSITIONS[i][1]) {
                @Override public boolean mayPickup(Player p) { return false; }
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });

            if (trader != null && i < trader.getStock().size()) {
                shopInv.setItem(i, trader.getStock().get(i).copy());
            }
        }

        addPlayerInventory(playerInv);
    }

    private void addPlayerInventory(Inventory playerInv) {
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, INV_X_START + j * 18, INV_Y_MAIN + i * 18));
            }
        }
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInv, k, INV_X_START + k * 18, INV_Y_HOTBAR));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return trader != null && trader.isAlive() && player.distanceToSqr(trader) < 64.0;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < 13) {
            if (player.level().isClientSide) {
                StonesMod.PACKET_HANDLER.sendToServer(new PacketBuyEcho(slotId, trader.getId()));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }
}
