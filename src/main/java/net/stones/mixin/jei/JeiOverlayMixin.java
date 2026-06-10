package net.stones.mixin.jei;

import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public class JeiOverlayMixin {

    // require = 0 ist der Failsafe: Wenn die Methode sich im nächsten JEI Update ändert,
    // wird der Mixin einfach stillschweigend deaktiviert, OHNE das Spiel zum Absturz zu bringen.
    @Inject(method = "getSearchAndConfigArea", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetSearchAndConfigArea(ImmutableRect2i displayArea, boolean searchBarCentered, IGuiProperties guiProperties, CallbackInfoReturnable<ImmutableRect2i> cir) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen) {
            ImmutableRect2i original = cir.getReturnValue();
            cir.setReturnValue(original.moveUp(24));
        }
    }

    @Inject(method = "getAvailableContentsArea", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetAvailableContentsArea(ImmutableRect2i displayArea, boolean searchBarCentered, CallbackInfoReturnable<ImmutableRect2i> cir) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen) {
            ImmutableRect2i original = cir.getReturnValue();
            cir.setReturnValue(original.cropBottom(24));
        }
    }
}