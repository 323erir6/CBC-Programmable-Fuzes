package ua.ivan.cbcprogrammablefuzes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

public final class CorrectionFlightController {
    private static final String ROOT = "cbc_programmable_fuzes_tail_flight";
    private static final byte STATE_ABSENT = 1;
    private static final byte STATE_ATTACHED = 2;
    private static final double EPSILON = 1.0e-9;
    private static final double FORCE_FILTER = 0.20;
    private static final double MAX_FORCE_SAMPLE = 0.5;
    private static final double MAX_FORCE_COMPENSATION_FRACTION = 0.25;
    /** Profile/induced drag paid for each radian through which the fins turn the flight path. */
    private static final double TURN_DRAG_PER_RADIAN = 0.04;
    /** Pitch-up needs more lift than a level/lateral correction and consequently produces more drag. */
    private static final double UPWARD_TURN_DRAG_MULTIPLIER = 1.5;

    private CorrectionFlightController() {}

    public static void attach(AbstractCannonProjectile projectile, CorrectionSettings settings) {
        CompoundTag data = new CompoundTag();
        data.putString("Channel", settings.channel());
        data.putBoolean("HasTarget", settings.hasTarget());
        data.putDouble("TargetX", settings.targetX());
        data.putDouble("TargetY", settings.targetY());
        data.putDouble("TargetZ", settings.targetZ());
        data.putBoolean("PersistentProjectile", settings.persistentProjectile());
        data.putDouble("SpentTurn", 0.0);
        data.putDouble("SpentDisplacement", 0.0);
        data.putBoolean("RangeApplied", false);
        data.putBoolean("HasPreviousCommand", false);
        data.putDouble("FilteredForceX", 0.0);
        data.putDouble("FilteredForceY", -0.05);
        data.putDouble("FilteredForceZ", 0.0);
        projectile.getPersistentData().put(ROOT, data);
        if (projectile instanceof CorrectionRuntimeState runtime) {
            runtime.cbcProgrammableFuzes$setCorrectionState(STATE_ATTACHED, data);
        }
    }

    public static boolean isAttached(AbstractCannonProjectile projectile) {
        return flightData(projectile) != null;
    }

    public static boolean isPersistent(AbstractCannonProjectile projectile) {
        CompoundTag data = flightData(projectile);
        return data != null && data.getBoolean("PersistentProjectile");
    }

    public static boolean updateTarget(AbstractCannonProjectile projectile,
                                       double x, double y, double z) {
        CompoundTag data = flightData(projectile);
        if (data == null || projectile.isRemoved()) return false;
        data.putBoolean("HasTarget", true);
        data.putDouble("TargetX", x);
        data.putDouble("TargetY", y);
        data.putDouble("TargetZ", z);
        return true;
    }

