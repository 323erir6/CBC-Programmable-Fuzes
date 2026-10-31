package ua.ivan.cbcprogrammablefuzes.peripheral;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.ICannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.AutocannonBlock;
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AbstractAutocannonBreechBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterialProperties;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBlock;
import rbasamoyai.createbigcannons.cannons.big_cannons.material.BigCannonMaterial;
import rbasamoyai.createbigcannons.cannons.big_cannons.material.BigCannonMaterialProperties;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;
import rbasamoyai.createbigcannons.munitions.autocannon.ammo_container.AutocannonAmmoContainerItem;
import rbasamoyai.createbigcannons.munitions.autocannon.config.AutocannonProjectilePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonMunitionBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;

/** Read-only snapshots exposed to CC:Tweaked. None of these methods mutate a cannon or its ammunition. */
final class CannonInspection {
    private CannonInspection() {}

    static Map<String, Object> getLoadedProjectileInfo(Object mount) {
        CannonContext context = context(mount);
        if (context == null) return Map.of();
        if (context.cannon() instanceof MountedAutocannonContraption autocannon) {
            return inspectAutocannonProjectile(context.entity(), autocannon);
        }
        return inspectBigCannonProjectile(context.entity(), context.cannon());
    }

    static Map<String, Object> getLoadedPropellantInfo(Object mount) {
        CannonContext context = context(mount);
        if (context == null) return Map.of();
        if (context.cannon() instanceof MountedAutocannonContraption autocannon) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cannonType", "autocannon");
            result.put("usesSeparateCharges", false);
            result.put("count", 0);
            result.put("segments", List.of());
            result.put("note", "Autocannon ammunition contains its own propellant");
            ItemStack next = peekAutocannonRound(autocannon);
            result.put("ammunitionPresent", !next.isEmpty());
            return result;
        }
        return inspectBigCannonPropellant(context.cannon());
    }

    static Map<String, Object> getCannonInfo(Object mount) {
        CannonContext context = context(mount);
        if (context == null) return Map.of();
        AbstractMountedCannonContraption cannon = context.cannon();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assembled", true);
        result.put("cannonClass", cannon.getClass().getName());
        result.put("lengthBlocks", cannon.getBlocks().size());
        Direction muzzleDirection = cannon.initialOrientation();
        int muzzleDistance = 0;
        for (BlockPos pos : cannon.getBlocks().keySet()) {
            int distance = pos.getX() * muzzleDirection.getStepX()
                + pos.getY() * muzzleDirection.getStepY()
                + pos.getZ() * muzzleDirection.getStepZ();
            muzzleDistance = Math.max(muzzleDistance, distance);
        }
        result.put("muzzleDistanceBlocks", Math.max(0, muzzleDistance));
        result.put("weight", (double) cannon.getWeightForStress());
        result.put("initialOrientation", cannon.initialOrientation().getSerializedName());
        result.put("axis", cannon.initialOrientation().getAxis().getName());
        result.put("startPosition", position(cannon.getStartPos()));
        addAddonCannonIdentity(result, cannon);

        for (StructureBlockInfo info : cannon.getBlocks().values()) {
            Block block = info.state().getBlock();
            if (block instanceof BigCannonBlock bigBlock) {
                addBigCannonProperties(result, bigBlock.getCannonMaterial(), cannon);
                return result;
            }
            if (block instanceof AutocannonBlock autocannonBlock) {
                addAutocannonProperties(result, autocannonBlock.getAutocannonMaterial(), cannon);
                return result;
            }
        }

        result.put("cannonType", "addon_or_unknown");
        result.putIfAbsent("material", "unknown");
        return result;
    }

    /**
     * Addons such as Military Supplement, CBC Modern Warfare and CBC AT use
     * their own cannon block/material classes.  Keeping this reflection-only
     * avoids hard dependencies while still giving external solvers enough
     * information to select the same nominal calibre as Realistic Ballistics.
     */
    private static void addAddonCannonIdentity(Map<String, Object> result,
                                               AbstractMountedCannonContraption cannon) {
        String className = cannon.getClass().getName();
        String lowerClass = className.toLowerCase(java.util.Locale.ROOT);
        result.put("ballisticProfile", ballisticProfile(lowerClass, null));

        try {
            Method getter = findNoArgMethod(cannon.getClass(), "getCannonMaterial");
            if (getter == null) return;
            getter.setAccessible(true);
            Object material = getter.invoke(cannon);
            if (material == null) return;
            Method nameGetter = findNoArgMethod(material.getClass(), "name");
            if (nameGetter == null) return;
            nameGetter.setAccessible(true);
            Object name = nameGetter.invoke(material);
            String materialName = String.valueOf(name);
            result.put("material", materialName);
            result.put("ballisticProfile", ballisticProfile(lowerClass,
                materialName.toLowerCase(java.util.Locale.ROOT)));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Identity is optional inspection metadata. Never make the normal
            // mount peripheral fail because an addon changed an internal API.
        }
    }

    @Nullable
    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static String ballisticProfile(String className, @Nullable String materialName) {
        if (className.contains("mounteddualcannoncontraption")) {
            String material = materialName == null ? "" : materialName;
            if (material.contains("large_single_")) return "military_large_125";
            if (material.contains("large_")) return "military_giant_180";
            if (material.contains("wide_")) return "military_medium_80";
            return "military_small_60";
        }
        if (className.contains("mountedtorpedotubecontraption")) return "torpedo_380";
        if (className.contains("mountedmediumcannoncontraption")) return "modern_medium_155";
        if (className.contains("mountedrotarycannoncontraption")) return "modern_rotary_20";
        if (className.contains("mountedheavyautocannoncontraption")) return "heavy_autocannon_45";
        if (className.contains("mountedmediumrocketrailcontraption")) return "medium_rocket_122";
        if (className.contains("mountedrocketpodcontraption")) return "rocket_pod_70";
        if (className.contains("mountedtwinautocannoncontraption")) return "autocannon_20";
        if (className.contains("mountedautocannoncontraption")) return "autocannon_20";
        if (className.contains("mountedbigcannoncontraption")) return "cbc_big_cannon";
        return "unknown";
    }

    @Nullable
    private static CannonContext context(Object mount) {
        PitchOrientedContraptionEntity entity = MountFuzeAccess.findContraption(mount);
        if (entity == null || entity.isRemoved()
            || !(entity.getContraption() instanceof AbstractMountedCannonContraption cannon)) return null;
        return new CannonContext(entity, cannon);
    }

    private static Map<String, Object> inspectBigCannonProjectile(PitchOrientedContraptionEntity entity,
                                                                  AbstractMountedCannonContraption cannon) {
        BlockPos current = cannon.getStartPos();
        Direction direction = cannon.initialOrientation();
        if (current == null || direction == null) return Map.of();

        List<StructureBlockInfo> projectileBlocks = new ArrayList<>();
        List<Map<String, Object>> positions = new ArrayList<>();
        int guard = cannon.getBlocks().size() + 2;
        while (guard-- > 0 && cannon.presentBlockEntities.get(current) instanceof ICannonBlockEntity<?> owner
            && owner.cannonBehavior() instanceof BigCannonBehavior behavior) {
            StructureBlockInfo contained = behavior.block();
            if (contained.state().getBlock() instanceof ProjectileBlock<?>) {
                projectileBlocks.add(contained);
                positions.add(position(current));
            } else if (!projectileBlocks.isEmpty()) {
                break;
            }
            current = current.relative(direction);
        }
        if (projectileBlocks.isEmpty()) return Map.of();

        StructureBlockInfo first = projectileBlocks.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cannonType", "big_cannon");
        result.put("id", blockId(first.state().getBlock()));
        result.put("name", first.state().getBlock().getName().getString());
        result.put("blockCount", projectileBlocks.size());
        result.put("positions", positions);
        result.put("complete", first.state().getBlock() instanceof ProjectileBlock<?> projectile
            && projectile.isComplete(projectileBlocks, direction));

        // CBC adds projectile-specific charge power after it has counted the
        // powder in the barrel. APFSDS is the important example: Military
        // Supplement adds four whole charge units, so reporting only the
        // propellant made external ballistic solvers underestimate launch
        // speed and increasingly overshoot in elevation at long range.
        // Build the same unspawned projectile preview CBC uses while firing;
        // this is read-only and also gives addon projectiles' real mass and
        // collision diameter without maintaining another hard-coded table.
        if (first.state().getBlock() instanceof ProjectileBlock<?> projectileBlock) {
            try {
                AbstractBigCannonProjectile preview = projectileBlock.getProjectile(
                    entity.level(), projectileBlocks);
                if (preview != null) {
                    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(preview.getType());
                    if (entityId != null) result.put("projectileEntity", entityId.toString());
                    result.put("projectileMass", (double) preview.getProjectileMass());
                    result.put("addedChargePower", (double) preview.addedChargePower());
                    result.put("minimumChargePower", (double) preview.minimumChargePower());
                    result.put("physicalDiameterMetres", Math.max(0.05,
                        Math.min(preview.getBbWidth(), preview.getBbHeight())));
                }
            } catch (RuntimeException ignored) {
                // Inspection must remain available for unusual addon blocks
                // whose projectile factory needs firing-only context.
            }
        }

        CompoundTag nbt = first.nbt();
        ItemStack fuze = nbt == null ? ItemStack.EMPTY
            : ItemStack.parseOptional(entity.level().registryAccess(), nbt.getCompound("Fuze"));
        ItemStack tracer = nbt == null ? ItemStack.EMPTY
            : ItemStack.parseOptional(entity.level().registryAccess(), nbt.getCompound("Tracer"));
        ItemStack module = nbt == null ? ItemStack.EMPTY : findSerializedItem(nbt, entity,
            "cbc_programmable_fuzes:trajectory_correction_module");
        addOptionalStack(result, "fuze", fuze);
        addOptionalStack(result, "tracer", tracer);
        addOptionalStack(result, "trajectoryCorrectionModule", module);
        result.put("hasFuze", !fuze.isEmpty());
        result.put("hasProgrammableFuze", FuzeAccessUtil.isProgrammableFuze(fuze));
        result.put("hasTracer", !tracer.isEmpty());
        result.put("hasTrajectoryCorrectionModule", module.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get()));
        return result;
    }

    private static Map<String, Object> inspectBigCannonPropellant(AbstractMountedCannonContraption cannon) {
        BlockPos current = cannon.getStartPos();
        Direction direction = cannon.initialOrientation();
        if (current == null || direction == null) return Map.of();

        List<Map<String, Object>> segments = new ArrayList<>();
        double totalPower = 0;
        double totalStress = 0;
        double totalSpread = 0;
        double effectiveRecoil = 0;
        int guard = cannon.getBlocks().size() + 2;
        while (guard-- > 0 && cannon.presentBlockEntities.get(current) instanceof ICannonBlockEntity<?> owner
            && owner.cannonBehavior() instanceof BigCannonBehavior behavior) {
            StructureBlockInfo contained = behavior.block();
            Block block = contained.state().getBlock();
            if (block instanceof ProjectileBlock<?>) break;
            if (block instanceof BigCannonPropellantBlock propellant) {
                double power = Math.max(0, propellant.getChargePower(contained));
                double stress = propellant.getStressOnCannon(contained);
                double spread = propellant.getSpread(contained);
                double recoil = Math.max(0, propellant.getRecoil(contained));
                Map<String, Object> segment = new LinkedHashMap<>();
                segment.put("id", blockId(block));
                segment.put("name", block.getName().getString());
                segment.put("position", position(current));
                segment.put("chargePower", power);
                segment.put("stress", stress);
                segment.put("spread", spread);
                segment.put("recoil", recoil);
                if (contained.state().hasProperty(BigCannonMunitionBlock.DAMP)) {
                    segment.put("damp", contained.state().getValue(BigCannonMunitionBlock.DAMP));
                }
                if (contained.state().hasProperty(BigCannonMunitionBlock.WATERLOGGED)) {
                    segment.put("waterlogged", contained.state().getValue(BigCannonMunitionBlock.WATERLOGGED));
                }
                segments.add(segment);
                totalPower += power;
                totalStress += stress;
                totalSpread += spread;
                effectiveRecoil = recoil;
            }
            current = current.relative(direction);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cannonType", "big_cannon");
        result.put("usesSeparateCharges", true);
        result.put("count", segments.size());
        result.put("segments", segments);
        result.put("totalChargePower", totalPower);
        result.put("totalStress", totalStress);
        result.put("totalSpread", totalSpread);
        result.put("effectiveRecoil", effectiveRecoil);
        if (cannon instanceof MountedBigCannonContraption bigCannon) {
            int safe = bigCannon.getMaxSafeCharges();
            result.put("maxSafeCharges", safe);
            result.put("overloaded", totalStress > safe);
        }
        return result;
    }

    private static Map<String, Object> inspectAutocannonProjectile(PitchOrientedContraptionEntity entity,
                                                                   MountedAutocannonContraption cannon) {
        ItemStack ammunition = peekAutocannonRound(cannon);
        if (ammunition.isEmpty()) return Map.of();
        ItemStack projectile = ammunition
            .getOrDefault(CBCDataComponents.PROJECTILE, ItemContainerContents.EMPTY).copyOne();
        ItemStack shown = projectile.isEmpty() ? ammunition : projectile;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cannonType", "autocannon");
        result.put("id", itemId(shown));
        result.put("name", shown.getHoverName().getString());
        result.put("ammunitionId", itemId(ammunition));
        result.put("count", ammunition.getCount());
        if (ammunition.getItem() instanceof AutocannonAmmoItem ammo) {
            result.put("ammunitionType", ammo.getType().name().toLowerCase());
            result.put("tracer", ammo.isTracer(ammunition));
            EntityType<?> entityType = ammo.getEntityType(ammunition);
            if (entityType != null) result.put("projectileEntity", BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString());
            AutocannonProjectilePropertiesComponent properties = ammo.getAutocannonProperties(ammunition);
            if (properties != null) {
                result.put("addedRecoil", properties.addedRecoil());
                result.put("canSquib", properties.canSquib());
            }
        }

        ItemStack directFuze = ammunition.getOrDefault(CBCDataComponents.FUZE, ItemContainerContents.EMPTY).copyOne();
        ItemStack nestedFuze = shown.getOrDefault(CBCDataComponents.FUZE, ItemContainerContents.EMPTY).copyOne();
        ItemStack fuze = directFuze.isEmpty() ? nestedFuze : directFuze;
        addOptionalStack(result, "fuze", fuze);
        result.put("hasFuze", !fuze.isEmpty());
        result.put("hasProgrammableFuze", FuzeAccessUtil.isProgrammableFuze(fuze));

        CompoundTag serialized = (CompoundTag) ammunition.saveOptional(entity.level().registryAccess());
        ItemStack module = findSerializedItem(serialized, entity,
            "cbc_programmable_fuzes:trajectory_correction_module");
        addOptionalStack(result, "trajectoryCorrectionModule", module);
        result.put("hasTrajectoryCorrectionModule", !module.isEmpty());
        return result;
    }

    private static ItemStack peekAutocannonRound(MountedAutocannonContraption cannon) {
        BlockPos breechPos = cannon.getStartPos();
        if (breechPos == null
            || !(cannon.presentBlockEntities.get(breechPos) instanceof AbstractAutocannonBreechBlockEntity breech)) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : breech.getInputBuffer()) {
            if (!stack.isEmpty()) return stack.copy();
        }
        ItemStack magazine = breech.getMagazine();
        if (magazine.isEmpty()) return ItemStack.EMPTY;
        ItemStack main = AutocannonAmmoContainerItem.getMainAmmoStack(magazine);
        ItemStack tracers = AutocannonAmmoContainerItem.getTracerAmmoStack(magazine);
        int total = AutocannonAmmoContainerItem.getTotalAmmoCount(magazine);
        int spacing = AutocannonAmmoContainerItem.getTracerSpacing(magazine);
        boolean tracerNext = total > 0 && total % (spacing + 1) == 0;
        ItemStack selected = tracerNext && !tracers.isEmpty() ? tracers : main;
        if (selected.isEmpty()) selected = tracers;
        return selected.copy();
    }

    private static void addBigCannonProperties(Map<String, Object> result, BigCannonMaterial material,
                                               AbstractMountedCannonContraption cannon) {
        BigCannonMaterialProperties p = material.properties();
        result.put("cannonType", "big_cannon");
        result.put("material", material.name().toString());
        result.put("weightPerBlock", (double) p.weight());
        result.put("minimumVelocityPerBarrel", p.minimumVelocityPerBarrel());
        result.put("maxSafePropellantStress", p.maxSafePropellantStress());
        result.put("failureMode", p.failureMode().getSerializedName());
        result.put("weldable", p.isWeldable());
        result.put("weldDamage", p.weldDamage());
        result.put("weldStressPenalty", p.weldStressPenalty());
        result.put("minimumSpread", (double) p.minimumSpread());
        result.put("spreadReductionPerBarrel", (double) p.spreadReductionPerBarrel());
        if (cannon instanceof MountedBigCannonContraption bigCannon) {
            result.put("maxSafeCharges", bigCannon.getMaxSafeCharges());
            result.put("weldedPenaltyPresent", bigCannon.hasWeldedPenalty);
        }
    }

    private static void addAutocannonProperties(Map<String, Object> result, AutocannonMaterial material,
                                                AbstractMountedCannonContraption cannon) {
        AutocannonMaterialProperties p = material.properties();
        result.put("cannonType", "autocannon");
        result.put("material", material.name().toString());
        result.put("weightPerBlock", (double) p.weight());
        result.put("maximumBarrelLength", p.maxBarrelLength());
        result.put("baseSpread", (double) p.baseSpread());
        result.put("spreadReductionPerBarrel", (double) p.spreadReductionPerBarrel());
        result.put("baseSpeedBlocksPerTick", (double) p.baseSpeed());
        result.put("baseSpeedMetersPerSecond", p.baseSpeed() * 20.0);
        result.put("speedIncreasePerBarrelBlocksPerTick", (double) p.speedIncreasePerBarrel());
        result.put("maximumSpeedIncreases", p.maxSpeedIncreases());
        result.put("projectileLifetimeTicks", p.projectileLifetime());
        result.put("baseRecoil", (double) p.baseRecoil());
        result.put("weldable", p.isWeldable());
        result.put("weldDamage", p.weldDamage());
        result.put("weldStressPenalty", p.weldStressPenalty());
    }

    private static void addOptionalStack(Map<String, Object> result, String key, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", itemId(stack));
        value.put("name", stack.getHoverName().getString());
        value.put("count", stack.getCount());
        result.put(key, value);
    }

    private static ItemStack findSerializedItem(Tag tag, PitchOrientedContraptionEntity entity, String wantedId) {
        if (tag instanceof CompoundTag compound) {
            if (wantedId.equals(compound.getString("id"))) {
                return ItemStack.parseOptional(entity.level().registryAccess(), compound).copyWithCount(1);
            }
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child == null) continue;
                ItemStack found = findSerializedItem(child, entity, wantedId);
                if (!found.isEmpty()) return found;
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) {
                ItemStack found = findSerializedItem(child, entity, wantedId);
                if (!found.isEmpty()) return found;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Map<String, Object> position(@Nullable BlockPos pos) {
        if (pos == null) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", pos.getX());
        result.put("y", pos.getY());
        result.put("z", pos.getZ());
        return result;
    }

    private static String blockId(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id.toString();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private record CannonContext(PitchOrientedContraptionEntity entity,
                                 AbstractMountedCannonContraption cannon) {}
}
