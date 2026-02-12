package dev.hunchclient.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

// JNA imports for Windows native mouse control
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

/**
 * Human-like Mouse Emulator with two interpolation modes:
 * 1. WindMouse - Physics-based with gravity/wind (Ben Land's algorithm)
 * 2. Spline - Catmull-Rom spline interpolation (smoother, more predictable)
 *
 * Based on Ben Land's WindMouse: https://ben.land/post/2021/04/25/windmouse-human-mouse-movement/
 *
 * Uses ydotool (Linux) or java.awt.Robot (Windows) for actual mouse control.
 * Only active when explicitly moving to targets - does NOT affect normal mouse usage.
 *
 * THREAD SAFETY: Uses generation counter to detect and abort stale movements.
 * When reset() is called, the generation increments and any ongoing windMouse
 * loop will detect this and abort cleanly.
 */
public class HumanMouseEmulator {

    /**
     * Mouse movement interpolation mode
     */
    public enum MouseMode {
        WINDMOUSE,  // Physics-based with gravity/wind randomness
        SPLINE,     // Catmull-Rom spline - smoother, more predictable
        BEZIER      // Bezier curves with random control points - organic wobble
    }

    private static HumanMouseEmulator INSTANCE;
    private boolean initialized = false;
    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    private boolean useYdotool = false;      // Linux: prefer ydotool when available AND working
    private boolean robotAvailable = false;  // Robot also works on Linux as a fallback
    private String ydotoolSocket = null;     // Remember a working socket path
    private java.awt.Robot robot;
    private final Random random = new Random();

    // Windows SendInput constants for mouse clicks
    private static final int INPUT_MOUSE = 0;
    private static final int MOUSEEVENTF_LEFTDOWN = 0x0002;
    private static final int MOUSEEVENTF_LEFTUP = 0x0004;
    private static final int MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private static final int MOUSEEVENTF_RIGHTUP = 0x0010;

    // Screen dimensions for absolute positioning (cached on init)
    private int screenWidth = 65535;
    private int screenHeight = 65535;

    // ydotool coordinate transformation for multi-monitor setups
    private int desktopOffsetX = 1440;
    private int desktopOffsetY = 0;
    private int coordScale = 2;

    // Current mouse position (tracked for smooth movement)
    private volatile double currentX = -1;
    private volatile double currentY = -1;

    // Movement state - use AtomicLong for thread-safe generation tracking
    private volatile boolean isMoving = false;
    private final AtomicLong generation = new AtomicLong(0); // Increments on each reset
    @SuppressWarnings("unused")
    private volatile long activeGeneration = 0; // Generation when current movement started

    // Current interpolation mode
    private volatile MouseMode mouseMode = MouseMode.WINDMOUSE;

    // Spline state (Catmull-Rom)
    private int splineFrames = 0;
    private int splineStep = 0;
    private double splineAx, splineBx, splineCx, splineDx; // X coefficients
    private double splineAy, splineBy, splineCy, splineDy; // Y coefficients
    private double splinePrevX = 0, splinePrevY = 0; // Previous position for P0

    // Timeout for ydotool process calls (ms)
    private static final long YDOTOOL_TIMEOUT_MS = 500;

    // WindMouse parameters
    private double gravity = 9.0;
    private double wind = 3.0;
    private double minWait = 2.0;
    private double maxWait = 10.0;
    private double maxStep = 25.0;
    private double targetArea = 8.0;

    // Spline parameters
    private double splineSpeedDivisor = 4.0;  // dist / divisor = frames (higher = faster)
    private double splineMinWait = 1.0;       // Minimum delay between frames (ms)
    private double splineMaxWait = 8.0;       // Maximum delay between frames (ms)

    // Bezier parameters (based on BezMouse algorithm)
    private int bezierDeviation = 15;         // Control point deviation (% of distance, 10-30 typical)
    private int bezierSpeed = 2;              // Speed multiplier (lower = faster, 1-5 typical)

    // Trail/tracer for visual feedback
    private final List<TrailPoint> trailPoints = new CopyOnWriteArrayList<>();
    private static final int MAX_TRAIL_POINTS = 2000; // Keep many points for full session
    private static final long TRAIL_LIFETIME_MS = 60000; // 60 seconds - don't expire during session
    private boolean trailEnabled = true;

    /**
     * A point in the mouse trail with timestamp for fading
     */
    public static class TrailPoint {
        public final double x;
        public final double y;
        public final long timestamp;

        public TrailPoint(double x, double y) {
            this.x = x;
            this.y = y;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Get opacity based on age (1.0 = new, 0.0 = expired)
         */
        public float getOpacity() {
            long age = System.currentTimeMillis() - timestamp;
            if (age >= TRAIL_LIFETIME_MS) return 0f;
            return 1f - (float) age / TRAIL_LIFETIME_MS;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp >= TRAIL_LIFETIME_MS;
        }
    }

    /**
     * A click point for visualization
     */
    public static class ClickPoint {
        public final double x;
        public final double y;
        public final boolean rightClick;
        public final long timestamp;
        public final long relativeTimeMs;

        public ClickPoint(double x, double y, boolean rightClick, long relativeTimeMs) {
            this.x = x;
            this.y = y;
            this.rightClick = rightClick;
            this.timestamp = System.currentTimeMillis();
            this.relativeTimeMs = relativeTimeMs;
        }
    }

    // Visualization state
    private final List<ClickPoint> clickPoints = new CopyOnWriteArrayList<>();
    private List<TrailPoint> visualizationTrail = new ArrayList<>();
    private List<ClickPoint> visualizationClicks = new ArrayList<>();
    private long visualizationStartTime = 0;
    private static final long VISUALIZATION_DURATION_MS = 5000;
    private long sessionStartTime = 0;

    private HumanMouseEmulator() {}

    public static synchronized HumanMouseEmulator get() {
        if (INSTANCE == null) {
            INSTANCE = new HumanMouseEmulator();
        }
        return INSTANCE;
    }

