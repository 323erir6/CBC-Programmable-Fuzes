package ua.ivan.cbcprogrammablefuzes;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

public final class CorrectionTelemetryRegistry {
    // Entity removal deletes entries immediately. This longer fallback only
    // protects against a lost removal callback and must not make a live shell
    // disappear from CC during a brief chunk-transition scheduling gap.
    private static final long STALE_AFTER_NANOS = 30_000_000_000L;
    private static final ConcurrentHashMap<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentSkipListMap<String, Entry>> CHANNELS =
        new ConcurrentHashMap<>();
    private static final AtomicLong LAST_PRUNE = new AtomicLong(System.nanoTime());

    private CorrectionTelemetryRegistry() {}

    public static void update(AbstractCannonProjectile projectile, String channel, boolean hasTarget,
                              double targetX, double targetY, double targetZ, double targetDistance,
                              double spentTurnRadians, double maxTurnRadians,
                              double spentDisplacement, double maximumDisplacement,
                              boolean guidanceActive, boolean targetForward,
                              boolean rangeMultiplierApplied) {
        if (!(projectile.level() instanceof ServerLevel level)) return;
        Vec3 velocity = projectile.getDeltaMovement();
        UUID id = projectile.getUUID();
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            Entry created = new Entry(id, channel, projectile.getType().toString(), projectile);
            created.update(
                level.dimension().location(),
                projectile.getX(), projectile.getY(), projectile.getZ(),
                velocity.x, velocity.y, velocity.z, Math.sqrt(velocity.lengthSqr()) * 20.0,
                projectile.tickCount, hasTarget, targetX, targetY, targetZ, targetDistance,
                Math.max(0.0, Math.toDegrees(maxTurnRadians - spentTurnRadians)),
                Math.max(0.0, maximumDisplacement - spentDisplacement),
                guidanceActive, targetForward, rangeMultiplierApplied, System.nanoTime()
            );
            Entry existing = ENTRIES.putIfAbsent(id, created);
            if (existing == null) {
                CHANNELS.compute(channel, (key, entries) -> {
                    if (entries == null) entries = new ConcurrentSkipListMap<>();
                    entries.put(created.idText, created);
                    return entries;
                });
                return;
            }
            entry = existing;
        }
        entry.update(
            level.dimension().location(),
            projectile.getX(), projectile.getY(), projectile.getZ(),
            velocity.x, velocity.y, velocity.z, Math.sqrt(velocity.lengthSqr()) * 20.0,
            projectile.tickCount, hasTarget, targetX, targetY, targetZ, targetDistance,
            Math.max(0.0, Math.toDegrees(maxTurnRadians - spentTurnRadians)),
            Math.max(0.0, maximumDisplacement - spentDisplacement),
            guidanceActive, targetForward, rangeMultiplierApplied, System.nanoTime()
        );
    }

    public static List<Map<String, Object>> list(String channel) {
        String wanted = CorrectionSettings.sanitizeChannel(channel);
        prune();
        var matches = CHANNELS.get(wanted);
        if (matches == null) return new ArrayList<>();
        long now = System.nanoTime();
        List<Map<String, Object>> result = new ArrayList<>();
        // Already ordered by UUID. Only this channel is visited; row data is
        // still copied fresh on every CC request, without a shared mutable map.
        for (Entry entry : matches.values()) {
            if (!entry.isStale(now) && ENTRIES.get(entry.id) == entry) result.add(entry.toListRow());
        }
        return result;
    }

    @Nullable
    public static Map<String, Object> get(String channel, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || !entry.channel.equals(CorrectionSettings.sanitizeChannel(channel))) return null;
        if (removeIfStale(entry, System.nanoTime())) return null;
        return entry.toMap();
    }

    public static boolean updateTarget(String channel, String id,
                                       double x, double y, double z) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        Entry entry = ENTRIES.get(uuid);
        if (entry == null
            || !entry.channel.equals(CorrectionSettings.sanitizeChannel(channel))) return false;
        if (removeIfStale(entry, System.nanoTime())) return false;
        AbstractCannonProjectile projectile = entry.projectile.get();
        return projectile != null && CorrectionFlightController.updateTarget(projectile, x, y, z);
    }

    public static void remove(Entity entity) {
        if (entity instanceof AbstractCannonProjectile) {
            Entry entry = ENTRIES.get(entity.getUUID());
            if (entry != null) remove(entry);
        }
    }

    private static void remove(Entry entry) {
        if (!ENTRIES.remove(entry.id, entry)) return;
        CHANNELS.computeIfPresent(entry.channel, (key, entries) -> {
            entries.remove(entry.idText, entry);
            return entries.isEmpty() ? null : entries;
        });
    }

    private static boolean removeIfStale(Entry entry, long now) {
        // A CC thread must not prune an entry between the server's fresh
        // telemetry write and removal. Coordinate with Entry.update's lock.
        synchronized (entry) {
            if (!entry.isStale(now)) return false;
            remove(entry);
            return true;
        }
    }

    public static void clear() {
        ENTRIES.clear();
        CHANNELS.clear();
        LAST_PRUNE.set(System.nanoTime());
    }

    private static void prune() {
        long now = System.nanoTime();
        long previous = LAST_PRUNE.get();
        if (now - previous < 1_000_000_000L || !LAST_PRUNE.compareAndSet(previous, now)) return;
        for (Entry entry : ENTRIES.values()) {
            removeIfStale(entry, now);
        }
    }

    private static final class Entry {
        private final UUID id;
        private final String idText;
        private final String channel;
        private final String type;
        private final WeakReference<AbstractCannonProjectile> projectile;
        private ResourceLocation dimension;
        private double x;
        private double y;
        private double z;
        private double vx;
        private double vy;
        private double vz;
        private double speed;
        private int ageTicks;
        private boolean hasTarget;
        private double targetX;
        private double targetY;
        private double targetZ;
        private double targetDistance;
        private double correctionDegreesRemaining;
        private double correctionDistanceRemaining;
        private boolean guidanceActive;
        private boolean targetForward;
        private boolean rangeMultiplierApplied;
        private long lastSeenNanos;

        private Entry(UUID id, String channel, String type,
                      AbstractCannonProjectile projectile) {
            this.id = id;
            this.idText = id.toString();
            this.channel = channel;
            this.type = type;
            this.projectile = new WeakReference<>(projectile);
        }

        private synchronized void update(ResourceLocation dimension,
                                         double x, double y, double z,
                                         double vx, double vy, double vz, double speed,
                                         int ageTicks, boolean hasTarget,
                                         double targetX, double targetY, double targetZ, double targetDistance,
                                         double correctionDegreesRemaining, double correctionDistanceRemaining,
                                         boolean guidanceActive, boolean targetForward,
                                         boolean rangeMultiplierApplied, long lastSeenNanos) {
            this.dimension = dimension;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz; this.speed = speed;
            this.ageTicks = ageTicks;
            this.hasTarget = hasTarget;
            this.targetX = targetX; this.targetY = targetY; this.targetZ = targetZ;
            this.targetDistance = targetDistance;
            this.correctionDegreesRemaining = correctionDegreesRemaining;
            this.correctionDistanceRemaining = correctionDistanceRemaining;
            this.guidanceActive = guidanceActive;
            this.targetForward = targetForward;
            this.rangeMultiplierApplied = rangeMultiplierApplied;
            this.lastSeenNanos = lastSeenNanos;
        }

        private synchronized boolean isStale(long now) {
            return now - lastSeenNanos > STALE_AFTER_NANOS;
        }

        private synchronized Map<String, Object> toListRow() {
            Map<String, Object> row = new LinkedHashMap<>(3);
            row.put("id", idText);
            row.put("type", type);
            row.put("ageTicks", ageTicks);
            return row;
        }

        private synchronized Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", idText);
            result.put("channel", channel);
            result.put("dimension", dimension.toString());
            result.put("type", type);
            result.put("x", x); result.put("y", y); result.put("z", z);
            result.put("velocityX", vx); result.put("velocityY", vy); result.put("velocityZ", vz);
            result.put("speed", speed);
            result.put("ageTicks", ageTicks);
            result.put("hasTarget", hasTarget);
            if (hasTarget) {
                result.put("targetX", targetX); result.put("targetY", targetY); result.put("targetZ", targetZ);
                result.put("targetDistance", targetDistance);
            }
            result.put("correctionDegreesRemaining", correctionDegreesRemaining);
            result.put("correctionDistanceRemaining", correctionDistanceRemaining);
            result.put("guidanceActive", guidanceActive);
            result.put("targetForward", targetForward);
            result.put("rangeMultiplierApplied", rangeMultiplierApplied);
            return result;
        }
    }
}
