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
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;

/**
 * Thread-safe mailbox and telemetry index for already-fired programmable fuzes.
 * CC calls only alter immutable pending state; the projectile tick applies the
 * latest state to the real ItemStack. This allows a 100 Hz Sable computer to
 * update a fuze without queueing 100 unsafe Minecraft-thread mutations.
 */
public final class InFlightFuzeRegistry {
    private static final long STALE_AFTER_NANOS = 30_000_000_000L;
    private static final ConcurrentHashMap<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentSkipListMap<String, Entry>> CHANNELS =
        new ConcurrentHashMap<>();
    private static final AtomicLong LAST_PRUNE = new AtomicLong(System.nanoTime());

    private InFlightFuzeRegistry() {}

    /** Called on the projectile/server thread before the fuze evaluates. */
    public static FuzeSettings applyAndUpdate(AbstractCannonProjectile projectile, ItemStack stack) {
        FuzeSettings settings = FuzeSettings.read(stack);
        if (!(projectile.level() instanceof ServerLevel level)) return settings;

        UUID id = projectile.getUUID();
        Entry entry = ENTRIES.get(id);
        if (entry == null) {
            Entry created = new Entry(id, settings.channel(), projectile.getType().toString(), projectile, settings);
            Entry existing = ENTRIES.putIfAbsent(id, created);
            entry = existing == null ? created : existing;
            if (existing == null) addToChannel(created, created.channel);
        }

        settings = entry.applyPending(stack, settings);
        String oldChannel = entry.updateTelemetry(level.dimension().location(), projectile, settings,
            stack.getOrDefault(CBCDataComponents.FUZE_TIMER, settings.timerTicks()),
            stack.has(CBCDataComponents.ARMED), System.nanoTime());
        if (oldChannel != null) moveChannel(entry, oldChannel, settings.channel());
        return settings;
    }

    /** Applies only the newest queued command from a Sable physics substep. */
    public static FuzeSettings applyPendingForPhysicalStep(AbstractCannonProjectile projectile, ItemStack stack) {
        FuzeSettings settings = FuzeSettings.read(stack);
        Entry entry = ENTRIES.get(projectile.getUUID());
        return entry == null ? settings : entry.applyPending(stack, settings);
    }

