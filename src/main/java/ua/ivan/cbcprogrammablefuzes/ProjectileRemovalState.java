package ua.ivan.cbcprogrammablefuzes;

/** Read-only CBC removal state used to distinguish an impact from lifetime expiry. */
public interface ProjectileRemovalState {
    boolean cbcProgrammableFuzes$removeAfterImpact();
    void cbcProgrammableFuzes$scheduleRemoval();
}
