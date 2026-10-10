package __APP_ID__;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

@CapacitorPlugin(name = "WidgetBridge", permissions = {
        @Permission(alias = "calendar", strings = {Manifest.permission.READ_CALENDAR})
})
public class WidgetPlugin extends Plugin {

    private Context ctx() { return getContext().getApplicationContext(); }

    @PluginMethod
    public void loadAll(PluginCall call) {
        JSObject o = new JSObject();
        o.put("cfg", Store.cfgJson(ctx()));
        o.put("settings", Store.settingsJson(ctx()));
        call.resolve(o);
    }

    @PluginMethod
    public void saveCfg(PluginCall call) {
        Store.saveCfg(ctx(), call.getString("json", "{}"));
        call.resolve();
    }

    @PluginMethod
    public void saveSettings(PluginCall call) {
        Store.saveSettings(ctx(), call.getString("json", "{}"));
        call.resolve();
    }

    /** Предпросмотр: тот же Renderer, что рисует виджет на рабочем столе. */
    @PluginMethod
    public void render(PluginCall call) {
        try {
            JSONObject cfg = new JSONObject(call.getString("cfg", "{}"));
            int w = call.getInt("width", 640), h = call.getInt("height", 360);
            float dens = call.getFloat("density", 2f);
            Bitmap b = Renderer.render(ctx(), cfg, w, h, dens, System.currentTimeMillis(), true, true);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, bo);
            b.recycle();
            JSObject o = new JSObject();
            o.put("png", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            call.resolve(o);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /** Перерисовать уже стоящие на рабочем столе виджеты (погода — только если кэш старше интервала). */
    @PluginMethod
    public void refresh(PluginCall call) {
        boolean force = Boolean.TRUE.equals(call.getBoolean("force", false));
        Updater.updateAll(ctx(), force ? Updater.FORCE : Updater.SYSTEM);
        call.resolve();
    }

    @PluginMethod
    public void checkWeather(PluginCall call) {
        try {
            JSONObject w = new JSONObject(call.getString("weather", "{}"));
            JSObject o = new JSObject();
            o.put("message", w.has("lat") ? WeatherService.check(ctx(), w) : "—");
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void saveBackground(PluginCall call) {
        try {
            byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            FileOutputStream fo = new FileOutputStream(Store.bgFile(ctx()));
            try { fo.write(bytes); } finally { fo.close(); }
            Store.putLong(ctx(), "bgRev", System.currentTimeMillis());
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void removeBackground(PluginCall call) {
        Store.bgFile(ctx()).delete();
        Store.putLong(ctx(), "bgRev", System.currentTimeMillis());
        call.resolve();
    }

    /** Сколько виджетов уже стоит на рабочем столе. */
    @PluginMethod
    public void widgetCount(PluginCall call) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx());
        JSObject o = new JSObject();
        o.put("count", m.getAppWidgetIds(new ComponentName(ctx(), WidgetProvider.class)).length);
        call.resolve(o);
    }

    /**
     * Добавить на рабочий стол. Если виджет уже стоит — новый НЕ создаём, а обновляем существующий.
     */
    @PluginMethod
    public void pinWidget(PluginCall call) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx());
            JSObject o = new JSObject();
            if (m.getAppWidgetIds(new ComponentName(ctx(), WidgetProvider.class)).length > 0) {
                Updater.updateAll(ctx(), Updater.FORCE);
                o.put("exists", true);
                o.put("supported", true);
                call.resolve(o);
                return;
            }
            boolean ok = android.os.Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported()
                    && m.requestPinAppWidget(new ComponentName(ctx(), WidgetProvider.class), null, null);
            o.put("exists", false);
            o.put("supported", ok);
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    // ---------- календарь: разрешение READ_CALENDAR запрашивается только по кнопке в настройках ----------
    @PluginMethod
    public void calendarState(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", CalendarService.granted(ctx()));
        call.resolve(o);
    }

    @PluginMethod
    public void calendarRequest(PluginCall call) {
        if (CalendarService.granted(ctx())) { calendarCb(call); return; }
        requestPermissionForAlias("calendar", call, "calendarCb");
    }

    @PermissionCallback
    private void calendarCb(PluginCall call) {
        boolean g = CalendarService.granted(ctx());
        if (g) Updater.async(ctx(), Updater.FORCE, null);
        JSObject o = new JSObject();
        o.put("granted", g);
        call.resolve(o);
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        try {
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void searchCity(PluginCall call) {
        try {
            JSONArray r = WeatherService.search(call.getString("query", ""), call.getString("lang", "en"));
            JSObject o = new JSObject();
            o.put("results", r.toString());
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @SuppressWarnings("deprecation")
    @PluginMethod
    public void getInfo(PluginCall call) {
        try {
            PackageInfo pi = ctx().getPackageManager().getPackageInfo(ctx().getPackageName(), 0);
            long vc = android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            JSObject o = new JSObject();
            o.put("version", pi.versionName);
            o.put("build", String.valueOf(vc));
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }
}
