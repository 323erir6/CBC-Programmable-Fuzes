package ua.ivan.cbcprogrammablefuzes.peripheral;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import dan200.computercraft.api.peripheral.PeripheralType;
import net.minecraft.world.item.ItemStack;
import ua.ivan.cbcprogrammablefuzes.FuzeConfig;
import ua.ivan.cbcprogrammablefuzes.FuzeSettings;
import ua.ivan.cbcprogrammablefuzes.CorrectionSettings;
import ua.ivan.cbcprogrammablefuzes.CorrectionTelemetryRegistry;
import ua.ivan.cbcprogrammablefuzes.InFlightFuzeRegistry;
import java.util.List;
import java.util.Map;

public final class FuzePeripheralMethods implements GenericPeripheral {
    @Override
    public String id() {
        return "cbc_programmable_fuzes:programmable_fuze";
    }

    @Override
    public PeripheralType getType() {
        return PeripheralType.ofAdditional("programmable_fuze", "cbc_programmable_fuze");
    }

    @LuaFunction(mainThread = true)
    public final boolean hasProgrammableFuze(ProgrammableFuzeAccess target) {
        return target.programmableFuze$hasFuze();
    }

    @LuaFunction(mainThread = true)
    public final boolean setProximity(ProgrammableFuzeAccess target, double radius, boolean active)
        throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.proximityAvailable()) {
            throw new LuaException("Proximity mode is disabled by the server configuration");
        }
        radius = radiusValue(radius);
        ItemStack stack = readFuzeStack(target);
        FuzeSettings.write(stack, FuzeSettings.read(stack).withProximity(active, radius));
        writeFuzeStack(target, stack);
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean setTimer(ProgrammableFuzeAccess target, double seconds, boolean active)
        throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.timerAvailable()) {
            throw new LuaException("Timer mode is disabled by the server configuration");
        }
        seconds = timerValue(seconds);
        ItemStack stack = readFuzeStack(target);
        FuzeSettings.write(stack, FuzeSettings.read(stack).withTimer(active, seconds));
        writeFuzeStack(target, stack);
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean setPersistent(ProgrammableFuzeAccess target, boolean active) throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.persistenceAvailable()) {
            throw new LuaException("Projectile persistence is disabled by the server configuration");
        }
        ItemStack stack = readFuzeStack(target);
        FuzeSettings.write(stack, FuzeSettings.read(stack).withPersistentProjectile(active));
        writeFuzeStack(target, stack);
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean setFuzeChannel(ProgrammableFuzeAccess target, String channel) throws LuaException {
        requireComputerProgramming();
        ItemStack stack = readFuzeStack(target);
        FuzeSettings.write(stack, FuzeSettings.read(stack).withChannel(channel));
        writeFuzeStack(target, stack);
        return true;
    }

    /** Lists every flying programmable fuze currently visible on this channel. */
    @LuaFunction
    public final List<Map<String, Object>> listFuzeProjectiles(ProgrammableFuzeAccess target, String channel) {
        return InFlightFuzeRegistry.list(channel);
    }

    /** Returns live position, velocity, arming state, timer and all programmed settings. */
    @LuaFunction
    public final MethodResult getFuzeTelemetry(ProgrammableFuzeAccess target, String channel, String id) {
        Map<String, Object> telemetry = InFlightFuzeRegistry.get(channel, id);
        return telemetry == null ? MethodResult.of(false) : MethodResult.of(true, telemetry);
    }

    /**
     * These in-flight methods intentionally do not use mainThread=true. They
     * only update a thread-safe latest-state mailbox, so Sable-cc-tick-sync can
     * deliver calls at its full construction frequency without filling CC's
     * Minecraft-thread task queue.
     */
    @LuaFunction
    public final boolean setFuzeProjectileTimer(ProgrammableFuzeAccess target, String channel, String id,
                                                 double seconds, boolean active) throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.timerAvailable()) {
            throw new LuaException("Timer mode is disabled by the server configuration");
        }
        final double value = timerValue(seconds);
        return InFlightFuzeRegistry.update(channel, id,
            settings -> settings.withTimer(active, value), true, false);
    }

    @LuaFunction
    public final boolean setFuzeProjectileProximity(ProgrammableFuzeAccess target, String channel, String id,
                                                     double radius, boolean active) throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.proximityAvailable()) {
            throw new LuaException("Proximity mode is disabled by the server configuration");
        }
        final double value = radiusValue(radius);
        return InFlightFuzeRegistry.update(channel, id,
            settings -> settings.withProximity(active, value), false, false);
    }

    @LuaFunction
    public final boolean setFuzeProjectilePersistent(ProgrammableFuzeAccess target, String channel, String id,
                                                      boolean active) throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.persistenceAvailable()) {
            throw new LuaException("Projectile persistence is disabled by the server configuration");
        }
        return InFlightFuzeRegistry.update(channel, id,
            settings -> settings.withPersistentProjectile(active), false, false);
    }

    @LuaFunction
    public final boolean setFuzeProjectileChannel(ProgrammableFuzeAccess target, String channel, String id,
                                                   String newChannel) throws LuaException {
        requireComputerProgramming();
        return InFlightFuzeRegistry.update(channel, id,
            settings -> settings.withChannel(newChannel), false, false);
    }

    /** Atomically replaces every configurable in-flight fuze parameter. */
    @LuaFunction
    public final boolean setFuzeProjectileSettings(ProgrammableFuzeAccess target, String channel, String id,
                                                    double timerSeconds, boolean timerActive,
                                                    double proximityRadius, boolean proximityActive,
                                                    boolean persistent) throws LuaException {
        requireComputerProgramming();
        if (timerActive && !FuzeConfig.timerAvailable()) {
            throw new LuaException("Timer mode is disabled by the server configuration");
        }
        if (proximityActive && !FuzeConfig.proximityAvailable()) {
            throw new LuaException("Proximity mode is disabled by the server configuration");
        }
        if (persistent && !FuzeConfig.persistenceAvailable()) {
            throw new LuaException("Projectile persistence is disabled by the server configuration");
        }
        final double timer = timerValue(timerSeconds);
        final double radius = radiusValue(proximityRadius);
        return InFlightFuzeRegistry.update(channel, id,
            settings -> new FuzeSettings(proximityActive, radius, timerActive, timer,
                persistent, settings.channel()), true, false);
    }

    @LuaFunction(mainThread = true)
    public final boolean hasTrajectoryCorrectionModule(ProgrammableFuzeAccess target) {
        return target.programmableFuze$hasCorrectionModule();
    }

    @LuaFunction(mainThread = true)
    public final boolean setCorrectionChannel(ProgrammableFuzeAccess target, String channel) throws LuaException {
        requireComputerProgramming();
        ItemStack stack = readCorrectionStack(target);
        CorrectionSettings.write(stack, CorrectionSettings.read(stack).withChannel(channel));
        writeCorrectionStack(target, stack);
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean setCorrectionPersistent(ProgrammableFuzeAccess target, boolean active)
        throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.persistenceAvailable()) {
            throw new LuaException("Projectile persistence is disabled by the server configuration");
        }
        ItemStack stack = readCorrectionStack(target);
        CorrectionSettings.write(stack,
            CorrectionSettings.read(stack).withPersistentProjectile(active));
        writeCorrectionStack(target, stack);
        return true;
    }

    @LuaFunction(mainThread = true)
    public final boolean setImpactCoordinates(ProgrammableFuzeAccess target, double x, double y, double z)
        throws LuaException {
        requireComputerProgramming();
        finiteNumber(x, "x"); finiteNumber(y, "y"); finiteNumber(z, "z");
        ItemStack stack = readCorrectionStack(target);
        CorrectionSettings.write(stack, CorrectionSettings.read(stack).withTarget(x, y, z));
        writeCorrectionStack(target, stack);
        return true;
    }

    @LuaFunction
    public final List<Map<String, Object>> listCorrectionProjectiles(ProgrammableFuzeAccess target,
                                                                     String channel) {
        return CorrectionTelemetryRegistry.list(channel);
    }

    @LuaFunction
    public final MethodResult getCorrectionTelemetry(ProgrammableFuzeAccess target, String channel, String id) {
        Map<String, Object> telemetry = CorrectionTelemetryRegistry.get(channel, id);
        return telemetry == null ? MethodResult.of(false) : MethodResult.of(true, telemetry);
    }

    @LuaFunction(mainThread = true)
    public final boolean setCorrectionProjectileTarget(ProgrammableFuzeAccess target,
                                                        String channel, String id,
                                                        double x, double y, double z)
        throws LuaException {
        requireComputerProgramming();
        if (!FuzeConfig.trajectoryCorrectionAvailable()) {
            throw new LuaException("Trajectory correction is disabled by the server configuration");
        }
        finiteNumber(x, "x"); finiteNumber(y, "y"); finiteNumber(z, "z");
        return CorrectionTelemetryRegistry.updateTarget(channel, id, x, y, z);
    }

    /** Returns true and a table describing the next round in firing order. */
    @LuaFunction(mainThread = true)
    public final MethodResult getLoadedProjectileInfo(ProgrammableFuzeAccess target) {
        return inspectionResult(target.programmableFuze$getLoadedProjectileInfo());
    }

    /** Returns true and a table containing every loaded propellant segment and the totals used by CBC. */
    @LuaFunction(mainThread = true)
    public final MethodResult getLoadedPropellantInfo(ProgrammableFuzeAccess target) {
        return inspectionResult(target.programmableFuze$getLoadedPropellantInfo());
    }

    /** Returns true and a table describing the assembled cannon and its material properties. */
    @LuaFunction(mainThread = true)
    public final MethodResult getCannonInfo(ProgrammableFuzeAccess target) {
        return inspectionResult(target.programmableFuze$getCannonInfo());
    }

    private static MethodResult inspectionResult(Map<String, Object> value) {
        return value == null || value.isEmpty() ? MethodResult.of(false) : MethodResult.of(true, value);
    }

    private static ItemStack readFuzeStack(ProgrammableFuzeAccess target) throws LuaException {
        ItemStack stack = target.programmableFuze$getFuze();
        if (!FuzeAccessUtil.isProgrammableFuze(stack)) {
            throw new LuaException("No loaded projectile with a programmable fuze");
        }
        return stack;
    }

    private static void writeFuzeStack(ProgrammableFuzeAccess target, ItemStack stack) throws LuaException {
        if (!target.programmableFuze$setFuze(stack)) {
            throw new LuaException("The programmable fuze is no longer available");
        }
    }

    private static ItemStack readCorrectionStack(ProgrammableFuzeAccess target) throws LuaException {
        ItemStack stack = target.programmableFuze$getCorrectionModule();
        if (!stack.is(ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) {
            throw new LuaException("No loaded projectile with a trajectory-correction module");
        }
        return stack;
    }

    private static void writeCorrectionStack(ProgrammableFuzeAccess target, ItemStack stack)
        throws LuaException {
        if (!FuzeConfig.trajectoryCorrectionAvailable()) {
            throw new LuaException("Trajectory correction is disabled by the server configuration");
        }
        if (!target.programmableFuze$setCorrectionModule(stack)) {
            throw new LuaException("The trajectory-correction module is no longer available");
        }
    }

    private static double radiusValue(double radius) throws LuaException {
        finiteNumber(radius, "radius");
        double minimum = FuzeConfig.minimumProximityRadius();
        double maximum = FuzeConfig.maximumProximityRadius();
        if (radius < minimum || radius > maximum) {
            throw new LuaException("radius must be between "
                + minimum + " and " + maximum);
        }
        return radius;
    }

    private static double timerValue(double seconds) throws LuaException {
        finiteNumber(seconds, "seconds");
        double minimum = FuzeConfig.minimumTimerSeconds();
        double maximum = FuzeConfig.maximumTimerSeconds();
        if (seconds < minimum || seconds > maximum) {
            throw new LuaException("seconds must be between " + minimum + " and " + maximum);
        }
        // The projectile ticks at 20 Hz, so expose exactly the value that can be represented in game.
        return Math.ceil(seconds * 20.0) / 20.0;
    }

    private static void finiteNumber(double value, String name) throws LuaException {
        if (!Double.isFinite(value)) throw new LuaException(name + " must be finite");
    }

    private static void requireComputerProgramming() throws LuaException {
        if (!FuzeConfig.computerProgrammingAvailable()) {
            throw new LuaException("Computer programming is disabled by the server configuration");
        }
    }
}