    public static void tick(AbstractCannonProjectile projectile) {
        if (projectile.level().isClientSide) return;
        CompoundTag data = flightData(projectile);
        if (data == null || !FuzeConfig.trajectoryCorrectionAvailable()) return;
        String channel = data.getString("Channel");
        boolean hasTarget = data.getBoolean("HasTarget");
        double targetX = data.getDouble("TargetX");
        double targetY = data.getDouble("TargetY");
        double targetZ = data.getDouble("TargetZ");

        Vec3 velocity = projectile.getDeltaMovement();
        double velocitySqr = velocity.lengthSqr();
        Vec3 observedForce = updateObservedForce(data, velocity);
        boolean rangeApplied = data.getBoolean("RangeApplied");
        if (!rangeApplied && velocitySqr > 1.0e-8) {
            velocity = velocity.scale(Math.sqrt(FuzeConfig.CORRECTION_RANGE_MULTIPLIER.get()));
            velocitySqr = velocity.lengthSqr();
            projectile.setDeltaMovement(velocity);
            data.putBoolean("RangeApplied", true);
            rangeApplied = true;
        }

        double spentTurn = data.getDouble("SpentTurn");
        double spentDisplacement = Math.max(0.0, data.getDouble("SpentDisplacement"));
        boolean guidanceActive = false;
        boolean targetForward = false;
        double targetDx = 0.0;
        double targetDy = 0.0;
        double targetDz = 0.0;
        double targetDistanceSqr = 0.0;
        double targetDistance = -1.0;
        if (hasTarget) {
            targetDx = targetX - projectile.getX();
            targetDy = targetY - projectile.getY();
            targetDz = targetZ - projectile.getZ();
            targetDistanceSqr = targetDx * targetDx + targetDy * targetDy + targetDz * targetDz;
            targetDistance = Math.sqrt(targetDistanceSqr);
        }
        if (hasTarget && !projectile.isInGround() && velocitySqr > 1.0e-8) {
            if (targetDistanceSqr > 1.0) {
                Vec3 toTarget = new Vec3(targetDx, targetDy, targetDz);
                double speed = Math.sqrt(velocitySqr);
                Vec3 currentDirection = velocity.scale(1.0 / speed);
                double closingSpeed = currentDirection.dot(toTarget) / targetDistance * speed;
                targetForward = closingSpeed > Math.max(speed * 0.02, EPSILON);
                boolean afterApex = velocity.y <= 0.0;

                // Steering starts exactly at the apex. Travelling one kilometre
                // after that point grants the configured turn in any 3D direction.
                if (targetForward && afterApex) {
                    double degreesPerKilometre =
                        FuzeConfig.CORRECTION_TURN_DEGREES_PER_KILOMETRE.get();
                    double perTick = Math.toRadians(degreesPerKilometre * speed / 1_000.0);
                    double remainingTurn = Math.toRadians(
                        degreesPerKilometre * targetDistance / 1_000.0);
                    Vec3 desiredVector = forceCompensatedAim(
                        toTarget, currentDirection, observedForce, closingSpeed, targetDistance);
                    Vec3 desiredDirection = desiredVector.normalize();
                    double angle = angleBetweenUnit(currentDirection, desiredDirection);
                    double requestedStep = Math.min(Math.min(angle, perTick), remainingTurn);
                    double step = gravityLimitedTurnStep(
                        requestedStep, currentDirection, desiredDirection, observedForce, speed);
                    if (step > 1.0e-8) {
                        Vec3 direction = rotateTowards(currentDirection, desiredDirection, angle, step);
                        // A fin may redirect momentum, but it may not create energy. Turning produces
                        // induced drag, especially while generating upward lift against gravity.
                        double retainedSpeed = speed * turnSpeedRetention(
                            step, currentDirection, direction);
                        velocity = direction.scale(retainedSpeed);
                        projectile.setDeltaMovement(velocity);
                        projectile.setOrientation(direction);
                        spentTurn += step;
                        spentDisplacement += Math.tan(step) * targetDistance;
                        data.putDouble("SpentTurn", spentTurn);
                        data.putDouble("SpentDisplacement", spentDisplacement);
                        guidanceActive = true;
                    }
                }
            }
        }
        double remainingTurn = hasTarget ? Math.toRadians(
            FuzeConfig.CORRECTION_TURN_DEGREES_PER_KILOMETRE.get()
                * Math.max(0.0, targetDistance) / 1_000.0) : 0.0;
        double remainingDisplacement = hasTarget
            ? Math.max(0.0, targetDistance)
                * Math.sin(Math.min(Math.PI * 0.5, remainingTurn))
            : 0.0;
        rememberCommandedVelocity(data, velocity);
        CorrectionTelemetryRegistry.update(projectile, channel, hasTarget,
            targetX, targetY, targetZ, targetDistance, spentTurn, spentTurn + remainingTurn,
            spentDisplacement, spentDisplacement + remainingDisplacement, guidanceActive, targetForward,
            rangeApplied);
    }

    private static Vec3 updateObservedForce(CompoundTag data, Vec3 currentVelocity) {
        Vec3 filtered = new Vec3(
            data.getDouble("FilteredForceX"),
            data.contains("FilteredForceY") ? data.getDouble("FilteredForceY") : -0.05,
            data.getDouble("FilteredForceZ")
        );
        if (data.getBoolean("HasPreviousCommand")) {
            Vec3 previousCommand = new Vec3(
                data.getDouble("PreviousCommandX"),
                data.getDouble("PreviousCommandY"),
                data.getDouble("PreviousCommandZ")
            );
            Vec3 sample = currentVelocity.subtract(previousCommand);
            if (isFinite(sample) && sample.lengthSqr() <= MAX_FORCE_SAMPLE * MAX_FORCE_SAMPLE) {
                filtered = filtered.scale(1.0 - FORCE_FILTER).add(sample.scale(FORCE_FILTER));
            }
        }
        data.putDouble("FilteredForceX", filtered.x);
        data.putDouble("FilteredForceY", filtered.y);
        data.putDouble("FilteredForceZ", filtered.z);
        return filtered;
    }

