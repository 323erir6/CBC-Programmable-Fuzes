package ua.ivan.cbcprogrammablefuzes.peripheral;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.ICannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AbstractAutocannonBreechBlockEntity;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import rbasamoyai.createbigcannons.munitions.autocannon.ammo_container.AutocannonAmmoContainerItem;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleHolder;

/** Accesses the shell which is physically loaded inside an assembled CBC cannon. */
public final class MountFuzeAccess implements ProgrammableFuzeAccess {
    private static final ClassValue<Optional<Method>> CONTRAPTION_GETTERS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method method = type.getMethod("getContraption");
                if (method.getParameterCount() != 0) return Optional.empty();
                return Optional.of(method);
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    };
    private static final ClassValue<List<Field>> CONTRAPTION_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (!PitchOrientedContraptionEntity.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // Keep any other accessible optional-addon fields.
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private final Object mountOrContraption;

    public MountFuzeAccess(Object mountOrContraption) {
        this.mountOrContraption = mountOrContraption;
    }

    @Nullable
    public static PitchOrientedContraptionEntity findContraption(@Nullable Object mountOrContraption) {
        if (mountOrContraption instanceof PitchOrientedContraptionEntity poce) return poce;
        if (mountOrContraption == null) return null;
        Optional<Method> getter = CONTRAPTION_GETTERS.get(mountOrContraption.getClass());
        if (getter.isPresent()) {
            try {
                Object result = getter.get().invoke(mountOrContraption);
                if (result instanceof PitchOrientedContraptionEntity poce) return poce;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall through to field discovery for addon mounts with a non-public or bridged getter.
            }
        }

        for (Field field : CONTRAPTION_FIELDS.get(mountOrContraption.getClass())) {
            try {
                Object value = field.get(mountOrContraption);
                if (value instanceof PitchOrientedContraptionEntity poce) return poce;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Keep checking other cached fields.
            }
        }
        return null;
    }

    @Override
    public ItemStack programmableFuze$getFuze() {
        LoadedFuze loaded = findLoadedFuze();
        return loaded == null ? ItemStack.EMPTY : loaded.fuze().copy();
    }

    @Override
    public boolean programmableFuze$setFuze(ItemStack stack) {
        if (!FuzeAccessUtil.isProgrammableFuze(stack)) return false;
        LoadedFuze loaded = findLoadedFuze();
        return loaded != null && loaded.write(stack);
    }

    @Override
    public ItemStack programmableFuze$getCorrectionModule() {
        LoadedModule loaded = findLoadedModule();
        return loaded == null ? ItemStack.EMPTY : loaded.module().copy();
    }

    @Override
    public boolean programmableFuze$setCorrectionModule(ItemStack stack) {
        if (!stack.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) return false;
        LoadedModule loaded = findLoadedModule();
        return loaded != null && loaded.write(stack.copyWithCount(1));
    }

    @Override
    public Map<String, Object> programmableFuze$getLoadedProjectileInfo() {
        return CannonInspection.getLoadedProjectileInfo(mountOrContraption);
    }

    @Override
    public Map<String, Object> programmableFuze$getLoadedPropellantInfo() {
        return CannonInspection.getLoadedPropellantInfo(mountOrContraption);
    }

    @Override
    public Map<String, Object> programmableFuze$getCannonInfo() {
        return CannonInspection.getCannonInfo(mountOrContraption);
    }

    @Nullable
    private LoadedModule findLoadedModule() {
        PitchOrientedContraptionEntity entity = findContraption(mountOrContraption);
        if (entity == null || entity.isRemoved()
            || !(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) return null;

        for (Map.Entry<BlockPos, BlockEntity> entry : cannon.presentBlockEntities.entrySet()) {
            BlockEntity blockEntity = entry.getValue();
            if (blockEntity instanceof CorrectionModuleHolder holder) {
                ItemStack module = holder.cbcProgrammableFuzes$getCorrectionModule();
                if (module.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) {
                    return new HolderLoadedModule(entity, cannon, entry.getKey().immutable(), blockEntity, holder,
                        module);
                }
            }
            if (blockEntity instanceof ICannonBlockEntity<?> owner
                && owner.cannonBehavior() instanceof BigCannonBehavior behavior) {
                StructureBlockInfo shell = behavior.block();
                if (shell.nbt() != null) {
                    ItemStack module = findSerializedModule(shell.nbt(), entity);
                    if (!module.isEmpty()) {
                        return new BlockCannonLoadedModule(entity, cannon, entry.getKey().immutable(), blockEntity,
                            behavior, shell, module);
                    }
                }
            }
        }

        BlockPos start = cannon.getStartPos();
        BlockEntity breech = start == null ? null : cannon.presentBlockEntities.get(start);
        if (breech == null) return null;
        CompoundTag nbt = breech.saveWithFullMetadata(entity.level().registryAccess());
        ItemStack module = findSerializedModule(nbt, entity);
        return module.isEmpty() ? null
            : new SerializedLoadedModule(entity, cannon, start.immutable(), breech, module);
    }

    private static ItemStack findSerializedModule(Tag tag, PitchOrientedContraptionEntity entity) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("TailModule", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(entity.level().registryAccess(), compound.getCompound("TailModule"));
                if (stack.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) return stack.copyWithCount(1);
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child == null) continue;
                ItemStack result = findSerializedModule(child, entity);
                if (!result.isEmpty()) return result;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) {
                ItemStack result = findSerializedModule(child, entity);
                if (!result.isEmpty()) return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean replaceFirstSerializedModule(Tag tag, CompoundTag replacement) {
        if (tag instanceof CompoundTag compound) {
            if (compound.contains("TailModule", Tag.TAG_COMPOUND)) {
                compound.put("TailModule", replacement.copy());
                return true;
            }
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (child != null && replaceFirstSerializedModule(child, replacement)) return true;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) {
                if (replaceFirstSerializedModule(child, replacement)) return true;
            }
        }
        return false;
    }

    @Nullable
    private LoadedFuze findLoadedFuze() {
        PitchOrientedContraptionEntity entity = findContraption(mountOrContraption);
        if (entity == null || entity.isRemoved()) return null;

        if (entity.getContraption() instanceof AbstractMountedCannonContraption cannon) {
            LoadedFuze loaded = findBlockCannonFuze(entity, cannon);
            if (loaded != null) return loaded;
            if (cannon instanceof MountedAutocannonContraption autocannon) {
                loaded = findAutocannonFuze(entity, autocannon);
                if (loaded != null) return loaded;
            }
            // Addon cannons commonly keep their loaded round in a custom breech BE. Scanning the serialized
            // virtual BEs makes this work without linking against every addon or assuming its private fields.
            return findSerializedFuze(entity, cannon);
        }
        return null;
    }

    @Nullable
    private static LoadedFuze findBlockCannonFuze(PitchOrientedContraptionEntity entity,
                                                   AbstractMountedCannonContraption cannon) {
        for (Map.Entry<BlockPos, BlockEntity> entry : cannon.presentBlockEntities.entrySet()) {
            if (!(entry.getValue() instanceof ICannonBlockEntity<?> owner)
                || !(owner.cannonBehavior() instanceof BigCannonBehavior behavior)) continue;
            StructureBlockInfo shell = behavior.block();
            if (shell.nbt() == null) continue;

            ItemStack fuze = findSerializedFuze(shell.nbt(), entity);
            if (!fuze.isEmpty()) {
                return new BlockCannonLoadedFuze(
                    entity, cannon, entry.getKey().immutable(), entry.getValue(), behavior, shell, fuze
                );
            }
        }
        return null;
    }

    @Nullable
    private static LoadedFuze findSerializedFuze(PitchOrientedContraptionEntity entity,
                                                  AbstractMountedCannonContraption cannon) {
        // Every installed item-ammunition cannon keeps the next round in its breech at startPos. Restricting the
        // serialized fallback to that BE avoids repeatedly serializing an entire long cannon in the tracker.
        BlockPos localPos = cannon.getStartPos();
        if (localPos == null) return null;
        BlockEntity blockEntity = cannon.presentBlockEntities.get(localPos);
        if (blockEntity == null) return null;
        CompoundTag nbt = blockEntity.saveWithFullMetadata(entity.level().registryAccess());
        ItemStack fuze = findSerializedFuze(nbt, entity);
        return fuze.isEmpty() ? null : new SerializedLoadedFuze(
            entity, cannon, localPos.immutable(), blockEntity, fuze
        );
    }

    private static ItemStack findSerializedFuze(Tag tag, PitchOrientedContraptionEntity entity) {
        if (tag instanceof CompoundTag compound) {
            if (isSerializedProgrammableFuze(compound)) {
                return ItemStack.parseOptional(entity.level().registryAccess(), compound).copyWithCount(1);
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child == null) continue;
                ItemStack result = findSerializedFuze(child, entity);
                if (!result.isEmpty()) return result;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) {
                ItemStack result = findSerializedFuze(child, entity);
                if (!result.isEmpty()) return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean replaceFirstSerializedFuze(Tag tag, CompoundTag replacement) {
        if (tag instanceof CompoundTag compound) {
            if (isSerializedProgrammableFuze(compound)) {
                for (String key : new ArrayList<>(compound.getAllKeys())) compound.remove(key);
                for (String key : replacement.getAllKeys()) {
                    Tag value = replacement.get(key);
                    if (value != null) compound.put(key, value.copy());
                }
                return true;
            }
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (child != null && replaceFirstSerializedFuze(child, replacement)) return true;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) {
                if (replaceFirstSerializedFuze(child, replacement)) return true;
            }
        }
        return false;
    }

    private static boolean isSerializedProgrammableFuze(CompoundTag tag) {
        return tag.contains("id", Tag.TAG_STRING)
            && "cbc_programmable_fuzes:programmable_fuze".equals(tag.getString("id"));
    }

    @Nullable
    private static LoadedFuze findAutocannonFuze(PitchOrientedContraptionEntity entity,
                                                  MountedAutocannonContraption cannon) {
        BlockPos breechPos = cannon.getStartPos();
        if (breechPos == null
            || !(cannon.presentBlockEntities.get(breechPos) instanceof AbstractAutocannonBreechBlockEntity breech)) {
            return null;
        }

        // The deque is consumed from the front, so expose the first programmable round in firing order.
        for (ItemStack ammunition : breech.getInputBuffer()) {
            NestedFuze nested = findNestedFuze(ammunition);
            if (nested != null) {
                return new AutocannonLoadedFuze(
                    entity, cannon, breechPos.immutable(), breech, ammunition, null, nested, nested.fuze()
                );
            }
        }

        ItemStack magazine = breech.getMagazine();
        if (magazine.isEmpty()) return null;

        ItemStack mainAmmo = AutocannonAmmoContainerItem.getMainAmmoStack(magazine);
        NestedFuze mainFuze = findNestedFuze(mainAmmo);
        if (mainFuze != null) {
            return new AutocannonLoadedFuze(
                entity, cannon, breechPos.immutable(), breech, mainAmmo,
                CBCDataComponents.AMMO, mainFuze, mainFuze.fuze()
            );
        }

        ItemStack tracerAmmo = AutocannonAmmoContainerItem.getTracerAmmoStack(magazine);
        NestedFuze tracerFuze = findNestedFuze(tracerAmmo);
        if (tracerFuze != null) {
            return new AutocannonLoadedFuze(
                entity, cannon, breechPos.immutable(), breech, tracerAmmo,
                CBCDataComponents.TRACERS, tracerFuze, tracerFuze.fuze()
            );
        }
        return null;
    }

    @Nullable
    private static NestedFuze findNestedFuze(ItemStack ammunition) {
        if (ammunition.isEmpty()) return null;

        ItemStack directFuze = ammunition
            .getOrDefault(CBCDataComponents.FUZE, ItemContainerContents.EMPTY)
            .copyOne();
        if (FuzeAccessUtil.isProgrammableFuze(directFuze)) {
            return new NestedFuze(false, directFuze);
        }

        ItemStack projectile = ammunition
            .getOrDefault(CBCDataComponents.PROJECTILE, ItemContainerContents.EMPTY)
            .copyOne();
        if (projectile.isEmpty()) return null;
        ItemStack projectileFuze = projectile
            .getOrDefault(CBCDataComponents.FUZE, ItemContainerContents.EMPTY)
            .copyOne();
        return FuzeAccessUtil.isProgrammableFuze(projectileFuze)
            ? new NestedFuze(true, projectileFuze)
            : null;
    }

    private static boolean replaceNestedFuze(ItemStack ammunition, NestedFuze nested, ItemStack fuze) {
        if (ammunition.isEmpty() || !FuzeAccessUtil.isProgrammableFuze(fuze)) return false;
        ItemStack copy = fuze.copyWithCount(1);
        ItemContainerContents contents = ItemContainerContents.fromItems(List.of(copy));

        if (!nested.insideProjectile()) {
            ammunition.set(CBCDataComponents.FUZE, contents);
            return true;
        }

        ItemStack projectile = ammunition
            .getOrDefault(CBCDataComponents.PROJECTILE, ItemContainerContents.EMPTY)
            .copyOne();
        if (projectile.isEmpty()) return false;
        projectile.set(CBCDataComponents.FUZE, contents);
        ammunition.set(CBCDataComponents.PROJECTILE, ItemContainerContents.fromItems(List.of(projectile)));
        return true;
    }

    private static void syncContraptionBlock(PitchOrientedContraptionEntity entity,
                                              AbstractMountedCannonContraption cannon,
                                              BlockPos localPos,
                                              BlockEntity blockEntity) {
        StructureBlockInfo oldBlock = cannon.getBlocks().get(localPos);
        if (oldBlock == null) return;
        blockEntity.setChanged();
        CompoundTag nbt = blockEntity.saveWithFullMetadata(entity.level().registryAccess());
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");
        cannon.getBlocks().put(localPos, new StructureBlockInfo(localPos, oldBlock.state(), nbt));
    }

    private interface LoadedModule {
        ItemStack module();
        boolean write(ItemStack module);
    }

    private record HolderLoadedModule(PitchOrientedContraptionEntity entity,
                                      AbstractMountedCannonContraption cannon,
                                      BlockPos localPos, BlockEntity blockEntity,
                                      CorrectionModuleHolder holder, ItemStack module) implements LoadedModule {
        @Override
        public boolean write(ItemStack stack) {
            holder.cbcProgrammableFuzes$setCorrectionModule(stack);
            syncContraptionBlock(entity, cannon, localPos, blockEntity);
            return true;
        }
    }

    private record BlockCannonLoadedModule(PitchOrientedContraptionEntity entity,
                                            AbstractMountedCannonContraption cannon,
                                            BlockPos localPos, BlockEntity owner,
                                            BigCannonBehavior behavior, StructureBlockInfo shell,
                                            ItemStack module) implements LoadedModule {
        @Override
        public boolean write(ItemStack stack) {
            StructureBlockInfo oldBarrel = cannon.getBlocks().get(localPos);
            if (oldBarrel == null) return false;
            CompoundTag shellNbt = shell.nbt() == null ? new CompoundTag() : shell.nbt().copy();
            Tag saved = stack.saveOptional(entity.level().registryAccess());
            if (!(saved instanceof CompoundTag replacement)
                || !replaceFirstSerializedModule(shellNbt, replacement)) return false;
            behavior.loadBlock(new StructureBlockInfo(shell.pos(), shell.state(), shellNbt));
            syncContraptionBlock(entity, cannon, localPos, owner);
            return true;
        }
    }

    private record SerializedLoadedModule(PitchOrientedContraptionEntity entity,
                                           AbstractMountedCannonContraption cannon,
                                           BlockPos localPos, BlockEntity blockEntity,
                                           ItemStack module) implements LoadedModule {
        @Override
        public boolean write(ItemStack stack) {
            CompoundTag nbt = blockEntity.saveWithFullMetadata(entity.level().registryAccess());
            Tag saved = stack.saveOptional(entity.level().registryAccess());
            if (!(saved instanceof CompoundTag replacement)
                || !replaceFirstSerializedModule(nbt, replacement)) return false;
            blockEntity.loadWithComponents(nbt, entity.level().registryAccess());
            syncContraptionBlock(entity, cannon, localPos, blockEntity);
            return true;
        }
    }

    private interface LoadedFuze {
        ItemStack fuze();
        boolean write(ItemStack fuze);
    }

    private record BlockCannonLoadedFuze(PitchOrientedContraptionEntity entity,
                                         AbstractMountedCannonContraption cannon,
                                         BlockPos localPos,
                                         BlockEntity owner,
                                         BigCannonBehavior behavior,
                                         StructureBlockInfo shell,
                                         ItemStack fuze) implements LoadedFuze {
        @Override
        public boolean write(ItemStack stack) {
            StructureBlockInfo oldBarrel = cannon.getBlocks().get(localPos);
            if (oldBarrel == null || !FuzeAccessUtil.isProgrammableFuze(stack)) return false;

            CompoundTag shellNbt = shell.nbt() == null ? new CompoundTag() : shell.nbt().copy();
            Tag saved = stack.copyWithCount(1).saveOptional(entity.level().registryAccess());
            if (!(saved instanceof CompoundTag replacement)
                || !replaceFirstSerializedFuze(shellNbt, replacement)) return false;

            StructureBlockInfo updatedShell = new StructureBlockInfo(shell.pos(), shell.state(), shellNbt);
            behavior.loadBlock(updatedShell);

            owner.setChanged();
            CompoundTag ownerNbt = owner.saveWithFullMetadata(entity.level().registryAccess());
            ownerNbt.remove("x");
            ownerNbt.remove("y");
            ownerNbt.remove("z");
            cannon.getBlocks().put(
                oldBarrel.pos(),
                new StructureBlockInfo(oldBarrel.pos(), oldBarrel.state(), ownerNbt)
            );
            return true;
        }
    }

    private record AutocannonLoadedFuze(PitchOrientedContraptionEntity entity,
                                        MountedAutocannonContraption cannon,
                                        BlockPos localPos,
                                        AbstractAutocannonBreechBlockEntity breech,
                                        ItemStack ammunition,
                                        net.minecraft.core.component.DataComponentType<ItemContainerContents> magazineComponent,
                                        NestedFuze nested,
                                        ItemStack fuze) implements LoadedFuze {
        @Override
        public boolean write(ItemStack stack) {
            if (!replaceNestedFuze(ammunition, nested, stack)) return false;
            if (magazineComponent != null) {
                ItemStack magazine = breech.getMagazine();
                if (magazine.isEmpty()) return false;
                magazine.set(magazineComponent, ItemContainerContents.fromItems(List.of(ammunition)));
                breech.setMagazine(magazine);
            }
            syncContraptionBlock(entity, cannon, localPos, breech);
            return true;
        }
    }

    private record SerializedLoadedFuze(PitchOrientedContraptionEntity entity,
                                        AbstractMountedCannonContraption cannon,
                                        BlockPos localPos,
                                        BlockEntity blockEntity,
                                        ItemStack fuze) implements LoadedFuze {
        @Override
        public boolean write(ItemStack stack) {
            if (!FuzeAccessUtil.isProgrammableFuze(stack)) return false;
            CompoundTag nbt = blockEntity.saveWithFullMetadata(entity.level().registryAccess());
            Tag saved = stack.copyWithCount(1).saveOptional(entity.level().registryAccess());
            if (!(saved instanceof CompoundTag replacement)) return false;
            if (!replaceFirstSerializedFuze(nbt, replacement)) return false;

            blockEntity.loadWithComponents(nbt, entity.level().registryAccess());
            syncContraptionBlock(entity, cannon, localPos, blockEntity);
            return true;
        }
    }

    private record NestedFuze(boolean insideProjectile, ItemStack fuze) {}
}
