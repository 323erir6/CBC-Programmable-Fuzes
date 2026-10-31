package ua.ivan.cbcprogrammablefuzes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Conservative, non-loading gate for the expensive proximity ray grid.
 * A candidate is NOT a hit: only the original exact rays may trigger the fuze. */
public final class ProximityBroadphase {
    private ProximityBroadphase() {}

    public static boolean needsBlockRays(Level world, Vec3 start, Vec3 end, double radius) {
        if (!(world instanceof ServerLevel level)) return true;
        Vec3 delta = end.subtract(start);
        double longest = Math.max(Math.abs(delta.x), Math.max(Math.abs(delta.y), Math.abs(delta.z)));
        // Pathological/huge radii use the original grid; do not replace 197
        // rays with millions of coarse section queries.
        if (!Double.isFinite(longest) || !Double.isFinite(radius)
                || radius < 0 || radius > 16 || longest > 16384) return true;
        int steps = Math.max(1, (int) Math.ceil(longest / 16));
        for (int i = 0; i < steps; i++) {
            Vec3 a = start.add(delta.scale((double) i / steps));
            Vec3 b = start.add(delta.scale((double) (i + 1) / steps));
            AABB box = envelope(a, b, radius);
            if (terrainCandidate(level, box) || SableBridge.candidate(level, box)) return true;
        }
        return false;
    }

    static AABB envelope(Vec3 a, Vec3 b, double radius) {
        // Covers lateral rays AND their forward reach. Extra block margin
        // covers voxel shapes which extend beyond their owning block.
        return new AABB(a, b).inflate(radius + 1.0);
    }

    private static boolean terrainCandidate(ServerLevel level, AABB box) {
        int minY = Math.max(level.getMinSection(), cell(box.minY));
        int maxY = Math.min(level.getMaxSection() - 1, cell(box.maxY));
        if (minY > maxY) return false;
        for (int x = cell(box.minX); x <= cell(box.maxX); x++) {
            for (int z = cell(box.minZ); z <= cell(box.maxZ); z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) return true; // Unknown is never considered air.
                for (int y = minY; y <= maxY; y++) {
                    if (!chunk.getSection(level.getSectionIndex(y << 4)).hasOnlyAir()) return true;
                }
            }
        }
        return false;
    }

    private static int cell(double coordinate) { return ((int) Math.floor(coordinate)) >> 4; }

    private static final class SableBridge {
        private static final Object HELPER;
        private static final Constructor<?> BOX;
        private static final Method INTERSECT;
        static {
            Object helper = null;
            Constructor<?> box = null;
            Method intersect = null;
            try {
                ClassLoader loader = ProximityBroadphase.class.getClassLoader();
                Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", false, loader);
                Class<?> bounds = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d", false, loader);
                helper = sable.getField("HELPER").get(null);
                box = bounds.getConstructor(double.class, double.class, double.class,
                        double.class, double.class, double.class);
                for (Method method : helper.getClass().getMethods()) {
                    if (method.getName().equals("getAllIntersecting") && method.getParameterCount() == 2) {
                        intersect = method;
                        break;
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                helper = null;
            }
            HELPER = helper;
            BOX = box;
            INTERSECT = intersect;
        }

        private static boolean candidate(ServerLevel level, AABB box) {
            if (HELPER == null || BOX == null || INTERSECT == null) {
                return ModList.get().isLoaded("sable"); // Incompatible API: keep exact rays.
            }
            try {
                Object result = INTERSECT.invoke(HELPER, level, BOX.newInstance(
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
                return !(result instanceof Iterable<?> iterable) || iterable.iterator().hasNext();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return true;
            }
        }
    }
}