    public static List<Map<String, Object>> list(String channel) {
        String wanted = CorrectionSettings.sanitizeChannel(channel);
        prune();
        var matches = CHANNELS.get(wanted);
        if (matches == null) return new ArrayList<>();
        long now = System.nanoTime();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Entry entry : matches.values()) {
            if (!entry.isStale(now) && ENTRIES.get(entry.id) == entry) result.add(entry.toListRow());
        }
        return result;
    }

    @Nullable
    public static Map<String, Object> get(String channel, String id) {
        Entry entry = find(channel, id);
        return entry == null ? null : entry.toMap();
    }

    public static boolean update(String channel, String id, UnaryOperator<FuzeSettings> operation,
                                 boolean resetTimer, boolean resetProximityArming) {
        Entry entry = find(channel, id);
        return entry != null && entry.queue(operation, resetTimer, resetProximityArming);
    }

    public static void remove(Entity entity) {
        if (!(entity instanceof AbstractCannonProjectile)) return;
        Entry entry = ENTRIES.get(entity.getUUID());
        if (entry != null) remove(entry);
    }

    public static void clear() {
        ENTRIES.clear();
        CHANNELS.clear();
        LAST_PRUNE.set(System.nanoTime());
    }

    @Nullable
    private static Entry find(String channel, String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
        Entry entry = ENTRIES.get(uuid);
        if (entry == null || !entry.channelMatches(CorrectionSettings.sanitizeChannel(channel))) return null;
        if (removeIfStale(entry, System.nanoTime())) return null;
        return entry;
    }

    private static void addToChannel(Entry entry, String channel) {
        CHANNELS.compute(channel, (key, entries) -> {
            if (entries == null) entries = new ConcurrentSkipListMap<>();
            entries.put(entry.idText, entry);
            return entries;
        });
    }

    private static void moveChannel(Entry entry, String oldChannel, String newChannel) {
        CHANNELS.computeIfPresent(oldChannel, (key, entries) -> {
            entries.remove(entry.idText, entry);
            return entries.isEmpty() ? null : entries;
        });
        addToChannel(entry, newChannel);
    }

    private static void remove(Entry entry) {
        if (!ENTRIES.remove(entry.id, entry)) return;
        String channel = entry.channelSnapshot();
        CHANNELS.computeIfPresent(channel, (key, entries) -> {
            entries.remove(entry.idText, entry);
            return entries.isEmpty() ? null : entries;
        });
    }

    private static boolean removeIfStale(Entry entry, long now) {
        synchronized (entry) {
            if (!entry.isStale(now)) return false;
            remove(entry);
            return true;
        }
    }

    private static void prune() {
        long now = System.nanoTime();
        long previous = LAST_PRUNE.get();
        if (now - previous < 1_000_000_000L || !LAST_PRUNE.compareAndSet(previous, now)) return;
        for (Entry entry : ENTRIES.values()) removeIfStale(entry, now);
    }

    private static final class Entry {
        private final UUID id;
        private final String idText;
        private final String type;
        private final WeakReference<AbstractCannonProjectile> projectile;
        private String channel;
        private FuzeSettings current;
        @Nullable private FuzeSettings pending;
        private boolean pendingTimerReset;
        private boolean pendingProximityReset;
        private ResourceLocation dimension;
        private double x, y, z, vx, vy, vz, speed;
        private int ageTicks;
        private int timerTicksRemaining;
        private boolean armed;
        private long lastSeenNanos;

        private Entry(UUID id, String channel, String type,
                      AbstractCannonProjectile projectile, FuzeSettings settings) {
            this.id = id;
            this.idText = id.toString();
            this.channel = channel;
            this.type = type;
            this.projectile = new WeakReference<>(projectile);
            this.current = settings;
            this.lastSeenNanos = System.nanoTime();
        }

        private synchronized boolean queue(UnaryOperator<FuzeSettings> operation,
                                           boolean resetTimer, boolean resetProximityArming) {
            AbstractCannonProjectile live = projectile.get();
            if (live == null || live.isRemoved()) return false;
            FuzeSettings base = pending == null ? current : pending;
            FuzeSettings next = operation.apply(base);
            if (next == null) return false;
            pending = next;
            // Re-sending identical state is common on fast computers. It must
            // not keep an already flying timer permanently at its start value.
            pendingTimerReset |= resetTimer && FuzeSettings.timerDefinitionChanged(base, next);
            pendingProximityReset |= resetProximityArming;
            return true;
        }

        private synchronized FuzeSettings applyPending(ItemStack stack, FuzeSettings actual) {
            if (pending == null) {
                current = actual;
                return actual;
            }
            FuzeSettings next = pending;
            FuzeSettings.writeInFlight(stack, actual, next, pendingTimerReset, pendingProximityReset);
            pending = null;
            pendingTimerReset = false;
            pendingProximityReset = false;
            current = next;
            return next;
        }

        @Nullable
        private synchronized String updateTelemetry(ResourceLocation dimension,
                                                    AbstractCannonProjectile projectile,
                                                    FuzeSettings settings, int timerTicksRemaining,
                                                    boolean armed, long now) {
            String oldChannel = channel.equals(settings.channel()) ? null : channel;
            channel = settings.channel();
            current = settings;
            Vec3 velocity = projectile.getDeltaMovement();
            this.dimension = dimension;
            this.x = projectile.getX(); this.y = projectile.getY(); this.z = projectile.getZ();
            this.vx = velocity.x; this.vy = velocity.y; this.vz = velocity.z;
            this.speed = Math.sqrt(velocity.lengthSqr()) * 20.0;
            this.ageTicks = projectile.tickCount;
            this.timerTicksRemaining = Math.max(0, timerTicksRemaining);
            this.armed = armed;
            this.lastSeenNanos = now;
            return oldChannel;
        }

        private synchronized boolean channelMatches(String wanted) { return channel.equals(wanted); }
        private synchronized String channelSnapshot() { return channel; }
        private synchronized boolean isStale(long now) { return now - lastSeenNanos > STALE_AFTER_NANOS; }

        private synchronized Map<String, Object> toListRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", idText);
            row.put("type", type);
            row.put("ageTicks", ageTicks);
            row.put("timerSecondsRemaining", timerTicksRemaining / 20.0);
            return row;
        }

        private synchronized Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", idText);
            result.put("channel", channel);
            result.put("dimension", dimension == null ? "" : dimension.toString());
            result.put("type", type);
            result.put("x", x); result.put("y", y); result.put("z", z);
            result.put("velocityX", vx); result.put("velocityY", vy); result.put("velocityZ", vz);
            result.put("speed", speed);
            result.put("ageTicks", ageTicks);
            result.putAll(current.toLua());
            result.put("timerTicksRemaining", timerTicksRemaining);
            result.put("timerSecondsRemaining", timerTicksRemaining / 20.0);
            result.put("armed", armed);
            result.put("updatePending", pending != null);
            return result;
        }
    }
}
