package __APP_ID__;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public class WidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Updater.async(c, Updater.SYSTEM, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        Updater.async(c, Updater.SYSTEM, goAsync());
    }

    @Override
    public void onEnabled(Context c) { Updater.schedule(c); }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Store.forget(c, id);
    }

    @Override
    public void onDisabled(Context c) { Updater.checkSchedule(c); }
}
