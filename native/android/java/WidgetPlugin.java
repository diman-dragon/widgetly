package __APP_ID__;

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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;

@CapacitorPlugin(name = "WidgetBridge")
public class WidgetPlugin extends Plugin {

    private Context ctx() { return getContext().getApplicationContext(); }

    @PluginMethod
    public void loadAll(PluginCall call) {
        JSObject o = new JSObject();
        o.put("designs", Store.designsJson(ctx()));
        o.put("settings", Store.settingsJson(ctx()));
        call.resolve(o);
    }

    @PluginMethod
    public void saveDesigns(PluginCall call) {
        Store.saveDesigns(ctx(), call.getString("json", "[]"));
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
            JSONObject d = new JSONObject(call.getString("design", "{}"));
            int w = call.getInt("width", 720), h = call.getInt("height", 504);
            float dens = call.getFloat("density", 2f);
            Bitmap b = Renderer.render(ctx(), d, w, h, dens, System.currentTimeMillis(), true, true);
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

    @PluginMethod
    public void refresh(PluginCall call) {
        boolean force = call.getBoolean("force", false);
        Updater.updateAll(ctx(), force ? Updater.FORCE : Updater.SYSTEM);
        call.resolve();
    }

    @PluginMethod
    public void getWidgets(PluginCall call) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx());
            String[] names = {"5x3", "4x2", "2x2"};
            JSONArray a = new JSONArray();
            for (int i = 0; i < Updater.PROVIDERS.length; i++) {
                for (int id : m.getAppWidgetIds(new ComponentName(ctx(), Updater.PROVIDERS[i]))) {
                    JSONObject w = new JSONObject();
                    w.put("id", id);
                    w.put("size", names[i]);
                    String did = Store.designIdFor(ctx(), id);
                    w.put("design", did == null ? "" : did);
                    a.put(w);
                }
            }
            JSObject o = new JSObject();
            o.put("widgets", a.toString());
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void assignWidget(PluginCall call) {
        Integer id = call.getInt("widgetId");
        String did = call.getString("designId", "");
        if (id != null) {
            Store.mapWidget(ctx(), id, did);
            Updater.updateAll(ctx(), Updater.SYSTEM);
        }
        call.resolve();
    }

    /** Системный диалог "добавить виджет на рабочий стол" (Android 8+). */
    @PluginMethod
    public void pinWidget(PluginCall call) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx());
            if (android.os.Build.VERSION.SDK_INT < 26 || !m.isRequestPinAppWidgetSupported()) {
                JSObject o = new JSObject();
                o.put("supported", false);
                call.resolve(o);
                return;
            }
            Class<?> cls = Widget5x3.class;
            String size = call.getString("size", "5x3");
            if ("4x2".equals(size)) cls = Widget4x2.class;
            else if ("2x2".equals(size)) cls = Widget2x2.class;
            Store.putString(ctx(), "pending", call.getString("designId", ""));
            Store.putLong(ctx(), "pendingTs", System.currentTimeMillis());
            boolean ok = m.requestPinAppWidget(new ComponentName(ctx(), cls), null, null);
            JSObject o = new JSObject();
            o.put("supported", ok);
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void notifAccess(PluginCall call) {
        JSObject o = new JSObject();
        o.put("granted", NotifListener.granted(ctx()));
        call.resolve(o);
    }

    @PluginMethod
    public void openNotifAccess(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
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
            JSObject o = new JSObject();
            o.put("version", pi.versionName);
            long vc = android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            o.put("build", String.valueOf(vc));
            o.put("appId", ctx().getPackageName());
            call.resolve(o);
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    /** Холодный старт по клику на виджете. */
    @PluginMethod
    public void getLaunchDesign(PluginCall call) {
        JSObject o = new JSObject();
        String id = getActivity().getIntent().getStringExtra("edit");
        o.put("id", id == null ? "" : id);
        getActivity().getIntent().removeExtra("edit");
        call.resolve(o);
    }

    /** Клик по виджету, когда приложение уже запущено (singleTask). */
    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        String id = intent.getStringExtra("edit");
        if (id != null && !id.isEmpty()) {
            JSObject o = new JSObject();
            o.put("id", id);
            notifyListeners("openDesign", o);
            intent.removeExtra("edit");
        }
    }
}
