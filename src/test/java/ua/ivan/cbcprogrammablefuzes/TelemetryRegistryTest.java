package ua.ivan.cbcprogrammablefuzes;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;

/** Snapshot/index regression fixture; no world, entity ticking or CC Lua VM. */
final class TelemetryRegistryTest {
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        CorrectionTelemetryRegistry.clear();
        Class<?> type = Class.forName(CorrectionTelemetryRegistry.class.getName() + "$Entry");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Map<UUID, Object> entries = (Map<UUID, Object>) field(CorrectionTelemetryRegistry.class, "ENTRIES").get(null);
        Map<String, ConcurrentSkipListMap<String, Object>> channels =
                (Map<String, ConcurrentSkipListMap<String, Object>>) field(CorrectionTelemetryRegistry.class, "CHANNELS").get(null);
        long now = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            UUID id = new UUID(0, i + 1);
            String channel = "channel" + i % 10;
            Object entry = constructor.newInstance(id, channel, "test:shell", null);
            field(type, "lastSeenNanos").setLong(entry, now);
            field(type, "ageTicks").setInt(entry, i);
            field(type, "dimension").set(entry, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
            entries.put(id, entry);
            channels.computeIfAbsent(channel, ignored -> new ConcurrentSkipListMap<>()).put(id.toString(), entry);
        }
        String id = new UUID(0, 1).toString();
        check(CorrectionTelemetryRegistry.get("wrong", id) == null, "channel isolation");
        check(CorrectionTelemetryRegistry.get("channel0", "not-a-uuid") == null, "invalid UUID");
        var first = CorrectionTelemetryRegistry.get("channel0", id);
        check(first != null && first.get("ageTicks").equals(0), "fresh direct lookup");
        first.put("ageTicks", -99);
        check(CorrectionTelemetryRegistry.get("channel0", id).get("ageTicks").equals(0), "snapshot aliasing");
        field(type, "ageTicks").setInt(entries.get(new UUID(0, 1)), 123);
        check(CorrectionTelemetryRegistry.get("channel0", id).get("ageTicks").equals(123), "stale telemetry cache");
        var rows = CorrectionTelemetryRegistry.list("channel0");
        check(rows.size() == 50, "channel list count");
        for (int i = 1; i < rows.size(); i++) {
            check(((String) rows.get(i - 1).get("id")).compareTo((String) rows.get(i).get("id")) < 0, "UUID order");
        }
        UUID stale = new UUID(0, 2);
        field(type, "lastSeenNanos").setLong(entries.get(stale), now - 40_000_000_000L);
        CorrectionTelemetryRegistry.get("channel0", id);
        check(entries.containsKey(stale), "direct lookup scanned unrelated entries");
        check(CorrectionTelemetryRegistry.get("channel1", stale.toString()) == null, "expired entry visible");
        check(!channels.get("channel1").containsKey(stale.toString()), "expired channel index entry");
        UUID stale2 = new UUID(0, 3);
        field(type, "lastSeenNanos").setLong(entries.get(stale2), now - 40_000_000_000L);
        ((AtomicLong) field(CorrectionTelemetryRegistry.class, "LAST_PRUNE").get(null)).set(now - 2_000_000_000L);
        CorrectionTelemetryRegistry.list("channel0");
        check(!entries.containsKey(stale2), "periodic pruning");
        CorrectionTelemetryRegistry.clear();
        check(entries.isEmpty() && channels.isEmpty(), "server cleanup");
        System.out.println("PASS: telemetry snapshots, channel isolation, UUID sorting, expiry and cleanup (500 entries).");
    }
}
