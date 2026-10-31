package ua.ivan.cbcprogrammablefuzes;

import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ProximityOptimizationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Random random = new Random(138);
        int checkedRays = 0;
        for (int test = 0; test < 2000; test++) {
            double radius = test == 0 ? 0 : test < 5 ? new double[]{0, 2, 4, 16, 1024}[test]
                    : random.nextDouble() * 64;
            ProximityRayGrid grid = ProximityRayGrid.get(radius);
            check(grid == ProximityRayGrid.get(radius), "grid not reused");
            double spacing = Math.max(0.5, radius / 8);
            int size = Math.min(8, Math.max(1, (int) Math.ceil(radius / spacing)));
            int expectedCount = 0;
            for (int x = -size; x <= size; x++) {
                for (int y = -size; y <= size; y++) {
                    double ox = x * spacing, oy = y * spacing;
                    double lateral = ox * ox + oy * oy;
                    if (lateral > radius * radius) continue;
                    expectedCount++;
                }
            }
            check(expectedCount == grid.rays.length, "ray count changed");
            double previousLateral = -1.0;
            for (var ray : grid.rays) {
                double lateral = ray.x() * ray.x() + ray.y() * ray.y();
                check(lateral + 1.0e-12 >= previousLateral, "rays are not centre-first");
                check(ray.reach() == Math.sqrt(Math.max(0, radius * radius - lateral)), "reach changed");
                previousLateral = lateral;
            }
            check(grid.rays[ProgrammableFuzeItem.sampledRayIndex(0, 1, grid.rays.length)] == grid.rays[0],
                    "central safety ray not retained");
            int sampledLast = ProgrammableFuzeItem.sampledRayIndex(
                    Math.min(64, grid.rays.length) - 1,
                    Math.min(64, grid.rays.length), grid.rays.length);
            check(sampledLast == grid.rays.length - 1 || grid.rays.length == 1,
                    "bounded sampling does not cover the outer radius");
            Vec3 start = new Vec3(random.nextDouble() * 60000000 - 30000000,
                    random.nextDouble() * 20000, random.nextDouble() * 60000000 - 30000000);
            Vec3 motion = new Vec3(random.nextDouble() * 1000 - 500,
                    random.nextDouble() * 1000 - 500, random.nextDouble() * 1000 - 500);
            if (test % 7 == 0) motion = new Vec3(0, 500, 0);
            if (test % 11 == 0) motion = Vec3.ZERO;
            Vec3 end = start.add(motion);
            Vec3 forward = motion.lengthSqr() > 1e-8 ? motion.normalize() : new Vec3(0, 0, 1);
            Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 1e-8) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(forward).normalize();
            AABB box = ProximityBroadphase.envelope(start, end, radius);
            for (var ray : grid.rays) {
                Vec3 offset = right.scale(ray.x()).add(up.scale(ray.y()));
                check(box.contains(start.add(offset)), "ray origin outside envelope");
                check(box.contains(end.add(offset).add(forward.scale(ray.reach()))), "ray end outside envelope");
                checkedRays++;
            }
        }
        System.out.println("PASS: 2000 grids unchanged; " + checkedRays + " rays covered by conservative envelopes.");
        TelemetryRegistryTest.run();
    }
}
