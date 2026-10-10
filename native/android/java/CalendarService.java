package __APP_ID__;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * События календаря устройства (только чтение, только на устройстве, никуда не отправляются).
 * Берём ближайшие события: ещё не закончившиеся, начинающиеся не позже конца окна (сегодня + days-1 дней).
 */
final class CalendarService {
    private CalendarService() {}

    static final int MAX_EVENTS = 12;

    static final class Ev {
        String label = "";   // время/день: «09:30», «Пт 18:00», пусто для «весь день сегодня»
        String title = "";
        long sortKey;
    }

    static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private static Calendar midnight(long t) {
        Calendar k = Calendar.getInstance();
        k.setTimeInMillis(t);
        k.set(Calendar.HOUR_OF_DAY, 0); k.set(Calendar.MINUTE, 0); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
        return k;
    }

    /** Событие «на весь день» хранится как UTC-полночь; переводим в локальную полночь той же даты. */
    private static long localMidnightOfUtcDate(long utc) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(utc);
        Calendar l = Calendar.getInstance();
        l.clear();
        l.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return l.getTimeInMillis();
    }

    static List<Ev> list(Context c, int days) {
        List<Ev> out = new ArrayList<>();
        if (!granted(c)) return out;
        try {
            long now = System.currentTimeMillis();
            long startToday = midnight(now).getTimeInMillis();
            Calendar endC = midnight(now);
            endC.add(Calendar.DAY_OF_YEAR, Math.max(1, days));
            long windowEnd = endC.getTimeInMillis();
            long pad = 14 * 3600_000L;

            Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, startToday - pad);
            ContentUris.appendId(b, windowEnd + pad);
            String[] proj = {CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY};
            Cursor cur;
            try {
                cur = c.getContentResolver().query(b.build(), proj, CalendarContract.Instances.VISIBLE + "=1", null, null);
            } catch (Exception e) {
                cur = c.getContentResolver().query(b.build(), proj, null, null, null);
            }
            if (cur == null) return out;
            boolean h24 = DateFormat.is24HourFormat(c);
            SimpleDateFormat tf = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.getDefault());
            SimpleDateFormat df = new SimpleDateFormat("EEE", Locale.getDefault());
            try {
                while (cur.moveToNext()) {
                    String title = cur.getString(0);
                    long begin = cur.getLong(1), end = cur.getLong(2);
                    boolean allDay = cur.getInt(3) == 1;
                    if (title == null || title.trim().isEmpty()) title = WeatherService.ru() ? "(без названия)" : "(no title)";
                    Ev e = new Ev();
                    e.title = title.trim().replace('\n', ' ');
                    if (allDay) {
                        long s = localMidnightOfUtcDate(begin), en = localMidnightOfUtcDate(end);
                        if (!(en > startToday && s < windowEnd)) continue;
                        e.sortKey = Math.max(s, startToday) - 1;
                        e.label = s <= startToday ? "" : cap(df.format(s));
                    } else {
                        if (!(end > now && begin < windowEnd)) continue;
                        e.sortKey = begin;
                        String t = tf.format(begin);
                        e.label = midnight(begin).getTimeInMillis() == startToday ? t : cap(df.format(begin)) + " " + t;
                    }
                    out.add(e);
                }
            } finally { cur.close(); }
        } catch (Exception ignored) {
            // нет доступа/провайдера — просто нет событий
        }
        Collections.sort(out, new Comparator<Ev>() {
            @Override public int compare(Ev a, Ev b) { return Long.compare(a.sortKey, b.sortKey); }
        });
        return out.size() > MAX_EVENTS ? new ArrayList<>(out.subList(0, MAX_EVENTS)) : out;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    /** Страницы по 2 строки; при 3+ событиях виджет листает их по кругу. */
    static List<List<Ev>> pages(List<Ev> all) {
        List<List<Ev>> p = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 2) p.add(new ArrayList<>(all.subList(i, Math.min(all.size(), i + 2))));
        return p;
    }

    static String signature(List<Ev> all) {
        StringBuilder sb = new StringBuilder();
        for (Ev e : all) sb.append(e.label).append('|').append(e.title).append(';');
        return Integer.toString(sb.toString().hashCode());
    }
}
