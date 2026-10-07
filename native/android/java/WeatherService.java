package __APP_ID__;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/** Погода без регистрации и ключей: Open-Meteo (по умолчанию) и MET Norway. Кэш в SharedPreferences. */
final class WeatherService {
    private WeatherService() {}

    static final String UA = "WidgetStudio/1.0 (Android home screen widget)";

    static final class Data {
        double temp, hi, lo;
        int code;
        boolean day = true;
        long ts;
    }

    static boolean ru() { return "ru".equals(Locale.getDefault().getLanguage()); }

    static String key(JSONObject o) {
        return o.optString("service", "openmeteo") + "|" + String.format(Locale.US, "%.3f|%.3f", o.optDouble("lat"), o.optDouble("lon"))
                + "|" + o.optString("units", "c");
    }

    static Data cached(Context c, String key) {
        try {
            String s = Store.getString(c, "wx_" + key, null);
            if (s == null) return null;
            JSONObject j = new JSONObject(s);
            Data d = new Data();
            d.ts = j.getLong("ts"); d.temp = j.getDouble("temp"); d.hi = j.getDouble("hi"); d.lo = j.getDouble("lo");
            d.code = j.getInt("code"); d.day = j.optBoolean("day", true);
            return d;
        } catch (Exception e) { return null; }
    }

    static Data get(Context c, JSONObject o, boolean allowNet) {
        Data d = cached(c, key(o));
        if (d == null && allowNet) return ensure(c, o, false, Long.MAX_VALUE);
        return d;
    }

    /** Возвращает свежие данные, если кэш старше maxAge (или force). При ошибке — старый кэш, повтор не чаще раза в 10 мин. */
    static Data ensure(Context c, JSONObject o, boolean force, long maxAge) {
        String key = key(o);
        Data cd = cached(c, key);
        long now = System.currentTimeMillis();
        if (cd != null && !force && now - cd.ts < maxAge) return cd;
        if (!force && now - Store.getLong(c, "wxf_" + key, 0) < 10 * 60000L) return cd;
        try {
            Data nd = "metno".equals(o.optString("service")) ? metno(o) : openMeteo(o);
            nd.ts = now;
            JSONObject j = new JSONObject();
            j.put("ts", nd.ts); j.put("temp", nd.temp); j.put("hi", nd.hi); j.put("lo", nd.lo);
            j.put("code", nd.code); j.put("day", nd.day);
            Store.putString(c, "wx_" + key, j.toString());
            return nd;
        } catch (Exception e) {
            Store.putLong(c, "wxf_" + key, now);
            return cd;
        }
    }

    static String http(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(5000);
        h.setReadTimeout(5000);
        h.setRequestProperty("User-Agent", UA);
        h.setRequestProperty("Accept", "application/json");
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { h.disconnect(); }
    }

    static Data openMeteo(JSONObject o) throws Exception {
        boolean f = "f".equals(o.optString("units", "c"));
        String u = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto&temperature_unit=%s",
                o.getDouble("lat"), o.getDouble("lon"), f ? "fahrenheit" : "celsius");
        JSONObject j = new JSONObject(http(u));
        JSONObject cur = j.getJSONObject("current");
        JSONObject day = j.getJSONObject("daily");
        Data d = new Data();
        d.temp = cur.getDouble("temperature_2m");
        d.code = cur.getInt("weather_code");
        d.day = cur.optInt("is_day", 1) == 1;
        d.hi = day.getJSONArray("temperature_2m_max").getDouble(0);
        d.lo = day.getJSONArray("temperature_2m_min").getDouble(0);
        return d;
    }

    static Data metno(JSONObject o) throws Exception {
        boolean f = "f".equals(o.optString("units", "c"));
        String u = String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
                o.getDouble("lat"), o.getDouble("lon"));
        JSONObject j = new JSONObject(http(u));
        JSONArray ts = j.getJSONObject("properties").getJSONArray("timeseries");
        JSONObject first = ts.getJSONObject(0).getJSONObject("data");
        double t = first.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
        String sym = "cloudy";
        for (String k : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
            JSONObject n = first.optJSONObject(k);
            if (n != null && n.optJSONObject("summary") != null) { sym = n.getJSONObject("summary").optString("symbol_code", sym); break; }
        }
        double hi = t, lo = t;
        for (int i = 0; i < Math.min(24, ts.length()); i++) {
            double v = ts.getJSONObject(i).getJSONObject("data").getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            hi = Math.max(hi, v); lo = Math.min(lo, v);
        }
        Data d = new Data();
        d.day = !sym.endsWith("_night");
        d.code = metToWmo(sym);
        d.temp = f ? t * 9 / 5 + 32 : t;
        d.hi = f ? hi * 9 / 5 + 32 : hi;
        d.lo = f ? lo * 9 / 5 + 32 : lo;
        return d;
    }

    static int metToWmo(String s) {
        s = s.replaceAll("_(day|night|polartwilight)$", "");
        boolean heavy = s.contains("heavy"), light = s.contains("light");
        if (s.contains("thunder")) return 95;
        if (s.contains("snow") || s.contains("sleet")) return heavy ? 75 : light ? 71 : 73;
        if (s.contains("showers")) return 80;
        if (s.contains("rain")) return heavy ? 65 : light ? 61 : 63;
        switch (s) {
            case "clearsky": return 0;
            case "fair": return 1;
            case "partlycloudy": return 2;
            case "fog": return 45;
            default: return 3;
        }
    }

    /** Поиск города (геокодер Open-Meteo, без ключа). */
    static JSONArray search(String q, String lang) throws Exception {
        String u = "https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&language=" + lang + "&name=" + URLEncoder.encode(q, "UTF-8");
        JSONObject j = new JSONObject(http(u));
        JSONArray res = j.optJSONArray("results"), out = new JSONArray();
        if (res == null) return out;
        for (int i = 0; i < res.length(); i++) {
            JSONObject r = res.getJSONObject(i), n = new JSONObject();
            n.put("name", r.optString("name"));
            n.put("lat", r.optDouble("latitude"));
            n.put("lon", r.optDouble("longitude"));
            n.put("country", r.optString("country"));
            n.put("admin", r.optString("admin1"));
            out.put(n);
        }
        return out;
    }

    static String emoji(int c, boolean day) {
        if (c == 0 || c == 1) return day ? (c == 0 ? "☀️" : "🌤️") : "🌙";
        if (c == 2) return day ? "⛅" : "☁️";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 57) return "🌦️";
        if (c >= 61 && c <= 67) return "🌧️";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "❄️";
        if (c >= 80 && c <= 82) return "🌧️";
        if (c >= 95) return "⛈️";
        return "☁️";
    }

    static String text(int c) {
        boolean r = ru();
        if (c == 0) return r ? "Ясно" : "Clear";
        if (c == 1) return r ? "Малооблачно" : "Mostly clear";
        if (c == 2) return r ? "Облачно с прояснениями" : "Partly cloudy";
        if (c == 3) return r ? "Пасмурно" : "Overcast";
        if (c == 45 || c == 48) return r ? "Туман" : "Fog";
        if (c >= 51 && c <= 57) return r ? "Морось" : "Drizzle";
        if (c >= 61 && c <= 67) return r ? "Дождь" : "Rain";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return r ? "Снег" : "Snow";
        if (c >= 80 && c <= 82) return r ? "Ливень" : "Showers";
        if (c >= 95) return r ? "Гроза" : "Thunderstorm";
        return r ? "Облачно" : "Cloudy";
    }
}
