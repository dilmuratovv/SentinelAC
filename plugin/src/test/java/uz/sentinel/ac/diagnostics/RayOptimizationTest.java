package uz.sentinel.ac.diagnostics;

import org.junit.jupiter.api.Test;
import uz.sentinel.engine.utils.collisions.datatypes.SimpleCollisionBox;
import uz.sentinel.engine.utils.math.Vector3dm;
import uz.sentinel.engine.utils.nmsutil.Ray;
import uz.sentinel.engine.libs.com.github.retrooper.packetevents.util.Vector3d;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class RayOptimizationTest {
    @Test void scalarMathExactlyMatchesPinnedReferenceForRandomRaysAndBoxes() {
        var random = new Random(0x5AC262);
        for (int i = 0; i < 25_000; i++) {
            double x = random.nextDouble(-10, 10), y = random.nextDouble(-10, 10), z = random.nextDouble(-10, 10);
            var box = new SimpleCollisionBox(x, y, z, x + random.nextDouble(0, 4), y + random.nextDouble(0, 4), z + random.nextDouble(0, 4));
            var ray = new Ray(new Vector3d(random.nextDouble(-12, 12), random.nextDouble(-12, 12), random.nextDouble(-12, 12)),
                    new Vector3d(random.nextDouble(-1, 1), random.nextDouble(-1, 1), random.nextDouble(-1, 1)));
            compare(box, ray);
        }
    }
    @Test void signedZerosParallelRaysInsideOriginsAndBoundariesStayIdentical() {
        var box = new SimpleCollisionBox(0, 0, 0, 1, 1, 1);
        for (double x : new double[]{-1, 0, .5, 1, 2})
            for (double y : new double[]{0, .5, 1})
                for (double dx : new double[]{-1, -0.0, 0.0, 1, Double.POSITIVE_INFINITY})
                    for (double dy : new double[]{-1, -0.0, 0.0, 1})
                        compare(box, new Ray(new Vector3d(x, y, .5), new Vector3d(dx, dy, 0)));
    }
    private static void compare(SimpleCollisionBox box, Ray ray) {
        Vector3dm expected = reference(box, ray, 0, 8), actual = box.intersectsRay(ray, 0, 8);
        if (expected == null) { assertNull(actual); return; }
        assertNotNull(actual);
        assertEquals(Double.doubleToLongBits(expected.getX()), Double.doubleToLongBits(actual.getX()));
        assertEquals(Double.doubleToLongBits(expected.getY()), Double.doubleToLongBits(actual.getY()));
        assertEquals(Double.doubleToLongBits(expected.getZ()), Double.doubleToLongBits(actual.getZ()));
    }
    // Unmodified arithmetic from GrimAC v2.3.74 SimpleCollisionBox.intersectsRay,
    // copyright upstream contributors, GPLv3; retained as a differential oracle.
    private static Vector3dm reference(SimpleCollisionBox b, Ray ray, float minDist, float maxDist) {
        Vector3dm invDir = new Vector3dm(1f / ray.direction().getX(), 1f / ray.direction().getY(), 1f / ray.direction().getZ());
        boolean sx = invDir.getX() < 0, sy = invDir.getY() < 0, sz = invDir.getZ() < 0;
        double tmin = ((sx ? b.maxX : b.minX) - ray.origin().getX()) * invDir.getX();
        double tmax = ((sx ? b.minX : b.maxX) - ray.origin().getX()) * invDir.getX();
        double tymin = ((sy ? b.maxY : b.minY) - ray.origin().getY()) * invDir.getY();
        double tymax = ((sy ? b.minY : b.maxY) - ray.origin().getY()) * invDir.getY();
        if (tmin > tymax || tymin > tmax) return null;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;
        double tzmin = ((sz ? b.maxZ : b.minZ) - ray.origin().getZ()) * invDir.getZ();
        double tzmax = ((sz ? b.minZ : b.maxZ) - ray.origin().getZ()) * invDir.getZ();
        if (tmin > tzmax || tzmin > tmax) return null;
        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;
        return tmin < maxDist && tmax > minDist ? Vector3dm.from(ray.getPointAtDistance(tmin)) : null;
    }
}
