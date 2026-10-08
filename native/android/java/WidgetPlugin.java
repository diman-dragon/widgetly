package __APP_ID__;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;

@CapacitorPlugin(name = "WidgetBridge")
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

    /** Перерисовать виджеты на рабочем столе (погода — только если кэш старше интервала). */
    @PluginMethod
    public void refresh(PluginCall call) {
        Updater.updateAll(ctx(), Updater.SYSTEM);
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
            try (FileOutputStream fo = new FileOutputStream(Store.bgFile(ctx()))) { fo.write(bytes); }
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

    /** Системный диалог «добавить виджет на рабочий стол» (Android 8+). */
    @PluginMethod
    public void pinWidget(PluginCall call) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx());
            boolean ok = android.os.Build.VERSION.SDK_INT >= 26 && m.isRequestPinAppWidgetSupported()
                    && m.requestPinAppWidget(new ComponentName(ctx(), WidgetProvider.class), null, null);
            JSObject o = new JSObject();
            o.put("supported", ok);
            call.resolve(o);
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
