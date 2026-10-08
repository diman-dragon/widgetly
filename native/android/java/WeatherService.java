package __APP_ID__;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Погода: текущая + прогноз на 3 дня вперёд.
 * Без ключа: Open-Meteo (по умолчанию), MET Norway. С ключом (API): OpenWeatherMap 2.5, WeatherAPI.com.
 * Кэш в SharedPreferences; запрос не чаще, чем раз в N часов (N ≥ 6).
 */
final class WeatherService {
    private WeatherService() {}

    static final String UA = "WidgetStudio/1.0 (Android home screen widget)";
    static final long MIN_INTERVAL = 6 * 3600_000L;
    static volatile String lastError = "";

    static final class Day { String date; int code; double hi, lo; }

    static final class Data {
        double temp, hi, lo;
        int code;
        boolean day = true;
        long ts;
        List<Day> days = new ArrayList<>();
    }

    static boolean ru() { return "ru".equals(Locale.getDefault().getLanguage()); }

    static String key(JSONObject w) {
        return w.optString("service", "openmeteo") + "|" + String.format(Locale.US, "%.3f|%.3f", w.optDouble("lat"), w.optDouble("lon"))
                + "|" + w.optString("units", "c") + "|" + w.optString("apiKey", "").hashCode();
    }

    // ---------- кэш ----------
    static Data cached(Context c, String key) {
        try {
            String s = Store.getString(c, "wx_" + key, null);
            if (s == null) return null;
            JSONObject j = new JSONObject(s);
            Data d = new Data();
            d.ts = j.getLong("ts"); d.temp = j.getDouble("temp"); d.hi = j.getDouble("hi"); d.lo = j.getDouble("lo");
            d.code = j.getInt("code"); d.day = j.optBoolean("day", true);
            JSONArray a = j.optJSONArray("days");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Day x = new Day();
                x.date = o.getString("date"); x.code = o.getInt("code"); x.hi = o.getDouble("hi"); x.lo = o.getDouble("lo");
                d.days.add(x);
            }
            return d;
        } catch (Exception e) { return null; }
    }

    static void save(Context c, String key, Data d) throws Exception {
        JSONObject j = new JSONObject();
        j.put("ts", d.ts); j.put("temp", d.temp); j.put("hi", d.hi); j.put("lo", d.lo); j.put("code", d.code); j.put("day", d.day);
        JSONArray a = new JSONArray();
        for (Day x : d.days) {
            JSONObject o = new JSONObject();
            o.put("date", x.date); o.put("code", x.code); o.put("hi", x.hi); o.put("lo", x.lo);
            a.put(o);
        }
        j.put("days", a);
        Store.putString(c, "wx_" + key, j.toString());
    }

    /** Данные из кэша; если кэша нет вообще и разрешена сеть — один запрос. */
    static Data get(Context c, JSONObject w, boolean allowNet) {
        Data d = cached(c, key(w));
        if (d == null && allowNet) return ensure(c, w, false, Long.MAX_VALUE);
        return d;
    }

    /**
     * Обновляет, если кэш старше maxAge (не меньше 6 часов). force обходит только паузу после ошибки.
     * При ошибке возвращает старый кэш; следующая попытка — не раньше чем через 10 минут.
     */
    static Data ensure(Context c, JSONObject w, boolean force, long maxAge) {
        String key = key(w);
        Data cd = cached(c, key);
        long now = System.currentTimeMillis();
        long age = Math.max(MIN_INTERVAL, maxAge);
        if (cd != null && now - cd.ts < age) return cd;
        if (!force && now - Store.getLong(c, "wxf_" + key, 0) < 10 * 60000L) return cd;
        try {
            Data nd = fetch(w);
            nd.ts = now;
            save(c, key, nd);
            lastError = "";
            return nd;
        } catch (Exception e) {
            lastError = String.valueOf(e.getMessage());
            Store.putLong(c, "wxf_" + key, now);
            return cd;
        }
    }

    /** Принудительная проверка (кнопка «Проверить» в редакторе): запрос и запись в кэш. */
    static String check(Context c, JSONObject w) {
        try {
            Data nd = fetch(w);
            nd.ts = System.currentTimeMillis();
            save(c, key(w), nd);
            return "OK: " + Math.round(nd.temp) + "°, " + text(nd.code);
        } catch (Exception e) {
            return String.valueOf(e.getMessage());
        }
    }

    // ---------- сеть ----------
    static String http(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(5000);
        h.setReadTimeout(5000);
        h.setRequestProperty("User-Agent", UA);
        h.setRequestProperty("Accept", "application/json");
        try {
            int code = h.getResponseCode();
            if (code == 401 || code == 403) throw new Exception("HTTP " + code + (ru() ? " — проверьте API-ключ" : " — check API key"));
            if (code != 200) throw new Exception("HTTP " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { h.disconnect(); }
    }

    static Data fetch(JSONObject w) throws Exception {
        String s = w.optString("service", "openmeteo");
        if (("owm".equals(s) || "weatherapi".equals(s)) && w.optString("apiKey", "").trim().isEmpty())
            throw new Exception(ru() ? "Нужен API-ключ" : "API key required");
        switch (s) {
            case "metno": return metno(w);
            case "owm": return owm(w);
            case "weatherapi": return weatherApi(w);
            default: return openMeteo(w);
        }
    }

    static double conv(double c, boolean f) { return f ? c * 9 / 5 + 32 : c; }
    static boolean isF(JSONObject w) { return "f".equals(w.optString("units", "c")); }

    static Data openMeteo(JSONObject w) throws Exception {
        String u = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto&temperature_unit=%s",
                w.getDouble("lat"), w.getDouble("lon"), isF(w) ? "fahrenheit" : "celsius");
        JSONObject j = new JSONObject(http(u));
        JSONObject cur = j.getJSONObject("current"), dd = j.getJSONObject("daily");
        JSONArray tm = dd.getJSONArray("time"), cd = dd.getJSONArray("weather_code"),
                hi = dd.getJSONArray("temperature_2m_max"), lo = dd.getJSONArray("temperature_2m_min");
        Data d = new Data();
        d.temp = cur.getDouble("temperature_2m");
        d.code = cur.getInt("weather_code");
        d.day = cur.optInt("is_day", 1) == 1;
        d.hi = hi.getDouble(0);
        d.lo = lo.getDouble(0);
        for (int i = 1; i < Math.min(4, tm.length()); i++) {
            Day x = new Day();
            x.date = tm.getString(i); x.code = cd.getInt(i); x.hi = hi.getDouble(i); x.lo = lo.getDouble(i);
            d.days.add(x);
        }
        return d;
    }

    static Data metno(JSONObject w) throws Exception {
        boolean f = isF(w);
        String u = String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
                w.getDouble("lat"), w.getDouble("lon"));
        JSONArray ts = new JSONObject(http(u)).getJSONObject("properties").getJSONArray("timeseries");
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        in.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat dk = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat hk = new SimpleDateFormat("H", Locale.US);
        Map<String, double[]> mm = new LinkedHashMap<>();   // min, max, расстояние до 13:00
        Map<String, String> sym = new HashMap<>();
        Data d = new Data();
        for (int i = 0; i < ts.length(); i++) {
            JSONObject e = ts.getJSONObject(i), data = e.getJSONObject("data");
            Date t = in.parse(e.getString("time"));
            String k = dk.format(t);
            double v = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            if (i == 0) {
                d.temp = conv(v, f);
                String s0 = symbolOf(data);
                d.day = s0 == null || !s0.endsWith("_night");
                d.code = metToWmo(s0 == null ? "cloudy" : s0);
            }
            double[] a = mm.get(k);
            if (a == null) { a = new double[]{v, v, 99}; mm.put(k, a); }
            a[0] = Math.min(a[0], v); a[1] = Math.max(a[1], v);
            String s = symbolOf(data);
            double dist = Math.abs(Integer.parseInt(hk.format(t)) - 13);
            if (s != null && dist < a[2]) { a[2] = dist; sym.put(k, s); }
        }
        int idx = 0;
        for (Map.Entry<String, double[]> en : mm.entrySet()) {
            double[] a = en.getValue();
            if (idx == 0) { d.hi = conv(a[1], f); d.lo = conv(a[0], f); }
            else if (idx <= 3) {
                Day x = new Day();
                x.date = en.getKey(); x.hi = conv(a[1], f); x.lo = conv(a[0], f);
                x.code = metToWmo(sym.containsKey(en.getKey()) ? sym.get(en.getKey()) : "cloudy");
                d.days.add(x);
            }
            idx++;
        }
        return d;
    }

    private static String symbolOf(JSONObject data) {
        for (String k : new String[]{"next_6_hours", "next_1_hours", "next_12_hours"}) {
            JSONObject n = data.optJSONObject(k);
            if (n != null && n.optJSONObject("summary") != null) return n.getJSONObject("summary").optString("symbol_code", null);
        }
        return null;
    }

    static Data owm(JSONObject w) throws Exception {
        String q = String.format(Locale.US, "lat=%.4f&lon=%.4f&units=%s&appid=%s", w.getDouble("lat"), w.getDouble("lon"),
                isF(w) ? "imperial" : "metric", URLEncoder.encode(w.optString("apiKey").trim(), "UTF-8"));
        JSONObject cur = new JSONObject(http("https://api.openweathermap.org/data/2.5/weather?" + q));
        JSONObject fc = new JSONObject(http("https://api.openweathermap.org/data/2.5/forecast?" + q));
        Data d = new Data();
        d.temp = cur.getJSONObject("main").getDouble("temp");
        JSONObject cw = cur.getJSONArray("weather").getJSONObject(0);
        d.code = owmToWmo(cw.getInt("id"));
        d.day = !cw.optString("icon", "d").endsWith("n");
        long tz = fc.getJSONObject("city").optLong("timezone", 0);
        SimpleDateFormat dk = new SimpleDateFormat("yyyy-MM-dd", Locale.US), hk = new SimpleDateFormat("H", Locale.US);
        dk.setTimeZone(TimeZone.getTimeZone("UTC")); hk.setTimeZone(TimeZone.getTimeZone("UTC"));
        Map<String, double[]> mm = new LinkedHashMap<>();
        JSONArray list = fc.getJSONArray("list");
        for (int i = 0; i < list.length(); i++) {
            JSONObject e = list.getJSONObject(i);
            Date t = new Date((e.getLong("dt") + tz) * 1000);
            String k = dk.format(t);
            JSONObject m = e.getJSONObject("main");
            double[] a = mm.get(k);
            if (a == null) { a = new double[]{m.getDouble("temp_min"), m.getDouble("temp_max"), 99, 3}; mm.put(k, a); }
            a[0] = Math.min(a[0], m.getDouble("temp_min")); a[1] = Math.max(a[1], m.getDouble("temp_max"));
            double dist = Math.abs(Integer.parseInt(hk.format(t)) - 13);
            if (dist < a[2]) { a[2] = dist; a[3] = owmToWmo(e.getJSONArray("weather").getJSONObject(0).getInt("id")); }
        }
        int idx = 0;
        d.hi = d.lo = d.temp;
        for (Map.Entry<String, double[]> en : mm.entrySet()) {
            double[] a = en.getValue();
            if (idx == 0) { d.hi = Math.max(a[1], d.temp); d.lo = Math.min(a[0], d.temp); }
            else if (idx <= 3) {
                Day x = new Day();
                x.date = en.getKey(); x.hi = a[1]; x.lo = a[0]; x.code = (int) a[3];
                d.days.add(x);
            }
            idx++;
        }
        return d;
    }

    static Data weatherApi(JSONObject w) throws Exception {
        boolean f = isF(w);
        String u = String.format(Locale.US, "https://api.weatherapi.com/v1/forecast.json?key=%s&q=%.4f,%.4f&days=4&aqi=no&alerts=no",
                URLEncoder.encode(w.optString("apiKey").trim(), "UTF-8"), w.getDouble("lat"), w.getDouble("lon"));
        JSONObject j = new JSONObject(http(u));
        JSONObject cur = j.getJSONObject("current");
        Data d = new Data();
        d.temp = cur.getDouble(f ? "temp_f" : "temp_c");
        d.code = waToWmo(cur.getJSONObject("condition").getInt("code"));
        d.day = cur.optInt("is_day", 1) == 1;
        JSONArray fd = j.getJSONObject("forecast").getJSONArray("forecastday");
        for (int i = 0; i < Math.min(4, fd.length()); i++) {
            JSONObject o = fd.getJSONObject(i), day = o.getJSONObject("day");
            double hi = day.getDouble(f ? "maxtemp_f" : "maxtemp_c"), lo = day.getDouble(f ? "mintemp_f" : "mintemp_c");
            if (i == 0) { d.hi = hi; d.lo = lo; continue; }
            Day x = new Day();
            x.date = o.getString("date"); x.hi = hi; x.lo = lo; x.code = waToWmo(day.getJSONObject("condition").getInt("code"));
            d.days.add(x);
        }
        return d;
    }

    // ---------- коды → WMO ----------
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

    static int owmToWmo(int id) {
        if (id >= 200 && id < 300) return 95;
        if (id >= 300 && id < 400) return 51;
        if (id == 500) return 61;
        if (id >= 501 && id <= 502) return 63;
        if (id >= 503 && id <= 504) return 65;
        if (id == 511) return 67;
        if (id >= 520 && id < 600) return 80;
        if (id >= 600 && id < 700) return id <= 601 ? 71 : 75;
        if (id >= 700 && id < 800) return 45;
        if (id == 800) return 0;
        if (id == 801) return 1;
        if (id == 802) return 2;
        return 3;
    }

    static int waToWmo(int c) {
        switch (c) {
            case 1000: return 0;
            case 1003: return 2;
            case 1006: case 1009: return 3;
            case 1030: case 1135: case 1147: return 45;
            case 1150: case 1153: case 1168: case 1171: return 51;
            case 1063: case 1180: case 1183: case 1240: return 61;
            case 1186: case 1189: case 1243: return 63;
            case 1192: case 1195: case 1246: return 65;
            case 1087: case 1273: case 1276: case 1279: case 1282: return 95;
            case 1066: case 1069: case 1072: case 1114: case 1117: case 1204: case 1207: case 1210: case 1213: case 1216:
            case 1219: case 1222: case 1225: case 1237: case 1249: case 1252: case 1255: case 1258: case 1261: case 1264: return 73;
            default: return 3;
        }
    }

    // ---------- поиск города (геокодер Open-Meteo, без ключа) ----------
    static JSONArray search(String q, String lang) throws Exception {
        String u = "https://geocoding-api.open-meteo.com/v1/search?count=6&format=json&language=" + lang + "&name=" + URLEncoder.encode(q, "UTF-8");
        JSONArray res = new JSONObject(http(u)).optJSONArray("results"), out = new JSONArray();
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
