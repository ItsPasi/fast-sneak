package com.instantsneak.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SelectionRaycastController {
    private static final double REACH_EPSILON = 1.0E-4D;
    private static final double HEIGHT_EPSILON = 1.0E-4D;

    private SelectionRaycastController() {
    }

    public static HitResult pickFromRenderedCameraIfSafe(Entity entity, double hitDistance, float partialTicks, boolean hitFluids) {
        if (!(entity instanceof LocalPlayer player) || !SneakCameraController.shouldUseCameraRaycast(player)) {
            return null;
        }

        Vec3 vanillaEye = entity.getEyePosition(partialTicks);
        double visualOffset = getVisualEyeOffset(player);
        if (visualOffset <= HEIGHT_EPSILON) {
            return null;
        }

        Vec3 raisedEye = vanillaEye.add(0.0D, visualOffset, 0.0D);
        Vec3 look = entity.getViewVector(partialTicks);
        Vec3 end = raisedEye.add(look.scale(hitDistance + visualOffset));

        HitResult visualHit = entity.level().clip(new ClipContext(
                raisedEye,
                end,
                ClipContext.Block.OUTLINE,
                hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity
        ));

        return isWithinVanillaReach(vanillaEye, visualHit, hitDistance, visualOffset) ? visualHit : null;
    }

    private static double getVisualEyeOffset(LocalPlayer player) {
        float renderedEyeHeight = SneakCameraController.getRenderedEyeHeight(player);
        float vanillaEyeHeight = player.getEyeHeight();

        double visualOffset = Math.max(0.0D, renderedEyeHeight - vanillaEyeHeight);
        return Math.min(visualOffset, SneakCameraController.getMaxVisualEyeOffset());
    }

    private static boolean isWithinVanillaReach(Vec3 vanillaEye, HitResult hitResult, double hitDistance, double visualOffset) {
        if (hitResult.getType() == HitResult.Type.MISS) {
            return true;
        }

        double maxDistance = hitDistance + Math.max(0.0D, visualOffset) + REACH_EPSILON;
        return hitResult.getLocation().distanceToSqr(vanillaEye) <= maxDistance * maxDistance + HEIGHT_EPSILON;
    }
}
