package ua.ivan.cbcprogrammablefuzes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable offsets; same ordering, spacing and reach as the original grid. */
final class ProximityRayGrid {
    record Ray(double x, double y, double reach) {}
    private static final Map<Double, ProximityRayGrid> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    final Ray[] rays;

    private ProximityRayGrid(double radius) {
        double spacing = Math.max(0.5, radius / 8);
        int gridRadius = Math.min(8, Math.max(1, (int) Math.ceil(radius / spacing)));
        ArrayList<Ray> values = new ArrayList<>();
        for (int x = -gridRadius; x <= gridRadius; x++) {
            for (int y = -gridRadius; y <= gridRadius; y++) {
                double ox = x * spacing, oy = y * spacing;
                double lateral = ox * ox + oy * oy;
                if (lateral <= radius * radius) {
                    values.add(new Ray(ox, oy, Math.sqrt(Math.max(0, radius * radius - lateral))));
                }
            }
        }
        // The central ray is first, then progressively wider rings. A bounded
        // caller can sample the complete radius instead of only one edge of
        // the old row-major grid.
        values.sort(Comparator.comparingDouble(ray -> ray.x() * ray.x() + ray.y() * ray.y()));
        rays = values.toArray(Ray[]::new);
    }

    static synchronized ProximityRayGrid get(double radius) {
        ProximityRayGrid grid = CACHE.get(radius);
        if (grid == null) {
            grid = new ProximityRayGrid(radius);
            if (CACHE.size() >= 32) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(radius, grid);
        }
        return grid;
    }
}
