package local.meli.morphe;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.SharedPreferences;

/** One process-wide paint preference. Pending changes take effect on explicit restart. */
public final class ThemeRuntime {
    private static volatile boolean dark = true;
    private static Context application;
    private ThemeRuntime() {}
    public static boolean isDark() { return dark; }
    public static int nightMode() { return dark ? 2 : 1; }
    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("morphe_meli_settings", Context.MODE_PRIVATE);
    }
    public static boolean requestedDark(Context context) {
        return preferences(context).getBoolean("dark_theme", true);
    }
    public static void initialize(Application app) {
        application = app;
        applyPreference(app);
    }
    public static void applyPreference(Context context) {
        dark = requestedDark(context);
        Context app = context.getApplicationContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        app.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
    }
    public static Context wrap(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return context.createConfigurationContext(config);
    }
}
