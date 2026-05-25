package net.stones.init;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.stones.StonesMod;
import net.stones.gui.EchoTraderMenu;
import net.stones.gui.RunestoneMenu;

public class StonesModMenus {
    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.MENU_TYPES, StonesMod.MODID);

    // Wir nutzen IForgeMenuType.create, um extra Daten (z.B. die Slot-Anzahl) beim Öffnen senden zu können
    public static final RegistryObject<MenuType<RunestoneMenu>> RUNESTONE_MENU = REGISTRY.register("runestone",
            () -> IForgeMenuType.create(RunestoneMenu::new));

    // Menü für den Echo Trader
    public static final RegistryObject<MenuType<EchoTraderMenu>> ECHO_TRADER_MENU = REGISTRY.register("echo_trader_menu",
            () -> IForgeMenuType.create(EchoTraderMenu::new));
}