    private static CompoundTag flightData(AbstractCannonProjectile projectile) {
        if (projectile instanceof CorrectionRuntimeState runtime) {
            byte state = runtime.cbcProgrammableFuzes$getCorrectionState();
            if (state == STATE_ABSENT) return null;
            if (state == STATE_ATTACHED) return runtime.cbcProgrammableFuzes$getCorrectionData();

            CompoundTag persistent = projectile.getPersistentData();
            if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
                runtime.cbcProgrammableFuzes$setCorrectionState(STATE_ABSENT, null);
                return null;
            }
            CompoundTag data = persistent.getCompound(ROOT);
            runtime.cbcProgrammableFuzes$setCorrectionState(STATE_ATTACHED, data);
            return data;
        }

        CompoundTag persistent = projectile.getPersistentData();
        return persistent.contains(ROOT, Tag.TAG_COMPOUND) ? persistent.getCompound(ROOT) : null;
    }

    private static Vec3 forceCompensatedAim(Vec3 toTarget, Vec3 flightDirection,
                                             Vec3 observedForce, double closingSpeed,
                                             double distance) {
        double lookahead = distance / Math.max(closingSpeed, EPSILON);
        // Parallel drag changes arrival time but not direction. Only compensate
        // acceleration which bends the path (gravity, wind and other ballistics
        // mods), learned from the projectile's actual previous tick.
        Vec3 bendingForce = observedForce.subtract(
            flightDirection.scale(observedForce.dot(flightDirection)));
        Vec3 compensation = bendingForce.scale(-0.5 * lookahead * lookahead);
        double maximumCompensation = distance * MAX_FORCE_COMPENSATION_FRACTION;
        if (compensation.lengthSqr() > maximumCompensation * maximumCompensation) {
            compensation = compensation.normalize().scale(maximumCompensation);
        }
        Vec3 result = toTarget.add(compensation);
        return result.lengthSqr() > EPSILON ? result : toTarget;
    }

    /**
     * The flight-path angle naturally falls by roughly g_perpendicular / speed every tick.
     * A pitch-up command must overcome that first. This uses the force actually observed on
     * the projectile, so it remains compatible with CBC Realistic Ballistics and other gravity
     * models rather than assuming Minecraft's hard-coded 0.05 blocks/tick².
     */
    private static double gravityLimitedTurnStep(double requestedStep, Vec3 currentDirection,
                                                 Vec3 desiredDirection, Vec3 observedForce,
                                                 double speed) {
        if (requestedStep <= 0.0 || speed <= EPSILON
            || desiredDirection.y <= currentDirection.y) return Math.max(0.0, requestedStep);

        double downwardAcceleration = Math.max(0.0, -observedForce.y);
        double horizontalFraction = Math.sqrt(Math.max(0.0,
            1.0 - currentDirection.y * currentDirection.y));
        double gravityPitchLoss = downwardAcceleration * horizontalFraction / speed;
        return Math.max(0.0, requestedStep - gravityPitchLoss);
    }

    private static double turnSpeedRetention(double step, Vec3 before, Vec3 after) {
        double upwardChange = Math.max(0.0, after.y - before.y);
        double upwardLoad = Math.min(1.0, upwardChange / Math.max(step, EPSILON));
        double dragFraction = step * TURN_DRAG_PER_RADIAN
            * (1.0 + UPWARD_TURN_DRAG_MULTIPLIER * upwardLoad);
        return Math.max(0.0, 1.0 - dragFraction);
    }

    private static void rememberCommandedVelocity(CompoundTag data, Vec3 velocity) {
        data.putDouble("PreviousCommandX", velocity.x);
        data.putDouble("PreviousCommandY", velocity.y);
        data.putDouble("PreviousCommandZ", velocity.z);
        data.putBoolean("HasPreviousCommand", true);
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static double angleBetweenUnit(Vec3 a, Vec3 b) {
        return Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b))));
    }

    private static Vec3 rotateTowards(Vec3 current, Vec3 desired, double angle, double step) {
        if (angle < 1.0e-8 || step >= angle) return desired;
        double fraction = step / angle;
        return current.scale(1.0 - fraction).add(desired.scale(fraction)).normalize();
    }
}
