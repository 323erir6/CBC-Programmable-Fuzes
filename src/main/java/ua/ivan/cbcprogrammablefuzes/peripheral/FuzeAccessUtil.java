package ua.ivan.cbcprogrammablefuzes.peripheral;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;

public final class FuzeAccessUtil {
    private static final ClassValue<List<Field>> BLOCK_ENTITY_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!BlockEntity.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // Optional integration can continue with the remaining accessible fields.
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private FuzeAccessUtil() {}

    public static boolean isProgrammableFuze(ItemStack stack) {
        return !stack.isEmpty() && stack.is(CBCProgrammableFuzes.PROGRAMMABLE_FUZE.get());
    }

    /**
     * NeoPeripheral keeps its mount in a private final field. Reflection here is performed only when a Lua method is
     * called and allows the optional integration to load without linking against NeoPeripheral or Compact Mount.
     */
    @Nullable
    public static PitchOrientedContraptionEntity findContraptionInPeripheral(Object peripheral) {
        for (Field field : BLOCK_ENTITY_FIELDS.get(peripheral.getClass())) {
            try {
                Object value = field.get(peripheral);
                PitchOrientedContraptionEntity contraption = MountFuzeAccess.findContraption(value);
                if (contraption != null) return contraption;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Optional integration: an incompatible wrapper simply exposes no loaded programmable fuze.
            }
        }
        return null;
    }
}
