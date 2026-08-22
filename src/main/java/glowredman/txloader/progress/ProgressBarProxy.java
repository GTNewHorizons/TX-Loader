package glowredman.txloader.progress;

import java.lang.reflect.Field;

public class ProgressBarProxy {

    public static boolean isBLSLoaded;
    public static boolean loadComplete;

    private static Class<?> ProgressDisplayer;
    private static Field displayer;

    public static ProgressBar get(String name, int maxSteps) {
        if (loadComplete) {
            // redirect step() and pop() to NO-OP methods once the game finished loading
            return NoneProgressBar.INSTANCE;
        }
        if (isBLSLoaded && isDisplayerAvailable()) {
            new BLSProgressBar(name, maxSteps);
        }
        return new FMLProgressBar(name, maxSteps);
    }

    private static boolean isDisplayerAvailable() {
        if (ProgressDisplayer == null) {
            return false;
        }

        if (displayer == null) {
            try {
                displayer = ProgressDisplayer.getDeclaredField("displayer");
            } catch (Exception ignored) {
                // This shouldn't be reached...
                // In case it is, cause early exits in the future
                isBLSLoaded = false;
                ProgressDisplayer = null;
                return false;
            }
        }

        try {
            return displayer.get(null) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    static {
        try {
            ProgressDisplayer = Class.forName("alexiil.mods.load.ProgressDisplayer");
        } catch (Exception ignored) {}
    }

    private ProgressBarProxy() {}
}
