package com.fastsneak.mixin.client;

import com.fastsneak.client.SelectionRaycastController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityPickMixin {
    @Inject(method = "pick", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastSneak$pickFromSneakCamera(
            double hitDistance,
            float partialTicks,
            boolean hitFluids,
            CallbackInfoReturnable<HitResult> cir
    ) {
        HitResult renderedCameraHit = SelectionRaycastController.pickFromRenderedCameraIfSafe(
                (Entity) (Object) this,
                hitDistance,
                partialTicks,
                hitFluids
        );

        if (renderedCameraHit != null) {
            cir.setReturnValue(renderedCameraHit);
        }
    }
}
