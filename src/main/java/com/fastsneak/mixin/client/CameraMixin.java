package com.fastsneak.mixin.client;

import com.fastsneak.client.SneakCameraController;
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
    private static final float fastSneak$TRANSITION_FINISH_EPSILON = 1.0E-4F;
    @Unique
    private static final float fastSneak$TARGET_EPSILON = 0.006F;

    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Unique
    private boolean fastSneak$deepTransitionActive;
    @Unique
    private boolean fastSneak$lastTargetWasStanding;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void fastSneak$beforeUpdate(CallbackInfo ci) {
        fastSneak$applyCameraRules(this.entity);
    }

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void fastSneak$afterUpdate(CallbackInfo ci) {
        fastSneak$applyCameraRules(this.entity);
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"),
            require = 0
    )
    private float fastSneak$redirectTickEyeHeight(Entity focusedEntity) {
        Float override = SneakCameraController.getCameraEyeHeightOverride(focusedEntity);
        return override != null ? override : focusedEntity.getEyeHeight();
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void fastSneak$afterTick(CallbackInfo ci) {
        fastSneak$applyCameraRules(this.entity);
    }

    @Unique
    private void fastSneak$applyCameraRules(Entity focusedEntity) {
        if (focusedEntity == null) {
            fastSneak$resetAndRecord(null);
            return;
        }

        float shallowHeight = SneakCameraController.getShallowSneakEyeHeight();
        Float override = SneakCameraController.getCameraEyeHeightOverride(focusedEntity);

        if (override != null) {
            fastSneak$applyTarget(focusedEntity, override, shallowHeight);
            return;
        }

        if (!SneakCameraController.isCustomCameraActive(focusedEntity)) {
            fastSneak$resetAndRecord(focusedEntity);
            return;
        }

        fastSneak$applyTarget(focusedEntity, SneakCameraController.STANDING_EYE_HEIGHT, shallowHeight);
    }

    @Unique
    private void fastSneak$applyTarget(Entity focusedEntity, float target, float shallowHeight) {
        boolean shallowTarget = fastSneak$isShallowTarget(target, shallowHeight);
        boolean standingTarget = target > shallowHeight + fastSneak$TARGET_EPSILON;
        boolean deepTarget = target < shallowHeight - fastSneak$TARGET_EPSILON;
        boolean returningFromDeep = this.fastSneak$deepTransitionActive || fastSneak$isBelowShallow(shallowHeight);

        if (deepTarget) {
            fastSneak$handleDeepTarget(target, shallowHeight);
            this.fastSneak$lastTargetWasStanding = false;
            fastSneak$record(focusedEntity);
            return;
        }

        if (fastSneak$shouldSnapInterruptedShallowTarget(shallowTarget)) {
            fastSneak$snapTo(target);
            this.fastSneak$deepTransitionActive = false;
            this.fastSneak$lastTargetWasStanding = false;
            fastSneak$record(focusedEntity);
            return;
        }

        if (returningFromDeep) {
            fastSneak$handleDeepTransition(target);
            this.fastSneak$lastTargetWasStanding = standingTarget;
            fastSneak$record(focusedEntity);
            return;
        }

        if (!SneakCameraController.shouldAnimateShallowSneak() && fastSneak$shouldSnapShallow(target, shallowHeight)) {
            fastSneak$snapTo(target);
        }

        this.fastSneak$deepTransitionActive = false;
        this.fastSneak$lastTargetWasStanding = standingTarget;
        fastSneak$record(focusedEntity);
    }

    @Unique
    private void fastSneak$handleDeepTarget(float target, float shallowHeight) {
        if (!SneakCameraController.shouldAnimateDeepSneak()) {
            fastSneak$snapTo(target);
            this.fastSneak$deepTransitionActive = false;
        } else if (fastSneak$isBelowShallow(shallowHeight)) {
            this.fastSneak$deepTransitionActive = true;
        }
    }

    @Unique
    private void fastSneak$handleDeepTransition(float target) {
        if (SneakCameraController.shouldAnimateDeepSneak()) {
            this.fastSneak$deepTransitionActive = true;
            fastSneak$finishTransitionIfClose(target);
        } else {
            fastSneak$snapTo(target);
            this.fastSneak$deepTransitionActive = false;
        }
    }

    @Unique
    private boolean fastSneak$shouldSnapInterruptedShallowTarget(boolean shallowTarget) {
        return !SneakCameraController.shouldAnimateShallowSneak()
                && shallowTarget
                && this.fastSneak$lastTargetWasStanding;
    }

    @Unique
    private boolean fastSneak$isShallowTarget(float target, float shallowHeight) {
        return Math.abs(target - shallowHeight) <= fastSneak$TARGET_EPSILON;
    }

    @Unique
    private boolean fastSneak$shouldSnapShallow(float target, float shallowHeight) {
        boolean shallowTarget = fastSneak$isShallowTarget(target, shallowHeight);
        boolean standingTarget = target > shallowHeight + fastSneak$TARGET_EPSILON;

        return shallowTarget || (standingTarget && fastSneak$isBelowStanding());
    }

    @Unique
    private boolean fastSneak$isBelowShallow(float shallowHeight) {
        return this.eyeHeight < shallowHeight - fastSneak$TARGET_EPSILON
                || this.eyeHeightOld < shallowHeight - fastSneak$TARGET_EPSILON;
    }

    @Unique
    private boolean fastSneak$isBelowStanding() {
        return this.eyeHeight < SneakCameraController.STANDING_EYE_HEIGHT - fastSneak$TARGET_EPSILON
                || this.eyeHeightOld < SneakCameraController.STANDING_EYE_HEIGHT - fastSneak$TARGET_EPSILON;
    }

    @Unique
    private void fastSneak$finishTransitionIfClose(float target) {
        if (Math.abs(this.eyeHeight - target) <= fastSneak$TRANSITION_FINISH_EPSILON
                && Math.abs(this.eyeHeightOld - target) <= fastSneak$TRANSITION_FINISH_EPSILON) {
            fastSneak$snapTo(target);
            this.fastSneak$deepTransitionActive = false;
        }
    }

    @Unique
    private void fastSneak$snapTo(float target) {
        this.eyeHeight = target;
        this.eyeHeightOld = target;
    }

    @Unique
    private void fastSneak$resetAndRecord(Entity focusedEntity) {
        this.fastSneak$deepTransitionActive = false;
        this.fastSneak$lastTargetWasStanding = false;
        fastSneak$record(focusedEntity);
    }

    @Unique
    private void fastSneak$record(Entity focusedEntity) {
        SneakCameraController.recordRenderedEyeHeight(focusedEntity, this.eyeHeight);
    }
}
