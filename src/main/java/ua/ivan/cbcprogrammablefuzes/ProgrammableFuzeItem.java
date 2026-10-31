package ua.ivan.cbcprogrammablefuzes;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.CBCCompatTransformers;
import rbasamoyai.createbigcannons.config.CBCConfigs;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public final class ProgrammableFuzeItem extends FuzeItem {
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
    private static final long PHYSICAL_ENTRY_GRACE_TICKS = 100L;
    private static final Map<UUID, PhysicalEntry> PHYSICAL_ENTRIES = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Long> LAST_PHYSICS_GAME_TIME = new IdentityHashMap<>();
    private static final Map<ServerLevel, WorkBudget> WORK_BUDGETS = new IdentityHashMap<>();

    public ProgrammableFuzeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        // Apply only the newest high-frequency CC command immediately before
        // evaluating this tick. Intermediate commands never build a backlog.
        FuzeSettings settings = InFlightFuzeRegistry.applyAndUpdate(projectile, stack);
        if (FuzeConfig.proximityAvailable() && settings.proximityEnabled()) {
            int airTime = stack.getOrDefault(CBCDataComponents.AIR_TIME, 0);
            if (airTime > CBCConfigs.server().munitions.proximityFuzeArmingTime.get()) {
                stack.set(CBCDataComponents.ARMED, true);
            }
            stack.set(CBCDataComponents.AIR_TIME, ++airTime);
        }
        return tickPhysicalRuntime(stack, projectile, settings);
    }

    @Override
    public boolean onProjectileClip(ItemStack stack, AbstractCannonProjectile projectile, Vec3 start, Vec3 end,
                                    ProjectileContext context, boolean baseFuze) {
        FuzeSettings settings = FuzeSettings.read(stack);
        if (FuzeConfig.timerAvailable() && settings.timerEnabled() && settings.timerTicks() == 0) {
            context.setDetonationPositionForClip(start);
            return markDetonation(projectile);
        }
        if (baseFuze) return false;
        if (!FuzeConfig.proximityAvailable() || !settings.proximityEnabled()) return false;
        if (!stack.has(CBCDataComponents.ARMED)) return false;

        double radius = settings.proximityRadius();
        Vec3 movement = end.subtract(start);
        double movementSqr = movement.lengthSqr();
        Vec3 forward = movementSqr > 1.0e-8
            ? movement.normalize()
            : projectile.getOrientation().normalize();
        if (forward.lengthSqr() < 1.0e-8) forward = new Vec3(0.0, 0.0, 1.0);

        Vec3 right = forward.cross(UP).normalize();
        if (right.lengthSqr() < 1.0e-8) right = new Vec3(1.0, 0.0, 0.0);
        Vec3 up = right.cross(forward).normalize();

        Level level = projectile.level();
        double movementLength = Math.sqrt(movementSqr);
        double earliestTrigger = Double.POSITIVE_INFINITY;

        if (ProximityBroadphase.needsBlockRays(level, start, end, radius)) {
            ProximityRayGrid grid = ProximityRayGrid.get(radius);
            int rayCount = claimRayQuota(level, grid.rays.length);
            for (int sample = 0; sample < rayCount; sample++) {
                ProximityRayGrid.Ray ray = grid.rays[sampledRayIndex(sample, rayCount, grid.rays.length)];
                Vec3 offset = right.scale(ray.x()).add(up.scale(ray.y()));
                Vec3 rayStart = start.add(offset);
                double forwardReach = ray.reach();
                Vec3 rayEnd = end.add(offset).add(forward.scale(forwardReach));
                HitResult hit = level.clip(new ClipContext(
                    rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile
                ));
                if (hit.getType() != HitResult.Type.MISS) {
                    Vec3 hitLocation = CBCCompatTransformers.transformVec3(level, hit.getLocation());
                    double hitAlong = hitLocation.subtract(rayStart).dot(forward);
                    earliestTrigger = Math.min(earliestTrigger, Math.max(0.0, hitAlong - forwardReach));
                    if (earliestTrigger == 0.0) break;
                }
            }
        }

        if (earliestTrigger > 0.0) {
            AABB entitySearch = projectile.getBoundingBox().expandTowards(movement).inflate(radius);
            Vec3 entityRayEnd = end.add(forward.scale(radius));
            for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class, entitySearch, projectile::canHitEntity)) {
                AABB hitBox = target.getBoundingBox().inflate(radius);
                if (hitBox.contains(start)) {
                    earliestTrigger = 0.0;
                    break;
                } else {
                    var hit = hitBox.clip(start, entityRayEnd);
                    if (hit.isPresent()) {
                        earliestTrigger = Math.min(
                            earliestTrigger,
                            Math.max(0.0, hit.get().subtract(start).dot(forward))
                        );
                    }
                }
            }
        }

        if (!Double.isFinite(earliestTrigger)) return false;
        context.setDetonationPositionForClip(
            start.add(forward.scale(Math.min(movementLength, earliestTrigger)))
        );
        return markDetonation(projectile);
    }

    @Override
    public boolean onProjectileImpact(ItemStack stack, AbstractCannonProjectile projectile, HitResult hitResult,
                                      AbstractCannonProjectile.ImpactResult impactResult, boolean baseFuze) {
        boolean detonate = FuzeConfig.proximityAvailable()
            && FuzeSettings.read(stack).proximityEnabled()
            && !baseFuze;
        return detonate && markDetonation(projectile);
    }

    @Override
    public boolean onProjectileExpiry(ItemStack stack, AbstractCannonProjectile projectile) {
        FuzeSettings settings = FuzeSettings.read(stack);
        if (FuzeConfig.persistenceAvailable() && settings.persistentProjectile()) return false;
        boolean detonate = (FuzeConfig.proximityAvailable() && settings.proximityEnabled())
            || (FuzeConfig.timerAvailable() && settings.timerEnabled());
        return detonate && markDetonation(projectile);
    }

    @Override
    public boolean canLingerInGround(ItemStack stack, AbstractCannonProjectile projectile) {
        FuzeSettings settings = FuzeSettings.read(stack);
        return (FuzeConfig.timerAvailable() && settings.timerEnabled())
            || (FuzeConfig.persistenceAvailable() && settings.persistentProjectile());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FuzeSettings settings = FuzeSettings.read(stack);
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.programmable_fuze.proximity",
            settings.proximityEnabled(), String.format(Locale.ROOT, "%.2f", settings.proximityRadius())
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.programmable_fuze.timer",
            settings.timerEnabled(), String.format(Locale.ROOT, "%.3f", settings.timerSeconds())
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.programmable_fuze.persistent",
            settings.persistentProjectile()
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.programmable_fuze.computer"
        ).withStyle(ChatFormatting.DARK_AQUA));
    }

    private static boolean markDetonation(AbstractCannonProjectile projectile) {
        ProjectilePersistence.markDetonating(projectile);
        return true;
    }

    /** Called by Sable after every physics substep. */
    public static void onPostPhysicsTick(ForgeSablePostPhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        long gameTime = level.getGameTime();
        LAST_PHYSICS_GAME_TIME.put(level, gameTime);
        double timeStep = event.getTimeStep();
        if (!(timeStep > 0.0) || !Double.isFinite(timeStep)) return;
        timeStep = Math.min(timeStep, 0.25);

        for (Map.Entry<UUID, PhysicalEntry> mapped : PHYSICAL_ENTRIES.entrySet()) {
            UUID id = mapped.getKey();
            PhysicalEntry entry = mapped.getValue();
            AbstractCannonProjectile projectile = entry.projectile.get();
            if (projectile == null || projectile.isRemoved()
                || gameTime - entry.lastTouchedGameTime > PHYSICAL_ENTRY_GRACE_TICKS) {
                PHYSICAL_ENTRIES.remove(id, entry);
                continue;
            }
            if (projectile.level() != level) continue;

            ItemStack stack = entry.stack;
            FuzeSettings settings = InFlightFuzeRegistry.applyPendingForPhysicalStep(projectile, stack);
            entry.synchronizeTimer(stack, settings);

            if (FuzeConfig.timerAvailable() && settings.timerEnabled()) {
                entry.remainingTimerSeconds -= timeStep;
                if (entry.remainingTimerSeconds <= 0.0) {
                    if (detonateFromPhysics(level, projectile, entry, projectile.position())) {
                        PHYSICAL_ENTRIES.remove(id, entry);
                    }
                    continue;
                }
            }

            if (!FuzeConfig.proximityAvailable() || !settings.proximityEnabled()
                || !stack.has(CBCDataComponents.ARMED)
                || !hasSableCandidate(level, projectile.position(), settings.proximityRadius())
                || !claimPhysicalNarrowPhase(level)) {
                continue;
            }
            if (stack.getItem() instanceof ProgrammableFuzeItem fuze
                && fuze.hasPhysicalProximityHit(stack, projectile, settings.proximityRadius())
                && detonateFromPhysics(level, projectile, entry, projectile.position())) {
                PHYSICAL_ENTRIES.remove(id, entry);
            }
        }
    }

    public static void forgetPhysical(Entity entity) {
        if (entity instanceof AbstractCannonProjectile) PHYSICAL_ENTRIES.remove(entity.getUUID());
    }

    public static void clearPhysicalRuntime() {
        PHYSICAL_ENTRIES.clear();
        LAST_PHYSICS_GAME_TIME.clear();
        WORK_BUDGETS.clear();
    }

    private static boolean tickPhysicalRuntime(ItemStack stack, AbstractCannonProjectile projectile,
                                               FuzeSettings settings) {
        if (!(projectile.level() instanceof ServerLevel level)) return false;
        if ((!FuzeConfig.timerAvailable() || !settings.timerEnabled())
            && (!FuzeConfig.proximityAvailable() || !settings.proximityEnabled())) {
            PHYSICAL_ENTRIES.remove(projectile.getUUID());
            return false;
        }

        PhysicalEntry entry = PHYSICAL_ENTRIES.compute(projectile.getUUID(), (id, existing) -> {
            if (existing == null || existing.projectile.get() != projectile) {
                existing = new PhysicalEntry(projectile, stack);
            }
            existing.stack = stack;
            existing.lastTouchedGameTime = level.getGameTime();
            existing.synchronizeTimer(stack, settings);
            return existing;
        });

        if (entry.pendingDetonation) {
            PHYSICAL_ENTRIES.remove(projectile.getUUID(), entry);
            return markDetonation(projectile);
        }

        long lastPhysics = LAST_PHYSICS_GAME_TIME.getOrDefault(level, Long.MIN_VALUE);
        boolean physicsClockActive = lastPhysics >= level.getGameTime() - 1L;
        if (FuzeConfig.timerAvailable() && settings.timerEnabled()) {
            if (!physicsClockActive) entry.remainingTimerSeconds -= 0.05;
            entry.publishTimer(stack);
            if (entry.remainingTimerSeconds <= 0.0) {
                PHYSICAL_ENTRIES.remove(projectile.getUUID(), entry);
                return markDetonation(projectile);
            }
        }
        return false;
    }

    private boolean hasPhysicalProximityHit(ItemStack stack, AbstractCannonProjectile projectile,
                                            double radius) {
        Vec3 centre = projectile.position();
        Vec3 forward = projectile.getDeltaMovement().normalize();
        if (forward.lengthSqr() < 1.0e-8) forward = projectile.getOrientation().normalize();
        if (forward.lengthSqr() < 1.0e-8) forward = new Vec3(0.0, 0.0, 1.0);
        Vec3 right = forward.cross(UP).normalize();
        if (right.lengthSqr() < 1.0e-8) right = new Vec3(1.0, 0.0, 0.0);
        Vec3 up = right.cross(forward).normalize();

        ProximityRayGrid grid = ProximityRayGrid.get(radius);
        int rayCount = claimRayQuota(projectile.level(), grid.rays.length);
        for (int sample = 0; sample < rayCount; sample++) {
            ProximityRayGrid.Ray ray = grid.rays[sampledRayIndex(sample, rayCount, grid.rays.length)];
            Vec3 offset = right.scale(ray.x()).add(up.scale(ray.y()));
            Vec3 rayStart = centre.add(offset).subtract(forward.scale(ray.reach()));
            Vec3 rayEnd = centre.add(offset).add(forward.scale(ray.reach()));
            HitResult hit = projectile.level().clip(new ClipContext(
                rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile
            ));
            if (hit.getType() != HitResult.Type.MISS) return true;
        }
        return false;
    }

    private static boolean hasSableCandidate(ServerLevel level, Vec3 centre, double radius) {
        double search = Math.max(0.0, radius) + 1.0;
        BoundingBox3d bounds = new BoundingBox3d(
            centre.x - search, centre.y - search, centre.z - search,
            centre.x + search, centre.y + search, centre.z + search
        );
        return Sable.HELPER.getAllIntersecting(level, bounds).iterator().hasNext();
    }

    private static boolean detonateFromPhysics(ServerLevel level, AbstractCannonProjectile projectile,
                                               PhysicalEntry entry, Vec3 position) {
        if (entry.pendingDetonation || projectile.isRemoved()) return false;
        entry.pendingDetonation = true;
        int chunkX = projectile.blockPosition().getX() >> 4;
        int chunkZ = projectile.blockPosition().getZ() >> 4;
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null
            || !(projectile instanceof PhysicalDetonationAccess access)) {
            return false;
        }
        ProjectilePersistence.markDetonating(projectile);
        access.cbcProgrammableFuzes$detonateFromPhysics(position);
        return true;
    }

    private static int claimRayQuota(Level level, int requested) {
        if (!(level instanceof ServerLevel serverLevel) || requested <= 1) return Math.max(1, requested);
        WorkBudget budget = budget(serverLevel);
        int perCheck = Math.min(requested, FuzeConfig.MAX_PROXIMITY_RAYS_PER_CHECK.get());
        int granted = Math.min(perCheck, budget.raysRemaining);
        if (granted > 0) {
            budget.raysRemaining -= granted;
            return granted;
        }
        return 1;
    }

    private static boolean claimPhysicalNarrowPhase(ServerLevel level) {
        WorkBudget budget = budget(level);
        if (budget.physicalNarrowPhaseRemaining <= 0) return false;
        --budget.physicalNarrowPhaseRemaining;
        return true;
    }

    private static WorkBudget budget(ServerLevel level) {
        WorkBudget budget = WORK_BUDGETS.computeIfAbsent(level, ignored -> new WorkBudget());
        long gameTime = level.getGameTime();
        if (budget.gameTime != gameTime) {
            budget.gameTime = gameTime;
            budget.raysRemaining = FuzeConfig.MAX_PROXIMITY_RAYS_PER_TICK.get();
            budget.physicalNarrowPhaseRemaining =
                FuzeConfig.MAX_PHYSICAL_NARROW_PHASE_CHECKS_PER_TICK.get();
        }
        return budget;
    }

    static int sampledRayIndex(int sample, int samples, int total) {
        if (sample <= 0 || samples <= 1 || total <= 1) return 0;
        if (samples == 2) return total - 1;
        return 1 + (int) ((long) (sample - 1) * (total - 2) / (samples - 2));
    }

    public interface PhysicalDetonationAccess {
        void cbcProgrammableFuzes$detonateFromPhysics(Vec3 position);
    }

    private static final class PhysicalEntry {
        private final WeakReference<AbstractCannonProjectile> projectile;
        private ItemStack stack;
        private double remainingTimerSeconds = Double.NaN;
        private double timerDefinitionSeconds = Double.NaN;
        private boolean timerDefinitionActive;
        private int publishedTimerTicks = Integer.MIN_VALUE;
        private long lastTouchedGameTime;
        private boolean pendingDetonation;

        private PhysicalEntry(AbstractCannonProjectile projectile, ItemStack stack) {
            this.projectile = new WeakReference<>(projectile);
            this.stack = stack;
        }

        private void synchronizeTimer(ItemStack stack, FuzeSettings settings) {
            if (!FuzeConfig.timerAvailable() || !settings.timerEnabled()) {
                remainingTimerSeconds = Double.NaN;
                timerDefinitionSeconds = Double.NaN;
                timerDefinitionActive = false;
                publishedTimerTicks = Integer.MIN_VALUE;
                return;
            }
            int componentTicks = stack.getOrDefault(CBCDataComponents.FUZE_TIMER, settings.timerTicks());
            boolean definitionChanged = !timerDefinitionActive
                || Double.compare(timerDefinitionSeconds, settings.timerSeconds()) != 0;
            if (definitionChanged || publishedTimerTicks == Integer.MIN_VALUE
                || componentTicks != publishedTimerTicks) {
                remainingTimerSeconds = Math.max(0, componentTicks) / 20.0;
                publishedTimerTicks = componentTicks;
            }
            timerDefinitionActive = true;
            timerDefinitionSeconds = settings.timerSeconds();
        }

        private void publishTimer(ItemStack stack) {
            int ticks = (int) Math.min(Integer.MAX_VALUE,
                Math.ceil(Math.max(0.0, remainingTimerSeconds) * 20.0));
            if (ticks != publishedTimerTicks) {
                stack.set(CBCDataComponents.FUZE_TIMER, ticks);
                publishedTimerTicks = ticks;
            }
        }
    }

    private static final class WorkBudget {
        private long gameTime = Long.MIN_VALUE;
        private int raysRemaining;
        private int physicalNarrowPhaseRemaining;
    }
}
