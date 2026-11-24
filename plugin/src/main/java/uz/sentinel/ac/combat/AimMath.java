package uz.sentinel.ac.combat;

/** Angle helpers in Minecraft's convention (yaw 0 = +Z, 90 = -X). */
public final class AimMath {
    private AimMath() {}

    /** Yaw a player at (fromX, fromZ) needs to face (toX, toZ). */
    public static double yawTo(double fromX, double fromZ, double toX, double toZ) {
        return Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0;
    }

    /** Smallest absolute difference between two angles in degrees, in [0, 180]. */
    public static double angleBetween(double a, double b) {
        double d = (a - b) % 360.0;
        if (d < 0) d += 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    /**
     * How far {@code delta} is from a whole multiple of {@code divisor}, in [0, 0.5]. Mouse rotations are whole
     * multiples of the sensitivity step; rotations computed by a cheat usually are not.
     */
    public static double stepError(double delta, double divisor) {
        if (!(divisor > 0) || !Double.isFinite(delta)) return 0;
        double steps = delta / divisor;
        return Math.abs(steps - Math.rint(steps));
    }
}
