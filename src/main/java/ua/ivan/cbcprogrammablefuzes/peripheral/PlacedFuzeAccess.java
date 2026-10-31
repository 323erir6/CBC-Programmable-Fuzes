package ua.ivan.cbcprogrammablefuzes.peripheral;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleHolder;

public final class PlacedFuzeAccess implements ProgrammableFuzeAccess {
    private static final ClassValue<List<Field>> SECONDARY_FUZE_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.getType() != ItemStack[].class) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // Keep any other accessible addon fields.
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private final FuzedBlockEntity blockEntity;

    public PlacedFuzeAccess(FuzedBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public ItemStack programmableFuze$getFuze() {
        if (blockEntity.isRemoved()) return ItemStack.EMPTY;
        ItemStack primary = blockEntity.getFuze();
        if (FuzeAccessUtil.isProgrammableFuze(primary)) return primary;
        SecondaryFuze secondary = findSecondaryFuze();
        return secondary == null ? ItemStack.EMPTY : secondary.stack();
    }

    @Override
    public boolean programmableFuze$setFuze(ItemStack stack) {
        if (blockEntity.isRemoved() || !FuzeAccessUtil.isProgrammableFuze(stack)) return false;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        if (FuzeAccessUtil.isProgrammableFuze(blockEntity.getFuze())) {
            blockEntity.setItem(1, copy);
        } else {
            SecondaryFuze secondary = findSecondaryFuze();
            if (secondary == null) return false;
            secondary.array()[secondary.index()] = copy;
            blockEntity.setChanged();
        }
        blockEntity.notifyUpdate();
        return true;
    }

    @Override
    public ItemStack programmableFuze$getCorrectionModule() {
        return !blockEntity.isRemoved() && blockEntity instanceof CorrectionModuleHolder holder
            ? holder.cbcProgrammableFuzes$getCorrectionModule() : ItemStack.EMPTY;
    }

    @Override
    public boolean programmableFuze$setCorrectionModule(ItemStack stack) {
        if (blockEntity.isRemoved() || !(blockEntity instanceof CorrectionModuleHolder holder)
            || !stack.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) return false;
        holder.cbcProgrammableFuzes$setCorrectionModule(stack.copyWithCount(1));
        blockEntity.setChanged();
        blockEntity.notifyUpdate();
        return true;
    }

    /** CBC AT cluster ammunition stores four additional standard fuzes in an addon-owned ItemStack array. */
    @Nullable
    private SecondaryFuze findSecondaryFuze() {
        for (Field field : SECONDARY_FUZE_FIELDS.get(blockEntity.getClass())) {
            try {
                ItemStack[] stacks = (ItemStack[]) field.get(blockEntity);
                if (stacks == null) continue;
                for (int i = 0; i < stacks.length; i++) {
                    if (FuzeAccessUtil.isProgrammableFuze(stacks[i])) {
                        return new SecondaryFuze(stacks, i, stacks[i]);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Optional addon state; keep checking other cached fields without a hard dependency.
            }
        }
        return null;
    }

    private record SecondaryFuze(ItemStack[] array, int index, ItemStack stack) {}
}