    public boolean init() {
        if (initialized) return true;

        // Windows: use JNA User32 for native mouse control
        if (isWindows) {
            try {
                // Use JNA to get screen dimensions for absolute mouse positioning
                // GetSystemMetrics(0) = SM_CXSCREEN (width), GetSystemMetrics(1) = SM_CYSCREEN (height)
                screenWidth = User32.INSTANCE.GetSystemMetrics(0);
                screenHeight = User32.INSTANCE.GetSystemMetrics(1);

                if (screenWidth <= 0 || screenHeight <= 0) {
                    screenWidth = 1920;
                    screenHeight = 1080;
                }

                initialized = true;
                System.out.println("[MouseEmulator] Initialized (JNA/User32 SendInput) - Screen: " + screenWidth + "x" + screenHeight);
                return true;
            } catch (Exception e) {
                System.err.println("[MouseEmulator] Failed to init JNA: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        // Linux: prefer ydotool, but fall back to Robot if ydotool is missing or unusable
        checkUinputAccess(); // logs if missing/wrong perms (textspam requested)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", "ydotool"});
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                // Lightweight sanity check that the daemon/socket is reachable
                // Use "mousemove --help" as ydotool 1.0+ doesn't have "version" command
                if (runYdotoolCommand("mousemove", "--help")) {
                    useYdotool = true;
                    initialized = true;
                    System.out.println("[MouseEmulator] Initialized (WindMouse + ydotool)");
                    return true;
                } else {
                    System.err.println("[MouseEmulator] ydotool found but not usable (is ydotoold running?). Falling back to java.awt.Robot");
                }
            } else {
                System.err.println("[MouseEmulator] ydotool not found, falling back to java.awt.Robot");
            }
        } catch (Exception e) {
            System.err.println("[MouseEmulator] Failed to check ydotool: " + e.getMessage());
        }

        // Fallback: try Robot on Linux as well (if not headless)
        boolean robotOk = initRobot();
        if (!robotOk) {
            System.err.println("[MouseEmulator] No usable backend (ydotool failed, Robot unavailable/headless)");
        }
        return robotOk;
    }

    public boolean isAvailable() {
        return initialized;
    }

    /**
     * Extended User32 interface with ClientToScreen method.
     * IMPORTANT: Only loaded on Windows - the INSTANCE field is lazy-initialized
     * to prevent JNI crashes on Linux when user32.dll doesn't exist.
     */
    public interface User32Extended extends com.sun.jna.win32.StdCallLibrary {
        boolean ClientToScreen(WinDef.HWND hWnd, WinDef.POINT lpPoint);
    }

    // Lazy holder for User32Extended instance - only initialized when accessed on Windows
    private static class User32ExtendedHolder {
        static final User32Extended INSTANCE = com.sun.jna.Native.load("user32", User32Extended.class);
    }

    /**
     * Get the User32Extended instance (Windows only).
     * Uses lazy initialization to prevent JNI crashes on Linux.
     */
    private User32Extended getUser32Extended() {
        if (!isWindows) {
            throw new UnsupportedOperationException("User32Extended is only available on Windows");
        }
        return User32ExtendedHolder.INSTANCE;
    }

    /**
     * Convert window client-area coordinates to desktop screen coordinates.
     * On Windows, this accounts for window position, title bar, and borders.
     * Uses Win32 ClientToScreen for accurate conversion.
     *
     * @param windowHandle The GLFW window handle (from mc.getWindow().handle())
     * @param clientX X coordinate relative to the client area
     * @param clientY Y coordinate relative to the client area
     * @return int[2] with {desktopX, desktopY}, or null if conversion failed
     */
    public int[] clientToScreen(long windowHandle, int clientX, int clientY) {
        if (!isWindows) {
            // On Linux, just apply the offset (ydotool has its own coordinate system)
            return new int[] { desktopOffsetX + clientX, desktopOffsetY + clientY };
        }

        try {
            // Get the native Win32 HWND from GLFW window handle
            WinDef.HWND hwnd = getHWNDFromGLFW(windowHandle);
            if (hwnd == null) {
                System.err.println("[MouseEmulator] Failed to get HWND from GLFW handle");
                return new int[] { clientX, clientY };
            }

            // Use ClientToScreen to convert client coords to screen coords
            WinDef.POINT point = new WinDef.POINT(clientX, clientY);
            boolean success = getUser32Extended().ClientToScreen(hwnd, point);

            if (success) {
                return new int[] { point.x, point.y };
            } else {
                System.err.println("[MouseEmulator] ClientToScreen failed");
                return new int[] { clientX, clientY };
            }
        } catch (Exception e) {
            System.err.println("[MouseEmulator] clientToScreen error: " + e.getMessage());
            return new int[] { clientX, clientY };
        }
    }

    /**
     * Get the Win32 HWND from a GLFW window handle using LWJGL.
     * GLFW on Windows uses native HWND internally.
     */
    private WinDef.HWND getHWNDFromGLFW(long glfwWindow) {
        try {
            // Use GLFW's native access to get the Win32 window handle
            long hwndPtr = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
            if (hwndPtr == 0) {
                return null;
            }
            return new WinDef.HWND(new com.sun.jna.Pointer(hwndPtr));
        } catch (Exception e) {
            System.err.println("[MouseEmulator] Failed to get HWND: " + e.getMessage());
            return null;
        }
    }

    public boolean isMoving() {
        return isMoving;
    }

    /**
     * Check if movement should continue (not aborted by reset)
     */
    private boolean shouldContinue(long myGeneration) {
        return isMoving && generation.get() == myGeneration;
    }

    /**
     * WindMouse algorithm - attempt to move mouse position without causing errors
     * Uses generation tracking to abort cleanly when reset() is called
     */
    private void windMouse(double startX, double startY, double destX, double destY,
                           double gravity, double wind, double minWait, double maxWait,
                           double maxStep, double targetArea, long myGeneration) {

        double localX = startX;
        double localY = startY;
        double windX = 0, windY = 0;
        double veloX = 0, veloY = 0;

        final double sqrt3 = Math.sqrt(3.0);
        final double sqrt5 = Math.sqrt(5.0);

        // Final approach threshold - scale with targetArea (which scales with GUI size)
        // Base: 15px at targetArea=8, so finalApproachDist = targetArea * 1.875
        final double finalApproachDist = targetArea * 2.0;

        double dist = hypot(destX - startX, destY - startY);

        // Main WindMouse loop - only while far from target
        while (dist >= finalApproachDist && shouldContinue(myGeneration)) {
            wind = Math.min(wind, dist);

            windX = windX / sqrt3 + (random.nextDouble() * 2 - 1) * wind / sqrt5;
            windY = windY / sqrt3 + (random.nextDouble() * 2 - 1) * wind / sqrt5;

            veloX = veloX + windX + gravity * (destX - localX) / dist;
            veloY = veloY + windY + gravity * (destY - localY) / dist;

            double veloMag = hypot(veloX, veloY);
            if (veloMag > maxStep) {
                double randomDist = maxStep / 2.0 + random.nextDouble() * maxStep / 2.0;
                veloX = (veloX / veloMag) * randomDist;
                veloY = (veloY / veloMag) * randomDist;
            }

            localX += veloX;
            localY += veloY;

            // Jitter only on fast movements
            int jitterX = 0, jitterY = 0;
            if (dist > targetArea * 3 && veloMag > 10) {
                jitterX = random.nextInt(3) - 1;
                jitterY = random.nextInt(3) - 1;
            }

            if (!shouldContinue(myGeneration)) break; // Check before syscall
            moveToRaw((int) Math.round(localX) + jitterX, (int) Math.round(localY) + jitterY);

            // Record trail point (convert desktop coords to window coords)
            addTrailPoint(localX - desktopOffsetX, localY - desktopOffsetY);

            double step = hypot(veloX, veloY);
            long waitTime = (long) Math.round(Math.max(minWait, Math.min(maxWait, maxWait / step * 3)));

            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            dist = hypot(destX - localX, destY - localY);
        }

        // Final approach: simple ease-out movement (no physics, no wobble)
        // Move 40% of remaining distance each step until within target area
        // NO SNAP TO EXACT TARGET - this causes visible jitter!
        while (dist >= targetArea && shouldContinue(myGeneration)) {
            double moveRatio = 0.4;
            localX += (destX - localX) * moveRatio;
            localY += (destY - localY) * moveRatio;

            if (!shouldContinue(myGeneration)) break;
            moveToRaw((int) Math.round(localX), (int) Math.round(localY));

            addTrailPoint(localX - desktopOffsetX, localY - desktopOffsetY);

            try {
                Thread.sleep((long) minWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            dist = hypot(destX - localX, destY - localY);
        }

        // Update tracked position to where we actually ended up (NOT the exact target)
        if (shouldContinue(myGeneration)) {
            this.currentX = localX;
            this.currentY = localY;
        }
    }

    /**
     * Catmull-Rom Spline movement - smoother, more predictable than WindMouse
     * Uses pre-calculated coefficients + Horner's method for efficiency
     */
    private void splineMove(double startX, double startY, double destX, double destY,
                            long myGeneration) {

        // Calculate distance and frame count using configurable speed
        double dist = hypot(destX - startX, destY - startY);
        int frames = Math.max(5, (int) (dist / splineSpeedDivisor)); // Min 5 frames

        // Catmull-Rom control points:
        // P0 = previous position (for smooth entry)
        // P1 = start (current mouse position)
        // P2 = destination (target)
        // P3 = extrapolated beyond destination (for smooth exit)
        double P0x = splinePrevX, P0y = splinePrevY;
        double P1x = startX, P1y = startY;
        double P2x = destX, P2y = destY;
        double dx = P2x - P1x;
        double dy = P2y - P1y;
        double P3x = P2x + dx * 0.5; // Extrapolate half the distance beyond
        double P3y = P2y + dy * 0.5;

        // Pre-calculate polynomial coefficients (Catmull-Rom matrix * 0.5)
        // Formula: ax*t³ + bx*t² + cx*t + dx (cubic polynomial)
        splineAx = 0.5 * (-P0x + 3 * P1x - 3 * P2x + P3x);
        splineBx = 0.5 * (2 * P0x - 5 * P1x + 4 * P2x - P3x);
        splineCx = 0.5 * (-P0x + P2x);
        splineDx = P1x;

        splineAy = 0.5 * (-P0y + 3 * P1y - 3 * P2y + P3y);
        splineBy = 0.5 * (2 * P0y - 5 * P1y + 4 * P2y - P3y);
        splineCy = 0.5 * (-P0y + P2y);
        splineDy = P1y;

        // Track final position
        double lastX = startX, lastY = startY;

        // Interpolate along the spline - NO SNAP TO EXACT TARGET
        for (int step = 1; step <= frames && shouldContinue(myGeneration); step++) {
            double t = (double) step / frames;

            // Horner's method: ((a*t + b)*t + c)*t + d
            double x = ((splineAx * t + splineBx) * t + splineCx) * t + splineDx;
            double y = ((splineAy * t + splineBy) * t + splineCy) * t + splineDy;

            // Small jitter on fast movements (not near target)
            double jitterX = 0, jitterY = 0;
            if (step < frames - 3) {
                jitterX = (random.nextDouble() - 0.5) * 0.8;
                jitterY = (random.nextDouble() - 0.5) * 0.8;
            }

            lastX = x + jitterX;
            lastY = y + jitterY;

            if (!shouldContinue(myGeneration)) break;
            moveToRaw((int) Math.round(lastX), (int) Math.round(lastY));

            addTrailPoint(x - desktopOffsetX, y - desktopOffsetY);

            double velocity = dist / frames;
            long waitTime = (long) Math.max(splineMinWait, Math.min(splineMaxWait, splineMaxWait / (velocity / 5.0)));

            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Update state to where we actually ended (NOT exact target)
        if (shouldContinue(myGeneration)) {
            splinePrevX = startX;
            splinePrevY = startY;
            this.currentX = lastX;
            this.currentY = lastY;
        }
    }

    /**
     * Generate nth row of Pascal's Triangle for Bezier coefficient calculation.
     * Used for the generalized Bezier curve formula.
     */
    private double[] pascalRow(int n) {
        double[] result = new double[n + 1];
        result[0] = 1;
        result[n] = 1;  // Last element is always 1

        double x = 1;
        int numerator = n;
        for (int denominator = 1; denominator <= n / 2; denominator++) {
            x *= numerator;
            x /= denominator;
            result[denominator] = x;
            result[n - denominator] = x; // Mirror for symmetry
            numerator--;
        }

        return result;
    }

    /**
     * Calculate a point on a Bezier curve at parameter t (0 to 1).
     * Uses the generalized Bezier formula with Pascal's triangle coefficients.
     * @param controlPoints Array of control points [x0,y0, x1,y1, ...]
     * @param t Parameter from 0 to 1
     * @return double[2] with {x, y}
     */
    private double[] bezierPoint(double[] controlPoints, double t) {
        int n = controlPoints.length / 2; // Number of control points
        double[] combinations = pascalRow(n - 1);

        double x = 0, y = 0;
        for (int i = 0; i < n; i++) {
            double tPower = Math.pow(t, i);
            double oneMinusTpower = Math.pow(1 - t, n - 1 - i);
            double coef = combinations[i] * tPower * oneMinusTpower;

            x += coef * controlPoints[i * 2];
            y += coef * controlPoints[i * 2 + 1];
        }

        return new double[] { x, y };
    }

    /**
     * Bezier curve movement algorithm (BezMouse).
     * Creates organic, curved paths by placing random control points
     * between start and destination.
     *
     * @param deviation Control point deviation as percentage (10-30 typical)
     *                  Higher = more curved/wobbly path
     * @param speed Speed multiplier (1-5). Lower = faster movement.
     */
    private void bezierMove(double startX, double startY, double destX, double destY,
                            int deviation, int speed, long myGeneration) {

        // Calculate distance and direction
        double dx = destX - startX;
        double dy = destY - startY;
        double dist = hypot(dx, dy);

        // Avoid division by zero for very short distances
        if (dist < 1.0) {
            moveToRaw((int) Math.round(destX), (int) Math.round(destY));
            this.currentX = destX;
            this.currentY = destY;
            return;
        }

        // Calculate steps based on distance (like Spline), NOT fixed count
        // This prevents spawning hundreds of ydotool processes for long distances
        // speed acts as divisor: higher speed = fewer steps = faster movement
        // Min 10 steps, max 80 steps to prevent overwhelming ydotool
        int steps = Math.max(10, Math.min(80, (int) (dist / (speed * 3.0))));

        // Calculate perpendicular unit vector (rotate direction 90 degrees)
        double perpX = -dy / dist;
        double perpY = dx / dist;

        int deviationHalf = Math.max(1, deviation / 2);

        // Position control points at 1/3 and 2/3 along the path
        double mid1X = startX + dx / 3.0;
        double mid1Y = startY + dy / 3.0;
        double mid2X = startX + 2.0 * dx / 3.0;
        double mid2Y = startY + 2.0 * dy / 3.0;

        // Calculate deviation amount as percentage of total distance
        double dev1 = dist * 0.01 * (deviationHalf + random.nextInt(Math.max(1, deviation - deviationHalf + 1)));
        double dev2 = dist * 0.01 * (deviationHalf + random.nextInt(Math.max(1, deviation - deviationHalf + 1)));

        // Control point 1: at 1/3 position with PERPENDICULAR offset
        double sign1 = random.nextBoolean() ? 1 : -1;
        double ctrl1X = mid1X + sign1 * perpX * dev1;
        double ctrl1Y = mid1Y + sign1 * perpY * dev1;

        // Control point 2: at 2/3 position with PERPENDICULAR offset
        double sign2 = random.nextBoolean() ? 1 : -1;
        double ctrl2X = mid2X + sign2 * perpX * dev2;
        double ctrl2Y = mid2Y + sign2 * perpY * dev2;

        // Control points array: start, ctrl1, ctrl2, destination (cubic Bezier)
        double[] controlPoints = new double[] {
            startX, startY,
            ctrl1X, ctrl1Y,
            ctrl2X, ctrl2Y,
            destX, destY
        };

        // Track final position
        double lastX = startX, lastY = startY;

        // Interpolate along the Bezier curve
        for (int step = 1; step <= steps && shouldContinue(myGeneration); step++) {
            double t = (double) step / steps;

            double[] point = bezierPoint(controlPoints, t);
            lastX = point[0];
            lastY = point[1];

            if (!shouldContinue(myGeneration)) break;
            moveToRaw((int) Math.round(lastX), (int) Math.round(lastY));

            addTrailPoint(lastX - desktopOffsetX, lastY - desktopOffsetY);

            // Variable delay based on distance per step (like Spline)
            // Minimum 3ms to prevent overwhelming ydotool with process spawns
            double pixelsPerStep = dist / steps;
            long waitTime = (long) Math.max(3, Math.min(10, 10 / (pixelsPerStep / 5.0)));
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Update tracked position to where we actually ended
        if (shouldContinue(myGeneration)) {
            this.currentX = lastX;
            this.currentY = lastY;
        }
    }

    private double hypot(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }

    /**
     * Add a point to the trail (for visual feedback)
     * Uses Minecraft window coordinates, not desktop coordinates
     */
    private void addTrailPoint(double windowX, double windowY) {
        if (!trailEnabled) return;

        // Clean up expired points
        trailPoints.removeIf(TrailPoint::isExpired);

        // Limit trail length
        while (trailPoints.size() >= MAX_TRAIL_POINTS) {
            trailPoints.remove(0);
        }

        trailPoints.add(new TrailPoint(windowX, windowY));
    }

    private void moveToRaw(int desktopX, int desktopY) {
        if (!initialized) return;

        if (!isMoving) return;

        if (isWindows) {
            // Use JNA SetCursorPos - simple and effective
            User32.INSTANCE.SetCursorPos(desktopX, desktopY);
            return;
        }

        // Linux: Robot fallback when ydotool not used
        if (!useYdotool && robotAvailable) {
            robot.mouseMove(desktopX, desktopY);
            return;
        }

        if (useYdotool) {
            // Double-check abort state before spawning process
            if (!isMoving) return;

            int ydotoolX = (desktopX - desktopOffsetX) / coordScale;
            int ydotoolY = (desktopY - desktopOffsetY) / coordScale;

            // Use "--" to prevent negative coordinates from being interpreted as options
            boolean ok = runYdotoolCommand("mousemove", "-a", "--",
                String.valueOf(ydotoolX), String.valueOf(ydotoolY));

            // If ydotool failed and we're still moving, try recovery
            if (!ok && isMoving) {
                System.err.println("[MouseEmulator] ydotool mousemove failed, attempting recovery...");
                if (reinitYdotool() && isMoving) {
                    // Retry with recovered ydotool
                    ok = runYdotoolCommand("mousemove", "-a", "--",
                        String.valueOf(ydotoolX), String.valueOf(ydotoolY));
                }
                if (!ok && isMoving && ensureRobot()) {
                    useYdotool = false;
                    System.err.println("[MouseEmulator] ydotool recovery failed, falling back to Robot");
                    robot.mouseMove(desktopX, desktopY);
                }
            }
            return;
        }

        // Last resort
        if (ensureRobot()) {
            robot.mouseMove(desktopX, desktopY);
        }
    }

    /**
     * Send a mouse click using Windows SendInput API via JNA
     * Uses realistic human-like timing between down and up events
     * @param downFlag MOUSEEVENTF_LEFTDOWN or MOUSEEVENTF_RIGHTDOWN
     * @param upFlag MOUSEEVENTF_LEFTUP or MOUSEEVENTF_RIGHTUP
     */
    private void sendMouseClick(int downFlag, int upFlag) {
        if (!isWindows) return;

        try {
            // Mouse DOWN
            WinUser.INPUT inputDown = new WinUser.INPUT();
            inputDown.type = new WinDef.DWORD(INPUT_MOUSE);
            inputDown.input.setType("mi");
            inputDown.input.mi.dwFlags = new WinDef.DWORD(downFlag);
            User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[] { inputDown }, inputDown.size());

            // Fast but human-like hold time: 10-35ms (quick gamer clicks)
            int holdTime = 10 + random.nextInt(25);
            Thread.sleep(holdTime);

            // Mouse UP
            WinUser.INPUT inputUp = new WinUser.INPUT();
            inputUp.type = new WinDef.DWORD(INPUT_MOUSE);
            inputUp.input.setType("mi");
            inputUp.input.mi.dwFlags = new WinDef.DWORD(upFlag);
            User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[] { inputUp }, inputUp.size());

        } catch (Exception e) {
            System.err.println("[MouseEmulator] SendInput failed: " + e.getMessage());
        }
    }

    /**
     * Move mouse to target using the selected interpolation mode
     * NOTE: Target position should already include any desired offset/jitter
     */
    public void moveTo(int desktopX, int desktopY) {
        if (!initialized) {
            System.err.println("[MouseEmulator] Not initialized!");
            return;
        }

        // Capture current generation at start of movement
        long myGeneration = generation.get();
        activeGeneration = myGeneration;
        isMoving = true;

        try {
            // Initialize current position if unknown - get ACTUAL mouse position on Windows
            if (currentX < 0 || currentY < 0) {
                if (isWindows) {
                    // Get real mouse position using JNA
                    WinDef.POINT point = new WinDef.POINT();
                    if (User32.INSTANCE.GetCursorPos(point)) {
                        currentX = point.x;
                        currentY = point.y;
                    } else {
                        // Fallback: use screen center
                        currentX = screenWidth / 2.0;
                        currentY = screenHeight / 2.0;
                    }
                } else {
                    // Linux fallback - use offset from target
                    currentX = desktopX - 30 + random.nextInt(60);
                    currentY = desktopY - 30 + random.nextInt(60);
                }
                // Init spline previous position relative to current
                splinePrevX = currentX - 20 + random.nextInt(40);
                splinePrevY = currentY - 20 + random.nextInt(40);
            }

            // Target is exactly what was passed - caller handles any jitter
            double targetX = desktopX;
            double targetY = desktopY;

            // Use selected interpolation mode
            if (mouseMode == MouseMode.SPLINE) {
                splineMove(currentX, currentY, targetX, targetY, myGeneration);
            } else if (mouseMode == MouseMode.BEZIER) {
                bezierMove(currentX, currentY, targetX, targetY, bezierDeviation, bezierSpeed, myGeneration);
            } else {
                windMouse(currentX, currentY, targetX, targetY,
                          gravity, wind, minWait, maxWait, maxStep, targetArea, myGeneration);
            }
        } catch (Exception e) {
            System.err.println("[MouseEmulator] Movement failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // ALWAYS clear isMoving to prevent stuck state after crashes
            // Only if we're still the active generation (reset() handles its own cleanup)
            if (generation.get() == myGeneration) {
                isMoving = false;
            }
        }
    }

    /**
     * Left click at current mouse position
     */
    public void leftClick() {
        if (!initialized) return;

        // Record click for visualization
        if (currentX >= 0 && currentY >= 0) {
            recordClick(currentX - desktopOffsetX, currentY - desktopOffsetY, false);
        }

        if (isWindows) {
            // Use JNA SendInput for mouse clicks
            sendMouseClick(MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP);
            return;
        }

        // Linux: Robot fallback when ydotool not used
        if (!useYdotool && robotAvailable) {
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            return;
        }

        if (useYdotool) {
            boolean ok = runYdotoolCommand("click", "0xC0");
            if (ok) return;

            // ydotool failed -> try recovery once before falling back
            System.err.println("[MouseEmulator] ydotool click failed, attempting recovery...");
            if (reinitYdotool()) {
                ok = runYdotoolCommand("click", "0xC0");
                if (ok) return;
            }

            // Recovery failed -> Robot fallback
            if (ensureRobot()) {
                useYdotool = false;
                System.err.println("[MouseEmulator] ydotool recovery failed, falling back to Robot for clicks");
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            } else {
                System.err.println("[MouseEmulator] Left click failed: ydotool unusable and Robot unavailable");
            }
            return;
        }

        if (ensureRobot()) {
            robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    /**
     * Click at current position
     * @param rightClick true for right-click, false for left-click
     */
    public void click(boolean rightClick) {
        if (rightClick) {
            rightClick();
        } else {
            leftClick();
        }
    }

    /**
     * Right click at current mouse position
     */
    public void rightClick() {
        if (!initialized) return;

        // Record click for visualization
        if (currentX >= 0 && currentY >= 0) {
            recordClick(currentX - desktopOffsetX, currentY - desktopOffsetY, true);
        }

        if (isWindows) {
            // Use JNA SendInput for mouse clicks
            sendMouseClick(MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP);
            return;
        }

        // Linux: Robot fallback when ydotool not used
        if (!useYdotool && robotAvailable) {
            robot.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
            return;
        }

        if (useYdotool) {
            boolean ok = runYdotoolCommand("click", "0xC1");
            if (ok) return;

            // ydotool failed -> try recovery once before falling back
            System.err.println("[MouseEmulator] ydotool right click failed, attempting recovery...");
            if (reinitYdotool()) {
                ok = runYdotoolCommand("click", "0xC1");
                if (ok) return;
            }

            // Recovery failed -> Robot fallback
            if (ensureRobot()) {
                useYdotool = false;
                System.err.println("[MouseEmulator] ydotool recovery failed, falling back to Robot for clicks");
                robot.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
            } else {
                System.err.println("[MouseEmulator] Right click failed: ydotool unusable and Robot unavailable");
            }
            return;
        }

        if (ensureRobot()) {
            robot.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
        }
    }

    /**
     * Move mouse with WindMouse and left click
     */
    public void moveAndClick(int desktopX, int desktopY) {
        moveAndClick(desktopX, desktopY, false);
    }

    /**
     * Move mouse with WindMouse and click (left or right)
     * @param rightClick true for right click, false for left click
     */
    public void moveAndClick(int desktopX, int desktopY, boolean rightClick) {
        moveTo(desktopX, desktopY);

        // Small random delay before clicking (15-40ms human reaction)
        try {
            Thread.sleep(15 + random.nextInt(25));
        } catch (InterruptedException ignored) {}

        if (rightClick) {
            rightClick();
        } else {
            leftClick();
        }
    }

    /**
     * Stop any ongoing movement immediately
     */
    public void stopMovement() {
        isMoving = false;
    }

    /**
     * Try to reinitialize ydotool after a crash/failure.
     * Call this when starting a new session to recover from previous failures.
     * @return true if ydotool is now available
     */
    public boolean reinitYdotool() {
        if (isWindows) return false;
        if (useYdotool) return true; // Already working

        boolean daemonRunning = isYdotooldRunning();

        if (!daemonRunning) {
            startYdotoold();
            // Wait a moment for daemon to initialize
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }

        ydotoolSocket = null;
        if (runYdotoolCommand("mousemove", "--help")) {
            useYdotool = true;
            return true;
        }
        return false;
    }

    /**
     * Check if ydotoold daemon is running
     */
    private boolean isYdotooldRunning() {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("pgrep", "-x", "ydotoold");
            p = pb.start();
            boolean finished = p.waitFor(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (finished) {
                return p.exitValue() == 0;
            }
        } catch (Exception ignored) {
        } finally {
            if (p != null) {
                try {
                    p.getInputStream().close();
                    p.getErrorStream().close();
                    p.getOutputStream().close();
                } catch (Exception ignored) {}
                if (p.isAlive()) p.destroyForcibly();
            }
        }
        return false;
    }

    /**
     * Try to start ydotoold daemon
     */
    private void startYdotoold() {
        // Try systemctl --user first
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("systemctl", "--user", "start", "ydotool");
            p = pb.start();
            p.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        } finally {
            if (p != null) {
                try {
                    p.getInputStream().close();
                    p.getErrorStream().close();
                    p.getOutputStream().close();
                } catch (Exception ignored) {}
                if (p.isAlive()) p.destroyForcibly();
            }
        }
    }

    /**
     * Reset position tracking (call when GUI closes)
     * Increments generation to abort any ongoing windMouse movements
     */
    public void reset() {
        // CRITICAL: Set isMoving = false FIRST to prevent moveToRaw from spawning new processes
        // This must happen before anything else!
        isMoving = false;

        generation.incrementAndGet();

        // Now kill any zombie ydotool processes that were already spawned
        // Safe to do now because isMoving=false prevents new ones
        if (!isWindows) {
            killZombieYdotool();

            // ALWAYS try to recover ydotool after killing zombies
            // This ensures ydotoold is still running and responsive
            if (initialized) {
                reinitYdotool();
            }
        }

        // Start visualization BEFORE clearing (freezes current trail/clicks)
        // Only if a session was explicitly started (sessionStartTime > 0)
        if (sessionStartTime > 0 && (!trailPoints.isEmpty() || !clickPoints.isEmpty())) {
            startVisualization();
        }

        // Clear state (isMoving already set above)
        currentX = -1;
        currentY = -1;
        // Reset spline state
        splinePrevX = 0;
        splinePrevY = 0;
        splineFrames = 0;
        splineStep = 0;

        trailPoints.clear();
        clickPoints.clear();
        sessionStartTime = 0;
    }

    /**
     * Kill any lingering ydotool processes that might cause mouse issues
     * Uses SIGKILL (-9) for immediate termination - cannot be ignored
     * IMPORTANT: Only kills ydotool CLIENT commands, NOT ydotoold daemon!
     */
    private void killZombieYdotool() {
        // Kill ONLY ydotool client processes by matching command patterns
        // DO NOT use "killall ydotool" or "pkill -x ydotool" as they might interfere with ydotoold
        killProcessSafely("pkill", "-9", "-f", "ydotool mousemove");
        killProcessSafely("pkill", "-9", "-f", "ydotool click");

        // Small delay for kernel cleanup
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}

        // Second wave
        killProcessSafely("pkill", "-9", "-f", "ydotool mousemove");
        killProcessSafely("pkill", "-9", "-f", "ydotool click");
    }

    /**
     * Run a process safely with proper cleanup
     */
    private void killProcessSafely(String... command) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            p = pb.start();
            p.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // pkill might not find any processes
        } finally {
            if (p != null) {
                try {
                    p.getInputStream().close();
                    p.getErrorStream().close();
                    p.getOutputStream().close();
                } catch (Exception ignored) {}
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        }
    }

    /**
     * Check if emulator is completely idle (not moving, not initialized for movement)
     */
    public boolean isIdle() {
        return !isMoving;
    }

    /**
     * Get current tracked mouse X position (desktop coordinates)
     * Returns -1 if position not yet initialized
     */
    public double getCurrentX() {
        return currentX;
    }

    /**
     * Get current tracked mouse Y position (desktop coordinates)
     * Returns -1 if position not yet initialized
     */
    public double getCurrentY() {
        return currentY;
    }

    /**
     * Check if mouse position is initialized (has been moved at least once)
     */
    public boolean hasPosition() {
        return currentX >= 0 && currentY >= 0;
    }

    /**
     * Configure WindMouse parameters
     */
    public void setWindMouseParams(double gravity, double wind, double minWait,
                                    double maxWait, double maxStep, double targetArea) {
        this.gravity = gravity;
        this.wind = wind;
        this.minWait = minWait;
        this.maxWait = maxWait;
        this.maxStep = maxStep;
        this.targetArea = targetArea;
    }

    /**
     * Configure Spline parameters
     * @param speedDivisor Higher = faster (dist/divisor = frames). Default 4.0, try 8-15 for faster
     * @param minWait Minimum delay between frames in ms. Default 1.0
     * @param maxWait Maximum delay between frames in ms. Default 8.0
     */
    public void setSplineParams(double speedDivisor, double minWait, double maxWait) {
        this.splineSpeedDivisor = speedDivisor;
        this.splineMinWait = minWait;
        this.splineMaxWait = maxWait;
    }

    /**
     * Configure Bezier parameters (BezMouse algorithm)
     * @param deviation Control point deviation as % of distance (10-30 typical). Default 15.
     *                  Higher = more curved/wobbly path, lower = straighter
     * @param speed Speed divisor (1-5). Higher = faster movement. Default 2.
     *              Steps = dist / (speed * 3), capped at 10-80 to prevent ydotool overload.
     */
    public void setBezierParams(int deviation, int speed) {
        this.bezierDeviation = Math.max(1, Math.min(50, deviation));
        this.bezierSpeed = Math.max(1, Math.min(10, speed));
    }

    /**
     * Set coordinate transformation for ydotool (Linux multi-monitor)
     */
    public void setCoordinateTransform(int offsetX, int offsetY, int scale) {
        this.desktopOffsetX = offsetX;
        this.desktopOffsetY = offsetY;
        this.coordScale = scale;
    }

    /**
     * Set the mouse movement interpolation mode
     */
    public void setMouseMode(MouseMode mode) {
        this.mouseMode = mode;
    }

    /**
     * Get the current mouse movement mode
     */
    public MouseMode getMouseMode() {
        return mouseMode;
    }

    // ==================== Trail/Tracer API ====================

    /**
     * Get trail points for rendering (thread-safe copy)
     * Points are in Minecraft window coordinates
     */
    public List<TrailPoint> getTrailPoints() {
        // Clean expired points first
        trailPoints.removeIf(TrailPoint::isExpired);
        return new ArrayList<>(trailPoints);
    }

    /**
     * Enable or disable trail recording
     */
    public void setTrailEnabled(boolean enabled) {
        this.trailEnabled = enabled;
        if (!enabled) {
            trailPoints.clear();
        }
    }

    /**
     * Check if trail is enabled
     */
    public boolean isTrailEnabled() {
        return trailEnabled;
    }

    /**
     * Clear all trail points
     */
    public void clearTrail() {
        trailPoints.clear();
    }

    /**
     * Get desktop offset X (for coordinate conversion)
     */
    public int getDesktopOffsetX() {
        return desktopOffsetX;
    }

    /**
     * Get desktop offset Y (for coordinate conversion)
     */
    public int getDesktopOffsetY() {
        return desktopOffsetY;
    }

    /**
     * EMERGENCY STOP - kills ydotool CLIENT processes immediately (NOT the daemon!)
     * Uses pattern matching to only kill mousemove/click commands, preserving ydotoold.
     */
    public void emergencyStop() {
        generation.incrementAndGet();
        isMoving = false;

        if (!isWindows && useYdotool) {
            killProcessSafely("pkill", "-9", "-f", "ydotool mousemove");
            killProcessSafely("pkill", "-9", "-f", "ydotool click");
        }

        currentX = -1;
        currentY = -1;
        splinePrevX = 0;
        splinePrevY = 0;
    }

    /**
     * Check if any ydotool processes are running
     */
    public boolean hasActiveProcesses() {
        if (isWindows || !useYdotool) return isMoving;
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("pgrep", "-c", "ydotool");
            p = pb.start();
            boolean finished = p.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (finished) {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                String line = r.readLine();
                r.close();
                if (line != null) return Integer.parseInt(line.trim()) > 0;
            }
        } catch (Exception ignored) {
        } finally {
            if (p != null) {
                try {
                    p.getInputStream().close();
                    p.getErrorStream().close();
                    p.getOutputStream().close();
                } catch (Exception ignored) {}
                if (p.isAlive()) p.destroyForcibly();
            }
        }
        return isMoving;
    }

    // ==================== Visualization API ====================

    public void setSessionStartTime(long timeMs) {
        this.sessionStartTime = timeMs;
        clickPoints.clear();
    }

    public void recordClick(double windowX, double windowY, boolean rightClick) {
        long relativeTime = sessionStartTime > 0 ? System.currentTimeMillis() - sessionStartTime : 0;
        clickPoints.add(new ClickPoint(windowX, windowY, rightClick, relativeTime));
    }

    public boolean isVisualizationActive() {
        if (visualizationStartTime == 0) return false;
        return System.currentTimeMillis() - visualizationStartTime < VISUALIZATION_DURATION_MS;
    }

    public float getVisualizationProgress() {
        if (visualizationStartTime == 0) return 0f;
        long elapsed = System.currentTimeMillis() - visualizationStartTime;
        if (elapsed >= VISUALIZATION_DURATION_MS) return 0f;
        return 1f - (float) elapsed / VISUALIZATION_DURATION_MS;
    }

    public List<TrailPoint> getVisualizationTrail() {
        return visualizationTrail;
    }

    public List<ClickPoint> getVisualizationClicks() {
        return visualizationClicks;
    }

    private void startVisualization() {
        visualizationTrail = new ArrayList<>(trailPoints);
        visualizationClicks = new ArrayList<>(clickPoints);
        visualizationStartTime = System.currentTimeMillis();
    }

    public void shutdown() {
        isMoving = false;
        initialized = false;
        robot = null;
        robotAvailable = false;
        useYdotool = false;
        ydotoolSocket = null;
        currentX = -1;
        currentY = -1;
    }

    // ==================== Helpers ====================

    /**
     * Try to initialize java.awt.Robot and mark availability.
     */
    private boolean initRobot() {
        try {
            // Some launchers set headless=true; try to disable to allow Robot
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                System.setProperty("java.awt.headless", "false");
            }
            robot = new java.awt.Robot();
            robotAvailable = true;
            initialized = true;
            System.out.println("[MouseEmulator] Initialized (WindMouse + java.awt.Robot)");
            return true;
        } catch (Exception e) {
            System.err.println("[MouseEmulator] Failed to init Robot: " + e.getMessage());
            robotAvailable = false;
            return false;
        }
    }

    /**
     * Ensure Robot exists (lazy init), used for Linux fallback.
     */
    private boolean ensureRobot() {
        if (robotAvailable && robot != null) return true;
        if (isWindows) return false; // Windows path initialized once
        return initRobot();
    }

    /**
     * Run a ydotool command and report success, with timeout and error logging.
     * Tries multiple common socket locations; remembers a working one.
     * Uses YDOTOOL_SOCKET environment variable (ydotool 1.0+ doesn't support -C flag).
     * IMPORTANT: Always destroys processes to prevent leaks!
     */
    private boolean runYdotoolCommand(String... args) {
        // CRITICAL: Abort actual mousemove commands if movement was cancelled
        // This prevents spawning new processes after reset()
        // But ALLOW "mousemove --help" for init() health check!
        boolean isActualMouseMove = args.length >= 3 && "mousemove".equals(args[0]) && "-a".equals(args[1]);
        if (isActualMouseMove && !isMoving) {
            return false;
        }

        List<String> sockets = getSocketCandidates();
        if (ydotoolSocket != null && !ydotoolSocket.isEmpty()) {
            sockets.add(0, ydotoolSocket);
        }

        String lastError = null;
        int lastExit = -1;

        for (String socket : sockets) {
            // Check again before each socket attempt (only for actual mousemove)
            if (isActualMouseMove && !isMoving) {
                return false;
            }

            Process p = null;
            try {
                List<String> full = new ArrayList<>();
                full.add("ydotool");
                java.util.Collections.addAll(full, args);

                ProcessBuilder pb = new ProcessBuilder(full);
                // Use YDOTOOL_SOCKET env var instead of -C flag (works with ydotool 1.0+)
                if (socket != null && !socket.isEmpty()) {
                    pb.environment().put("YDOTOOL_SOCKET", socket);
                }
                p = pb.start();
                long startTime = System.currentTimeMillis();

                boolean finished = false;
                long deadline = startTime + YDOTOOL_TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline) {
                    if (isActualMouseMove && !isMoving) {
                        p.destroyForcibly();
                        return false;
                    }
                    if (p.waitFor(10, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        finished = true;
                        break;
                    }
                }
                if (!finished) {
                    lastError = "timeout";
                    continue; // finally block will destroy
                }
                int exit = p.exitValue();
                lastExit = exit;
                if (exit != 0) {
                    java.io.BufferedReader err = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream()));
                    String errLine = err.readLine();
                    err.close();
                    lastError = (errLine != null ? errLine : "(no stderr)");
                    continue; // finally block will destroy
                }

                // Success: remember working socket (can be null for default)
                if (socket != null && !socket.isEmpty()) {
                    ydotoolSocket = socket;
                }
                return true;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                lastError = e.getMessage();
            } finally {
                // ALWAYS destroy the process to prevent zombies
                if (p != null) {
                    try {
                        // Close streams first
                        p.getInputStream().close();
                        p.getErrorStream().close();
                        p.getOutputStream().close();
                    } catch (Exception ignored) {}

                    if (p.isAlive()) {
                        p.destroyForcibly();
                    }
                }
            }
        }

