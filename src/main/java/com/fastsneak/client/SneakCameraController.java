package com.fastsneak.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SneakCameraController {
    public static final float STANDING_EYE_HEIGHT = 1.62F;
    public static final float MODERN_SNEAK_EYE_HEIGHT = 1.27F;

    private static final double BOX_INSET = 1.0E-4D;
    private static final double STANDING_HEIGHT = 1.8D - BOX_INSET;
    private static final double CAMERA_PROBE_INSET = 0.02D;
    private static final double CAMERA_PROBE_TOP_PADDING = 0.03D;
    private static final double LOWER_SPACE_TOP_PADDING = 0.015D;
    private static final double INPUT_LOOKAHEAD = 0.075D;
    private static final double MIN_HORIZONTAL_LENGTH = 1.0E-5D;

    private static final float CAMERA_HEIGHT_EPSILON = 1.0E-4F;

    private static Entity lastCameraEntity;
    private static LocalPlayer ceilingSneakLockPlayer;
    private static float lastRenderedEyeHeight = Float.NaN;

    private SneakCameraController() {
    }

    public static Float getCameraEyeHeightOverride(Entity entity) {
        if (!(entity instanceof LocalPlayer player) || !canApplyToPlayer(player)) {
            clearCeilingSneakLock(entity);
            return null;
        }

        boolean sneakKeyHeld = isSneakKeyHeld(player);
        boolean crouching = player.getPose() == Pose.CROUCHING;
        boolean ceilingBlocked = needsDeepSneakCamera(player);

        if (!sneakKeyHeld && !crouching) {
            if (isCeilingSneakLocked(player) && ceilingBlocked) {
                return MODERN_SNEAK_EYE_HEIGHT;
            }

            clearCeilingSneakLock(player);
            return null;
        }

        if (ceilingBlocked) {
            lockCeilingSneak(player);
            return MODERN_SNEAK_EYE_HEIGHT;
        }

        clearCeilingSneakLock(player);

        if (!sneakKeyHeld) {
            return getReleasedSneakEyeHeight(player, crouching);
        }

        return getShallowSneakEyeHeight();
    }

    public static float getShallowSneakEyeHeight() {
        FastSneakConfig.SneakHeight height = FastSneakConfig.get().sneakHeight;
        return height != null ? height.eyeHeight() : FastSneakConfig.SneakHeight.PRE_1_9.eyeHeight();
    }

    public static double getMaxVisualEyeOffset() {
        return Math.max(0.0D, getShallowSneakEyeHeight() - MODERN_SNEAK_EYE_HEIGHT);
    }

    public static boolean shouldAnimateShallowSneak() {
        return FastSneakConfig.get().animateShallowSneak;
    }

    public static boolean shouldAnimateDeepSneak() {
        return FastSneakConfig.get().animateDeepSneak;
    }

    public static boolean isCustomCameraActive(Entity entity) {
        return entity instanceof LocalPlayer player && canApplyToPlayer(player);
    }

    public static void recordRenderedEyeHeight(Entity entity, float eyeHeight) {
        lastCameraEntity = entity;
        lastRenderedEyeHeight = eyeHeight;
    }

    public static float getRenderedEyeHeight(LocalPlayer player) {
        if (lastCameraEntity == player && !Float.isNaN(lastRenderedEyeHeight)) {
            return lastRenderedEyeHeight;
        }

        Float override = getCameraEyeHeightOverride(player);
        return override != null ? override : player.getEyeHeight();
    }

    public static boolean shouldUseCameraRaycast(LocalPlayer player) {
        if (!canApplyToPlayer(player) || getMaxVisualEyeOffset() <= CAMERA_HEIGHT_EPSILON) {
            return false;
        }

        Float override = getCameraEyeHeightOverride(player);
        if (override == null) {
            return false;
        }

        float renderedEyeHeight = getRenderedEyeHeight(player);
        float vanillaPickEyeHeight = isVanillaSneakingForPick(player) ? MODERN_SNEAK_EYE_HEIGHT : STANDING_EYE_HEIGHT;
        boolean renderedDiffersFromVanilla = Math.abs(renderedEyeHeight - vanillaPickEyeHeight) > CAMERA_HEIGHT_EPSILON;
        boolean overrideMatchesShallow = Math.abs(override - getShallowSneakEyeHeight()) <= CAMERA_HEIGHT_EPSILON;

        return renderedDiffersFromVanilla || overrideMatchesShallow;
    }

    public static boolean isSneakKeyHeld(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == player) {
            return minecraft.options.keyShift.isDown();
        }

        return player.isShiftKeyDown();
    }

    private static float getReleasedSneakEyeHeight(LocalPlayer player, boolean crouching) {
        if (crouching && !canStandSafely(player)) {
            return MODERN_SNEAK_EYE_HEIGHT;
        }

        return STANDING_EYE_HEIGHT;
    }

    private static void lockCeilingSneak(LocalPlayer player) {
        ceilingSneakLockPlayer = player;
    }

    private static boolean isCeilingSneakLocked(LocalPlayer player) {
        return ceilingSneakLockPlayer == player;
    }

    private static void clearCeilingSneakLock(Entity entity) {
        if (entity == null || ceilingSneakLockPlayer == entity) {
            ceilingSneakLockPlayer = null;
        }
    }

    private static boolean canApplyToPlayer(LocalPlayer player) {
        return isCameraViewEnabled(player)
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isSwimming()
                && !player.isVisuallySwimming()
                && !player.isVisuallyCrawling()
                && !player.isPassenger();
    }

    private static boolean isCameraViewEnabled(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != player) {
            return false;
        }

        return minecraft.options.getCameraType().isFirstPerson() || FastSneakConfig.get().affectThirdPerson;
    }

    private static boolean isVanillaSneakingForPick(LocalPlayer player) {
        return player.getPose() == Pose.CROUCHING || isSneakKeyHeld(player);
    }

    private static boolean needsDeepSneakCamera(LocalPlayer player) {
        if (getShallowSneakEyeHeight() <= MODERN_SNEAK_EYE_HEIGHT + CAMERA_HEIGHT_EPSILON) {
            return false;
        }

        AABB body = player.getBoundingBox();
        boolean predictedBlockage = player.onGround() && isInputPredictedCameraBlocked(player, body);

        return !canStandSafely(player)
                || isCameraProbeBlocked(player, body)
                || predictedBlockage;
    }

    private static boolean isInputPredictedCameraBlocked(LocalPlayer player, AABB body) {
        Vec3 offset = getInputLookahead(player);
        return offset != Vec3.ZERO && isOffsetCameraProbeBlocked(player, body, offset.x, offset.z);
    }

    private static Vec3 getInputLookahead(LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != player) {
            return Vec3.ZERO;
        }

        double forward = getForwardInput(minecraft);
        double left = getLeftInput(minecraft);
        if (forward == 0.0D && left == 0.0D) {
            return Vec3.ZERO;
        }

        Vec3 forwardDirection = getHorizontalFacing(player);
        double forwardX = forwardDirection.x;
        double leftX = forwardDirection.z;
        double leftZ = -forwardX;

        double offsetX = forwardX * forward + leftX * left;
        double offsetZ = leftX * forward + leftZ * left;
        double offsetLength = horizontalLength(offsetX, offsetZ);
        if (offsetLength <= MIN_HORIZONTAL_LENGTH) {
            return Vec3.ZERO;
        }

        return new Vec3(offsetX / offsetLength * INPUT_LOOKAHEAD, 0.0D, offsetZ / offsetLength * INPUT_LOOKAHEAD);
    }

    private static double getForwardInput(Minecraft minecraft) {
        double input = 0.0D;

        if (minecraft.options.keyUp.isDown()) {
            input += 1.0D;
        }
        if (minecraft.options.keyDown.isDown()) {
            input -= 1.0D;
        }

        return input;
    }

    private static double getLeftInput(Minecraft minecraft) {
        double input = 0.0D;

        if (minecraft.options.keyLeft.isDown()) {
            input += 1.0D;
        }
        if (minecraft.options.keyRight.isDown()) {
            input -= 1.0D;
        }

        return input;
    }

    private static Vec3 getHorizontalFacing(LocalPlayer player) {
        double yawRadians = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private static boolean canStandSafely(LocalPlayer player) {
        AABB body = player.getBoundingBox();
        if (isTooNarrow(body)) {
            return false;
        }

        AABB standingBody = new AABB(
                body.minX + BOX_INSET,
                player.getY() + BOX_INSET,
                body.minZ + BOX_INSET,
                body.maxX - BOX_INSET,
                player.getY() + STANDING_HEIGHT,
                body.maxZ - BOX_INSET
        );

        return player.level().noBlockCollision(null, standingBody);
    }

    private static boolean isCameraProbeBlocked(LocalPlayer player, AABB body) {
        return isOffsetCameraProbeBlocked(player, body, 0.0D, 0.0D);
    }

    private static boolean isOffsetCameraProbeBlocked(LocalPlayer player, AABB body, double offsetX, double offsetZ) {
        double minX = body.minX + CAMERA_PROBE_INSET + offsetX;
        double maxX = body.maxX - CAMERA_PROBE_INSET + offsetX;
        double minZ = body.minZ + CAMERA_PROBE_INSET + offsetZ;
        double maxZ = body.maxZ - CAMERA_PROBE_INSET + offsetZ;

        if (minX >= maxX || minZ >= maxZ) {
            return true;
        }

        AABB cameraSpace = new AABB(
                minX,
                player.getY() + MODERN_SNEAK_EYE_HEIGHT,
                minZ,
                maxX,
                player.getY() + getShallowSneakEyeHeight() + CAMERA_PROBE_TOP_PADDING,
                maxZ
        );

        return isCeilingOnlyBlockage(player, cameraSpace, minX, maxX, minZ, maxZ);
    }

    private static boolean isCeilingOnlyBlockage(LocalPlayer player, AABB cameraSpace, double minX, double maxX, double minZ, double maxZ) {
        if (player.level().noBlockCollision(null, cameraSpace)) {
            return false;
        }

        AABB lowerSpace = new AABB(
                minX,
                player.getY() + BOX_INSET,
                minZ,
                maxX,
                player.getY() + MODERN_SNEAK_EYE_HEIGHT - LOWER_SPACE_TOP_PADDING,
                maxZ
        );

        return player.level().noBlockCollision(null, lowerSpace);
    }

    private static double horizontalLength(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }

    private static boolean isTooNarrow(AABB box) {
        return box.maxX - box.minX <= BOX_INSET * 2.0D
                || box.maxZ - box.minZ <= BOX_INSET * 2.0D;
    }
}
