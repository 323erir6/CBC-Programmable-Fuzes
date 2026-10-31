package ua.ivan.cbcprogrammablefuzes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import ua.ivan.cbcprogrammablefuzes.peripheral.FuzeAccessUtil;

/** Generic lifetime protection for CBC and addon projectiles carrying this fuze. */
public final class ProjectilePersistence {
    private static final String DETONATING_KEY = CBCProgrammableFuzes.MOD_ID + ":detonating";

    private static final ClassValue<List<Field>> ITEM_STACK_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            Class<?> current = type;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getType() != ItemStack.class || Modifier.isStatic(field.getModifiers())) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // A serialized-NBT fallback below still covers inaccessible addon fields.
                    }
                }
                current = current.getSuperclass();
            }
            return List.copyOf(fields);
        }
    };

    private ProjectilePersistence() {}

    public static void markDetonating(AbstractCannonProjectile projectile) {
        projectile.getPersistentData().putBoolean(DETONATING_KEY, true);
    }

    public static boolean preventRemoval(Entity entity, Entity.RemovalReason reason) {
        if (!(entity instanceof AbstractCannonProjectile projectile)
            || reason != Entity.RemovalReason.DISCARDED
            || !FuzeConfig.persistenceAvailable()
            || projectile.getPersistentData().getBoolean(DETONATING_KEY)
            || projectile.isInGround()
            || projectile instanceof ProjectileRemovalState removalState
                && removalState.cbcProgrammableFuzes$removeAfterImpact()
            || !(CorrectionFlightController.isPersistent(projectile)
                || hasPersistentFuze(projectile))) {
            return false;
        }

        // Core and addon autocannon projectiles inherit this public lifetime setter. Giving the counter
        // another second avoids running their expiry code every tick while the projectile remains active.
        if (projectile instanceof AbstractAutocannonProjectile autocannonProjectile) {
            autocannonProjectile.setLifetime(20);
        }
        return true;
    }

    private static boolean hasPersistentFuze(AbstractCannonProjectile projectile) {
        for (Field field : ITEM_STACK_FIELDS.get(projectile.getClass())) {
            try {
                Object value = field.get(projectile);
                if (value instanceof ItemStack stack && isPersistentFuze(stack)) return true;
            } catch (IllegalAccessException | RuntimeException ignored) {
                // Try the entity's serialized form after checking every accessible field.
            }
        }

        CompoundTag serialized = new CompoundTag();
        try {
            projectile.saveWithoutId(serialized);
            return containsPersistentFuze(serialized, projectile);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean containsPersistentFuze(Tag tag, AbstractCannonProjectile projectile) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING)
                && "cbc_programmable_fuzes:programmable_fuze".equals(compound.getString("id"))) {
                ItemStack stack = ItemStack.parseOptional(projectile.level().registryAccess(), compound);
                if (isPersistentFuze(stack)) return true;
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child != null && containsPersistentFuze(child, projectile)) return true;
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                if (containsPersistentFuze(child, projectile)) return true;
            }
        }
        return false;
    }

    private static boolean isPersistentFuze(ItemStack stack) {
        return FuzeAccessUtil.isProgrammableFuze(stack)
            && FuzeSettings.read(stack).persistentProjectile();
    }
}
