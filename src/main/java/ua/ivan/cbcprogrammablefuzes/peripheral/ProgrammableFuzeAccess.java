package ua.ivan.cbcprogrammablefuzes.peripheral;

import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** A small, side-effect-free bridge between CC:Tweaked and a fuze stored in a shell. */
public interface ProgrammableFuzeAccess {
    /** Returns a defensive copy, or {@link ItemStack#EMPTY} when no compatible fuze is available. */
    ItemStack programmableFuze$getFuze();

    /** Replaces the current programmable fuze with the supplied stack. */
    boolean programmableFuze$setFuze(ItemStack stack);

    default ItemStack programmableFuze$getCorrectionModule() {
        return ItemStack.EMPTY;
    }

    default boolean programmableFuze$setCorrectionModule(ItemStack stack) {
        return false;
    }

    /** Information about the next projectile which would be fired. Empty when this is not a cannon mount. */
    default Map<String, Object> programmableFuze$getLoadedProjectileInfo() {
        return Map.of();
    }

    /** Information about the propellant belonging to the next shot. Empty when this is not a cannon mount. */
    default Map<String, Object> programmableFuze$getLoadedPropellantInfo() {
        return Map.of();
    }

    /** Information about the assembled cannon. Empty when this is not a cannon mount. */
    default Map<String, Object> programmableFuze$getCannonInfo() {
        return Map.of();
    }

    default boolean programmableFuze$hasFuze() {
        return FuzeAccessUtil.isProgrammableFuze(programmableFuze$getFuze());
    }

    default boolean programmableFuze$hasCorrectionModule() {
        return programmableFuze$getCorrectionModule().is(
            ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get()
        );
    }
}
