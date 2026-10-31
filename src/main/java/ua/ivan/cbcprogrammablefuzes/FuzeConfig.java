package ua.ivan.cbcprogrammablefuzes;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-synchronised settings exposed through Create's config screen. */
public final class FuzeConfig {
    private static final double ABSOLUTE_MAX_RADIUS = 1_024.0;
    private static final double ABSOLUTE_MAX_TIMER_SECONDS = 31_536_000.0;

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_PROXIMITY;
    public static final ModConfigSpec.BooleanValue ENABLE_TIMER;
    public static final ModConfigSpec.BooleanValue ENABLE_PERSISTENCE;
    public static final ModConfigSpec.BooleanValue ENABLE_COMPUTER_PROGRAMMING;
    public static final ModConfigSpec.BooleanValue ENABLE_TRAJECTORY_CORRECTION;
    public static final ModConfigSpec.DoubleValue CORRECTION_RANGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CORRECTION_TURN_DEGREES_PER_KILOMETRE;

    public static final ModConfigSpec.BooleanValue DEFAULT_PROXIMITY_ACTIVE;
    public static final ModConfigSpec.DoubleValue DEFAULT_PROXIMITY_RADIUS;
    public static final ModConfigSpec.BooleanValue DEFAULT_TIMER_ACTIVE;
    public static final ModConfigSpec.DoubleValue DEFAULT_TIMER_SECONDS;
    public static final ModConfigSpec.BooleanValue DEFAULT_PERSISTENT_PROJECTILE;

    public static final ModConfigSpec.DoubleValue MIN_PROXIMITY_RADIUS;
    public static final ModConfigSpec.DoubleValue MAX_PROXIMITY_RADIUS;
    public static final ModConfigSpec.DoubleValue MIN_TIMER_SECONDS;
    public static final ModConfigSpec.DoubleValue MAX_TIMER_SECONDS;
    public static final ModConfigSpec.IntValue MAX_PROXIMITY_RAYS_PER_CHECK;
    public static final ModConfigSpec.IntValue MAX_PROXIMITY_RAYS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_PHYSICAL_NARROW_PHASE_CHECKS_PER_TICK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Global switches for programmable-fuze features.").push("features");
        ENABLE_PROXIMITY = builder
            .comment("Allow proximity detonation. Existing programmed fuzes are ignored while disabled.")
            .define("enableProximity", true);
        ENABLE_TIMER = builder
            .comment("Allow timer detonation. Existing programmed fuzes are ignored while disabled.")
            .define("enableTimer", true);
        ENABLE_PERSISTENCE = builder
            .comment("Allow programmable fuzes and correction modules to prevent projectile lifetime despawn.")
            .define("enableProjectilePersistence", true);
        ENABLE_COMPUTER_PROGRAMMING = builder
            .comment("Allow CC:Tweaked computers to change programmable-fuze settings.",
                "hasProgrammableFuze() remains available as a read-only check.")
            .define("enableComputerProgramming", true);
        ENABLE_TRAJECTORY_CORRECTION = builder
            .comment("Enable active trajectory-correction tail modules.")
            .define("enableTrajectoryCorrection", true);
        builder.pop();

        builder.comment("Active tail-module flight model.").push("trajectoryCorrection");
        CORRECTION_RANGE_MULTIPLIER = builder
            .comment("Theoretical range multiplier supplied by the lifting fins.",
                "1.10 means approximately ten percent more range without changing CBC damage values.")
            .defineInRange("rangeMultiplier", 1.10, 1.0, 2.0);
        CORRECTION_TURN_DEGREES_PER_KILOMETRE = builder
            .comment("Maximum 3D direction change accumulated per kilometre flown after the apex.",
                "The default allows six degrees of correction in any direction per kilometre.")
            .defineInRange("turnDegreesPerKilometre", 6.0, 0.0, 45.0);
        builder.pop();

        builder.comment("Settings used by a new or not-yet-programmed fuze.").push("defaults");
        DEFAULT_PROXIMITY_ACTIVE = builder
            .comment("Enable proximity detonation by default.")
            .define("proximityActive", true);
        DEFAULT_PROXIMITY_RADIUS = builder
            .comment("Default proximity radius in blocks/metres.",
                "The effective value is clamped to the configured limits below.")
            .defineInRange("proximityRadius", 2.0, 0.0, ABSOLUTE_MAX_RADIUS);
        DEFAULT_TIMER_ACTIVE = builder
            .comment("Enable timer detonation by default.")
            .define("timerActive", false);
        DEFAULT_TIMER_SECONDS = builder
            .comment("Default timer delay in seconds.",
                "The effective value is clamped to the configured limits below.")
            .defineInRange("timerSeconds", 15.0, 0.0, ABSOLUTE_MAX_TIMER_SECONDS);
        DEFAULT_PERSISTENT_PROJECTILE = builder
            .comment("Prevent lifetime/despawn removal by default.")
            .define("persistentProjectile", false);
        builder.pop();

