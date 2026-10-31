package ua.ivan.cbcprogrammablefuzes;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

public record CorrectionSettings(String channel, boolean hasTarget, double targetX, double targetY,
                                 double targetZ, boolean persistentProjectile) {
    public static final String DEFAULT_CHANNEL = "unsecured";
    private static final String ROOT = "cbc_programmable_fuzes_trajectory_correction";

    public CorrectionSettings {
        channel = sanitizeChannel(channel);
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) {
            hasTarget = false;
            targetX = targetY = targetZ = 0.0;
        }
    }

    public static CorrectionSettings defaults() {
        return new CorrectionSettings(DEFAULT_CHANNEL, false, 0, 0, 0, false);
    }

    public static CorrectionSettings read(ItemStack stack) {
        if (stack.isEmpty()) return defaults();
        return readRoot(stack.get(DataComponents.CUSTOM_DATA));
    }

    @Nullable
    public static CorrectionSettings readCarrier(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.getUnsafe().contains(ROOT, Tag.TAG_COMPOUND)) return null;
        return readRoot(data);
    }

    private static CorrectionSettings readRoot(@Nullable CustomData data) {
        if (data == null) return defaults();
        CompoundTag root = data.getUnsafe();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) return defaults();
        CompoundTag tag = root.getCompound(ROOT);
        return new CorrectionSettings(
            tag.getString("Channel"),
            tag.getBoolean("HasTarget"),
            tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"),
            tag.getBoolean("PersistentProjectile")
        );
    }

    public static void write(ItemStack stack, CorrectionSettings settings) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = new CompoundTag();
        tag.putString("Channel", settings.channel());
        tag.putBoolean("HasTarget", settings.hasTarget());
        tag.putDouble("TargetX", settings.targetX());
        tag.putDouble("TargetY", settings.targetY());
        tag.putDouble("TargetZ", settings.targetZ());
        tag.putBoolean("PersistentProjectile", settings.persistentProjectile());
        root.put(ROOT, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static void writeCarrier(ItemStack carrier, CorrectionSettings settings) {
        if (!carrier.isEmpty()) write(carrier, settings);
    }

    public static void clearCarrier(ItemStack carrier) {
        if (carrier.isEmpty()) return;
        CompoundTag root = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.remove(ROOT);
        if (root.isEmpty()) carrier.remove(DataComponents.CUSTOM_DATA);
        else carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public Vec3 target() {
        return new Vec3(targetX, targetY, targetZ);
    }

    public CorrectionSettings withChannel(String value) {
        return new CorrectionSettings(value, hasTarget, targetX, targetY, targetZ, persistentProjectile);
    }

    public CorrectionSettings withTarget(double x, double y, double z) {
        return new CorrectionSettings(channel, true, x, y, z, persistentProjectile);
    }

    public CorrectionSettings withPersistentProjectile(boolean value) {
        return new CorrectionSettings(channel, hasTarget, targetX, targetY, targetZ, value);
    }

    public static String sanitizeChannel(String channel) {
        if (channel == null || channel.isBlank()) return DEFAULT_CHANNEL;
        String cleaned = channel.strip();
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