        // All attempts failed – emit a single concise error with hints
        String socketList = String.join(",", sockets);
        String hint = "Start ydotoold (e.g. sudo ydotoold --socket-path /run/ydotoold.socket) and ensure your user can access /dev/uinput";
        System.err.println("[MouseEmulator] ydotool failed (exit " + lastExit + "): " + (lastError != null ? lastError : "(no stderr)") +
            " | sockets tried: " + socketList + " | " + hint);
        return false;
    }

    private List<String> getSocketCandidates() {
        List<String> sockets = new ArrayList<>();
        String env = System.getenv("YDOTOOLD_SOCKET");
        if (env != null && !env.isBlank()) sockets.add(env);
        sockets.add("/run/ydotoold.socket"); // systemd service default on many distros
        sockets.add("/tmp/.ydotool_socket"); // upstream default
        sockets.add("/dev/shm/ydotool_socket"); // some setups
        sockets.add("/run/user/" + getUid() + "/.ydotool_socket");
        return sockets;
    }

    private String getUid() {
        String envUid = System.getenv("UID");
        if (envUid != null && !envUid.isBlank()) return envUid;
        // Fallback: best-effort
        return "1000";
    }

    /**
     * Simple uinput access probe with noisy log (requested "textspam").
     */
    private boolean checkUinputAccess() {
        if (isWindows) return true;
        File f = new File("/dev/uinput");
        if (!f.exists()) {
            System.err.println("[MouseEmulator] /dev/uinput missing. Load module: sudo modprobe uinput");
            return false;
        }
        if (!f.canWrite()) {
            String user = System.getProperty("user.name", "unknown");
            System.err.println("[MouseEmulator] /dev/uinput not writable for user '" + user + "'. Add udev rule: KERNEL==\"uinput\", GROUP=\"uinput\", MODE=\"0660\" and add user to group uinput, then relogin.");
            return false;
        }
        return true;
    }
}
