package com.instantsneak.mixin.client;

import com.instantsneak.client.SneakCameraController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private static final float instantSneak$TRANSITION_FINISH_EPSILON = 1.0E-4F;
    @Unique
    private static final float instantSneak$TARGET_EPSILON = 0.006F;

    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Unique
    private boolean instantSneak$deepTransitionActive;
    @Unique
    private boolean instantSneak$lastTargetWasStanding;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void instantSneak$beforeUpdate(CallbackInfo ci) {
        instantSneak$applyCameraRules(this.entity);
    }

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void instantSneak$afterUpdate(CallbackInfo ci) {
        instantSneak$applyCameraRules(this.entity);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"),
            require = 0
    )
    private float instantSneak$redirectTickEyeHeight(Entity focusedEntity) {
        Float override = SneakCameraController.getCameraEyeHeightOverride(focusedEntity);
        return override != null ? override : focusedEntity.getEyeHeight();
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void instantSneak$afterTick(CallbackInfo ci) {
        instantSneak$applyCameraRules(this.entity);
    }

    @Unique
    private void instantSneak$applyCameraRules(Entity focusedEntity) {
        if (focusedEntity == null) {
            instantSneak$resetAndRecord(null);
            return;
        }

        float shallowHeight = SneakCameraController.getShallowSneakEyeHeight();
        Float override = SneakCameraController.getCameraEyeHeightOverride(focusedEntity);

        if (override != null) {
            instantSneak$applyTarget(focusedEntity, override, shallowHeight);
            return;
        }

        if (!SneakCameraController.isCustomCameraActive(focusedEntity)) {
            instantSneak$resetAndRecord(focusedEntity);
            return;
        }

        instantSneak$applyTarget(focusedEntity, SneakCameraController.STANDING_EYE_HEIGHT, shallowHeight);
    }

    @Unique
    private void instantSneak$applyTarget(Entity focusedEntity, float target, float shallowHeight) {
        boolean shallowTarget = instantSneak$isShallowTarget(target, shallowHeight);
        boolean standingTarget = target > shallowHeight + instantSneak$TARGET_EPSILON;
        boolean deepTarget = target < shallowHeight - instantSneak$TARGET_EPSILON;
        boolean returningFromDeep = this.instantSneak$deepTransitionActive || instantSneak$isBelowShallow(shallowHeight);

        if (deepTarget) {
            instantSneak$handleDeepTarget(target, shallowHeight);
            this.instantSneak$lastTargetWasStanding = false;
            instantSneak$record(focusedEntity);
            return;
        }

        if (instantSneak$shouldSnapInterruptedShallowTarget(shallowTarget)) {
            instantSneak$snapTo(target);
            this.instantSneak$deepTransitionActive = false;
            this.instantSneak$lastTargetWasStanding = false;
            instantSneak$record(focusedEntity);
            return;
        }

        if (returningFromDeep) {
            instantSneak$handleDeepTransition(target);
            this.instantSneak$lastTargetWasStanding = standingTarget;
            instantSneak$record(focusedEntity);
            return;
        }

        if (!SneakCameraController.shouldAnimateShallowSneak() && instantSneak$shouldSnapShallow(target, shallowHeight)) {
            instantSneak$snapTo(target);
        }

        this.instantSneak$deepTransitionActive = false;
        this.instantSneak$lastTargetWasStanding = standingTarget;
        instantSneak$record(focusedEntity);
    }

    @Unique
    private void instantSneak$handleDeepTarget(float target, float shallowHeight) {
        if (!SneakCameraController.shouldAnimateDeepSneak()) {
            instantSneak$snapTo(target);
            this.instantSneak$deepTransitionActive = false;
        } else if (instantSneak$isBelowShallow(shallowHeight)) {
            this.instantSneak$deepTransitionActive = true;
        }
    }

    @Unique
    private void instantSneak$handleDeepTransition(float target) {
        if (SneakCameraController.shouldAnimateDeepSneak()) {
            this.instantSneak$deepTransitionActive = true;
            instantSneak$finishTransitionIfClose(target);
        } else {
            instantSneak$snapTo(target);
            this.instantSneak$deepTransitionActive = false;
        }
    }

    @Unique
    private boolean instantSneak$shouldSnapInterruptedShallowTarget(boolean shallowTarget) {
        return !SneakCameraController.shouldAnimateShallowSneak()
                && shallowTarget
                && this.instantSneak$lastTargetWasStanding;
    }

    @Unique
    private boolean instantSneak$isShallowTarget(float target, float shallowHeight) {
        return Math.abs(target - shallowHeight) <= instantSneak$TARGET_EPSILON;
    }

    @Unique
    private boolean instantSneak$shouldSnapShallow(float target, float shallowHeight) {
        boolean shallowTarget = instantSneak$isShallowTarget(target, shallowHeight);
        boolean standingTarget = target > shallowHeight + instantSneak$TARGET_EPSILON;

        return shallowTarget || (standingTarget && instantSneak$isBelowStanding());
    }

    @Unique
    private boolean instantSneak$isBelowShallow(float shallowHeight) {
        return this.eyeHeight < shallowHeight - instantSneak$TARGET_EPSILON
                || this.eyeHeightOld < shallowHeight - instantSneak$TARGET_EPSILON;
    }

    @Unique
    private boolean instantSneak$isBelowStanding() {
        return this.eyeHeight < SneakCameraController.STANDING_EYE_HEIGHT - instantSneak$TARGET_EPSILON
                || this.eyeHeightOld < SneakCameraController.STANDING_EYE_HEIGHT - instantSneak$TARGET_EPSILON;
    }

    @Unique
    private void instantSneak$finishTransitionIfClose(float target) {
        if (Math.abs(this.eyeHeight - target) <= instantSneak$TRANSITION_FINISH_EPSILON
                && Math.abs(this.eyeHeightOld - target) <= instantSneak$TRANSITION_FINISH_EPSILON) {
            instantSneak$snapTo(target);
            this.instantSneak$deepTransitionActive = false;
        }
    }

    @Unique
    private void instantSneak$snapTo(float target) {
        this.eyeHeight = target;
        this.eyeHeightOld = target;
    }

    @Unique
    private void instantSneak$resetAndRecord(Entity focusedEntity) {
        this.instantSneak$deepTransitionActive = false;
        this.instantSneak$lastTargetWasStanding = false;
        instantSneak$record(focusedEntity);
    }

    @Unique
    private void instantSneak$record(Entity focusedEntity) {
        SneakCameraController.recordRenderedEyeHeight(focusedEntity, this.eyeHeight);
    }
}