        builder.comment("Values accepted by the CC:Tweaked API.",
            "If a minimum is larger than its maximum, the two values are automatically reordered.")
            .push("limits");
        MIN_PROXIMITY_RADIUS = builder
            .comment("Smallest accepted proximity radius in blocks/metres. Zero means contact distance.")
            .defineInRange("minimumProximityRadius", 0.0, 0.0, ABSOLUTE_MAX_RADIUS);
        MAX_PROXIMITY_RADIUS = builder
            .comment("Largest accepted proximity radius in blocks/metres.")
            .defineInRange("maximumProximityRadius", 64.0, 0.0, ABSOLUTE_MAX_RADIUS);
        MIN_TIMER_SECONDS = builder
            .comment("Smallest accepted timer delay in seconds. Timer resolution is 0.05 seconds.")
            .defineInRange("minimumTimerSeconds", 0.0, 0.0, ABSOLUTE_MAX_TIMER_SECONDS);
        MAX_TIMER_SECONDS = builder
            .comment("Largest accepted timer delay in seconds.")
            .defineInRange("maximumTimerSeconds", 86_400.0, 0.0, ABSOLUTE_MAX_TIMER_SECONDS);
        builder.pop();

        builder.comment("Bounded work limits for large salvos.",
            "Cheap Sable broad-phase checks still run on every physics substep.")
            .push("performance");
        MAX_PROXIMITY_RAYS_PER_CHECK = builder
            .comment("Maximum exact block rays used by one proximity check.",
                "The rays are distributed over the full spherical search area.")
            .defineInRange("maximumProximityRaysPerCheck", 65, 1, 197);
        MAX_PROXIMITY_RAYS_PER_TICK = builder
            .comment("Shared exact block-ray budget for all programmable fuzes in one server tick.",
                "Every check retains at least its central safety ray after this budget is exhausted.")
            .defineInRange("maximumProximityRaysPerServerTick", 4_096, 64, 65_536);
        MAX_PHYSICAL_NARROW_PHASE_CHECKS_PER_TICK = builder
            .comment("Maximum exact Sable-construction proximity checks in one server tick.",
                "The spatial-index broad phase remains active on every Sable physics substep.")
            .defineInRange("maximumPhysicalNarrowPhaseChecksPerServerTick", 128, 1, 4_096);
        builder.pop();

        SPEC = builder.build();
    }

    private FuzeConfig() {
    }

    public static boolean proximityAvailable() {
        return ENABLE_PROXIMITY.get();
    }

    public static boolean timerAvailable() {
        return ENABLE_TIMER.get();
    }

    public static boolean persistenceAvailable() {
        return ENABLE_PERSISTENCE.get();
    }

    public static boolean computerProgrammingAvailable() {
        return ENABLE_COMPUTER_PROGRAMMING.get();
    }

    public static boolean trajectoryCorrectionAvailable() {
        return ENABLE_TRAJECTORY_CORRECTION.get();
    }

    public static double minimumProximityRadius() {
        return Math.min(MIN_PROXIMITY_RADIUS.get(), MAX_PROXIMITY_RADIUS.get());
    }

    public static double maximumProximityRadius() {
        return Math.max(MIN_PROXIMITY_RADIUS.get(), MAX_PROXIMITY_RADIUS.get());
    }

    public static double minimumTimerSeconds() {
        return Math.min(MIN_TIMER_SECONDS.get(), MAX_TIMER_SECONDS.get());
    }

    public static double maximumTimerSeconds() {
        return Math.max(MIN_TIMER_SECONDS.get(), MAX_TIMER_SECONDS.get());
    }

    public static double defaultProximityRadius() {
        return clamp(DEFAULT_PROXIMITY_RADIUS.get(), minimumProximityRadius(), maximumProximityRadius());
    }

    public static double defaultTimerSeconds() {
        return clamp(DEFAULT_TIMER_SECONDS.get(), minimumTimerSeconds(), maximumTimerSeconds());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
