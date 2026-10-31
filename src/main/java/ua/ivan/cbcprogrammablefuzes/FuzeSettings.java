package ua.ivan.cbcprogrammablefuzes;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import rbasamoyai.createbigcannons.index.CBCDataComponents;

public record FuzeSettings(boolean proximityEnabled, double proximityRadius,
                           boolean timerEnabled, double timerSeconds,
                           boolean persistentProjectile, String channel) {
    private static final String ROOT_KEY = "cbc_programmable_fuzes";
    private static final String PROXIMITY_ENABLED = "ProximityEnabled";
    private static final String PROXIMITY_RADIUS = "ProximityRadius";
    private static final String TIMER_ENABLED = "TimerEnabled";
    private static final String TIMER_SECONDS = "TimerSeconds";
    private static final String PERSISTENT_PROJECTILE = "PersistentProjectile";
    private static final String CHANNEL = "Channel";

    public FuzeSettings {
        channel = CorrectionSettings.sanitizeChannel(channel);
        proximityRadius = clampFinite(
            proximityRadius,
            FuzeConfig.minimumProximityRadius(),
            FuzeConfig.maximumProximityRadius(),
            FuzeConfig.defaultProximityRadius()
        );
        timerSeconds = clampFinite(
            timerSeconds,
            FuzeConfig.minimumTimerSeconds(),
            FuzeConfig.maximumTimerSeconds(),
            FuzeConfig.defaultTimerSeconds()
        );
    }

    public static FuzeSettings defaults() {
        return new FuzeSettings(
            FuzeConfig.DEFAULT_PROXIMITY_ACTIVE.get(),
            FuzeConfig.defaultProximityRadius(),
            FuzeConfig.DEFAULT_TIMER_ACTIVE.get(),
            FuzeConfig.defaultTimerSeconds(),
            FuzeConfig.DEFAULT_PERSISTENT_PROJECTILE.get(),
            CorrectionSettings.DEFAULT_CHANNEL
        );
    }

    public static FuzeSettings read(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return defaults();

        CompoundTag root = customData.getUnsafe();
        if (!root.contains(ROOT_KEY, Tag.TAG_COMPOUND)) return defaults();
        CompoundTag tag = root.getCompound(ROOT_KEY);

        boolean proximityEnabled = tag.contains(PROXIMITY_ENABLED)
            ? tag.getBoolean(PROXIMITY_ENABLED) : FuzeConfig.DEFAULT_PROXIMITY_ACTIVE.get();
        double proximityRadius = tag.contains(PROXIMITY_RADIUS, Tag.TAG_ANY_NUMERIC)
            ? tag.getDouble(PROXIMITY_RADIUS) : FuzeConfig.defaultProximityRadius();
        boolean timerEnabled = tag.contains(TIMER_ENABLED)
            ? tag.getBoolean(TIMER_ENABLED) : FuzeConfig.DEFAULT_TIMER_ACTIVE.get();
        double timerSeconds = tag.contains(TIMER_SECONDS, Tag.TAG_ANY_NUMERIC)
            ? tag.getDouble(TIMER_SECONDS) : FuzeConfig.defaultTimerSeconds();
        boolean persistentProjectile = tag.contains(PERSISTENT_PROJECTILE)
            ? tag.getBoolean(PERSISTENT_PROJECTILE) : FuzeConfig.DEFAULT_PERSISTENT_PROJECTILE.get();
        String channel = tag.contains(CHANNEL, Tag.TAG_STRING)
            ? tag.getString(CHANNEL) : CorrectionSettings.DEFAULT_CHANNEL;

        return new FuzeSettings(
            proximityEnabled, proximityRadius, timerEnabled, timerSeconds, persistentProjectile, channel
        );
    }

    public static void write(ItemStack stack, FuzeSettings settings) {
        writeData(stack, settings);

        // Loading/programming a round starts both arming processes from zero.
        if (settings.timerEnabled) {
            stack.set(CBCDataComponents.FUZE_TIMER, settings.timerTicks());
        } else {
            stack.remove(CBCDataComponents.FUZE_TIMER);
        }
        stack.remove(CBCDataComponents.AIR_TIME);
        stack.remove(CBCDataComponents.ARMED);
    }

    /** Updates a flying fuze without accidentally rearming unrelated modes. */
    public static void writeInFlight(ItemStack stack, FuzeSettings previous, FuzeSettings settings,
                                     boolean resetTimer, boolean resetProximityArming) {
        writeData(stack, settings);

        if (!settings.timerEnabled) {
            stack.remove(CBCDataComponents.FUZE_TIMER);
        } else if (!previous.timerEnabled
            || Double.compare(previous.timerSeconds, settings.timerSeconds) != 0
            || resetTimer && timerDefinitionChanged(previous, settings)) {
            stack.set(CBCDataComponents.FUZE_TIMER, settings.timerTicks());
        }

        if (!settings.proximityEnabled || resetProximityArming
            || previous.proximityEnabled != settings.proximityEnabled) {
            stack.remove(CBCDataComponents.AIR_TIME);
            stack.remove(CBCDataComponents.ARMED);
        }
    }

    private static void writeData(ItemStack stack, FuzeSettings settings) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PROXIMITY_ENABLED, settings.proximityEnabled);
        tag.putDouble(PROXIMITY_RADIUS, settings.proximityRadius);
        tag.putBoolean(TIMER_ENABLED, settings.timerEnabled);
        tag.putDouble(TIMER_SECONDS, settings.timerSeconds);
        tag.putBoolean(PERSISTENT_PROJECTILE, settings.persistentProjectile);
        tag.putString(CHANNEL, settings.channel);
        root.put(ROOT_KEY, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public Map<String, Object> toLua() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("proximityEnabled", proximityEnabled);
        result.put("proximityRadius", proximityRadius);
        result.put("timerEnabled", timerEnabled);
        result.put("timerSeconds", timerSeconds);
        result.put("persistentProjectile", persistentProjectile);
        result.put("channel", channel);
        return result;
    }

    public int timerTicks() {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(timerSeconds * 20.0));
    }

    public FuzeSettings withProximity(boolean enabled, double radius) {
        return new FuzeSettings(enabled, radius, timerEnabled, timerSeconds, persistentProjectile, channel);
    }

    public FuzeSettings withTimer(boolean enabled, double seconds) {
        return new FuzeSettings(proximityEnabled, proximityRadius, enabled, seconds, persistentProjectile, channel);
    }

    public FuzeSettings withPersistentProjectile(boolean persistent) {
        return new FuzeSettings(proximityEnabled, proximityRadius, timerEnabled, timerSeconds, persistent, channel);
    }

    public FuzeSettings withChannel(String value) {
        return new FuzeSettings(proximityEnabled, proximityRadius, timerEnabled, timerSeconds,
            persistentProjectile, value);
    }

    static boolean timerDefinitionChanged(FuzeSettings previous, FuzeSettings next) {
        return previous.timerEnabled != next.timerEnabled
            || Double.compare(previous.timerSeconds, next.timerSeconds) != 0;
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
