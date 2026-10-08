package __APP_ID__;

import android.content.Context;
import android.graphics.*;
import org.json.JSONObject;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Рисует виджет целиком в Bitmap по JSON-конфигу. Один код для превью в редакторе и для рабочего стола.
 * Часы на рабочем столе — системный TextClock поверх картинки (drawClock=false), поэтому здесь
 * заданы общие функции геометрии часов (clockRect/clockSize), по которым их ставит Updater.
 */
final class Renderer {
    private Renderer() {}

    // ---------- helpers ----------
    static JSONObject sub(JSONObject o, String k) {
        JSONObject s = o.optJSONObject(k);
        return s == null ? new JSONObject() : s;
    }

    static int col(String hex, int alphaPct, int def) {
        try {
            int c = Color.parseColor(hex);
            return alphaPct >= 0 ? (c & 0x00FFFFFF) | (Math.round(alphaPct * 2.55f) << 24) : c;
        } catch (Exception e) { return def; }
    }

    static Typeface tf(String fam) {
        return Typeface.create(fam == null || fam.isEmpty() ? "sans-serif" : fam, Typeface.NORMAL);
    }

    static RectF rf(float l, float t, float r, float b) { return new RectF(l, t, r, b); }

    static float fitSize(Paint p, String text, float w, float h, float scale) {
        p.setTextSize(100);
        float tw = Math.max(1, p.measureText(text));
        Paint.FontMetrics fm = p.getFontMetrics();
        float th = Math.max(1, fm.descent - fm.ascent);
        return 100 * Math.min(w / tw, h / th) * scale;
    }

    static void drawFit(Canvas cv, String text, Paint p, RectF r, String align, float scale) {
        if (text == null || text.isEmpty() || r.width() <= 0 || r.height() <= 0) return;
        p.setTextSize(fitSize(p, text, r.width(), r.height(), scale));
        Paint.FontMetrics fm = p.getFontMetrics();
        float tw = p.measureText(text);
        float x = "center".equals(align) ? r.centerX() - tw / 2 : "right".equals(align) ? r.right - tw : r.left;
        cv.drawText(text, x, r.centerY() - (fm.ascent + fm.descent) / 2, p);
    }

    static Paint paint(int color, float alpha, String font) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf(font));
        p.setColor((color & 0x00FFFFFF) | ((int) (Color.alpha(color) * alpha) << 24));
        return p;
    }

    static String fmt(String pat, String def, Date d, TimeZone tz) {
        try {
            SimpleDateFormat f = new SimpleDateFormat(pat, Locale.getDefault());
            if (tz != null) f.setTimeZone(tz);
            return f.format(d);
        } catch (Exception e) {
            return new SimpleDateFormat(def, Locale.getDefault()).format(d);
        }
    }

    static String cap(String s, boolean upper) {
        if (s.isEmpty()) return s;
        return upper ? s.toUpperCase(Locale.getDefault()) : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    // ---------- геометрия ----------
    static float split(JSONObject cfg, int w) { return w * (float) cfg.optDouble("split", 48) / 100f; }

    static RectF clockRect(JSONObject cfg, int w, int h) {
        return rf(0.06f * w, 0.08f * h, split(cfg, w) - 0.02f * w, 0.47f * h);
    }

    static String clockSample(String fmt) { return fmt.contains("a") ? "00:00 PM" : "00:00"; }

    static float clockSize(JSONObject cfg, int w, int h) {
        JSONObject ck = sub(cfg, "clock");
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf(ck.optString("font", "sans-serif-light")));
        RectF r = clockRect(cfg, w, h);
        // 0.9 — запас: реальный TextClock и растяжка картинки дают отличие в ширине, без запаса обрезается последняя цифра
        return 0.9f * fitSize(p, clockSample(ck.optString("fmt", "HH:mm")), r.width(), r.height(), (float) ck.optDouble("scale", 1));
    }

    // ---------- main ----------
    static Bitmap render(Context c, JSONObject cfg, int w, int h, float dens, long dateTs, boolean drawClock, boolean net) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float rad = (float) cfg.optDouble("radius", 28) * dens;
        int alpha = Math.round(Math.max(0, Math.min(100, cfg.optInt("opacity", 92))) * 2.55f);
        int fg = col(cfg.optString("fg", "#FFFFFF"), -1, Color.WHITE);
        int fg2 = col(cfg.optString("fg2", "#B7BEE8"), -1, 0xFFB7BEE8);

        // фон (прозрачность применяется ко всему фону, текст остаётся чётким)
        int layer = cv.saveLayerAlpha(0, 0, w, h, alpha);
        drawBackground(c, cv, cfg, w, h, dens);
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setColor(Color.BLACK);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        cv.drawRoundRect(new RectF(0, 0, w, h), rad, rad, mask);
        cv.restoreToCount(layer);

        if (cfg.optBoolean("border", true)) {
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setStyle(Paint.Style.STROKE);
            b.setStrokeWidth(dens);
            b.setColor((0x38 << 24) | 0xFFFFFF);
            b.setAlpha((int) (0x38 * alpha / 255f));
            cv.drawRoundRect(new RectF(dens / 2, dens / 2, w - dens / 2, h - dens / 2), rad, rad, b);
        }

        try { drawLeft(cv, cfg, w, h, dens, dateTs, drawClock, fg); } catch (Exception ignored) {}
        try { drawRight(c, cv, cfg, w, h, dens, net, fg, fg2); } catch (Exception ignored) {}
        return bmp;
    }

    static Bitmap message(String msg, int w, int h, float dens) {
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xD91B2147);
        cv.drawRoundRect(new RectF(0, 0, w, h), 24 * dens, 24 * dens, p);
        p.setColor(Color.WHITE);
        drawFit(cv, msg, p, rf(12 * dens, 12 * dens, w - 12 * dens, h - 12 * dens), "center", 0.5f);
        return bmp;
    }

    // ---------- фон ----------
    private static void drawBackground(Context c, Canvas cv, JSONObject cfg, int w, int h, float dens) {
        float split = split(cfg, w);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int c1 = col(cfg.optString("panel", "#2A3363"), -1, 0xFF2A3363);
        int c2 = col(cfg.optString("panel2", "#171C3E"), -1, 0xFF171C3E);
        p.setShader(new LinearGradient(split, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);

        String mode = cfg.optString("bgMode", "sunset");
        if (!"none".equals(mode)) {
            float fade = 0.06f * w;
            RectF lr = rf(0, 0, split + fade, h);
            int ly = cv.saveLayer(0, 0, w, h, null);
            boolean ok = false;
            if ("photo".equals(mode)) ok = drawPhoto(c, cv, lr);
            if (!ok) scene(cv, lr, "night".equals(mode));
            int dim = cfg.optInt("dim", 10);
            if (dim > 0) {
                Paint d = new Paint();
                d.setColor(Color.BLACK);
                d.setAlpha(Math.round(dim * 2.55f));
                cv.drawRect(lr, d);
            }
            Paint fm = new Paint();
            fm.setShader(new LinearGradient(split - 0.03f * w, 0, split + fade, 0, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
            fm.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cv.drawRect(lr, fm);
            cv.restoreToCount(ly);
        }
        // разделитель левой и правой частей
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0x33FFFFFF);
        line.setStrokeWidth(Math.max(1f, dens * 0.8f));
        cv.drawLine(split, 0.1f * h, split, 0.9f * h, line);
    }

    private static boolean drawPhoto(Context c, Canvas cv, RectF lr) {
        try {
            File f = Store.bgFile(c);
            if (!f.exists()) return false;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return false;
            float k = Math.max(lr.width() / b.getWidth(), lr.height() / b.getHeight());
            float sw = lr.width() / k, sh = lr.height() / k;
            Rect src = new Rect(Math.round((b.getWidth() - sw) / 2), Math.round((b.getHeight() - sh) / 2),
                    Math.round((b.getWidth() + sw) / 2), Math.round((b.getHeight() + sh) / 2));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            cv.drawBitmap(b, src, lr, p);
            b.recycle();
            return true;
        } catch (Throwable t) { return false; }
    }

    /** Встроенные «фото»: закат над бухтой и ночной вариант — рисуются кодом, без файлов. */
    private static void scene(Canvas cv, RectF r, boolean night) {
        float W = r.width(), H = r.height(), hz = r.top + H * 0.64f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] sky = night ? new int[]{0xFF070B26, 0xFF1B2A6B, 0xFF4A66A8, 0xFF8FA8D8}
                          : new int[]{0xFF5B5A9C, 0xFF9A6FB1, 0xFFE88AA2, 0xFFF7BA8C};
        p.setShader(new LinearGradient(0, r.top, 0, hz, sky, new float[]{0, 0.4f, 0.78f, 1}, Shader.TileMode.CLAMP));
        cv.drawRect(r.left, r.top, r.right, hz, p);
        p.setShader(new RadialGradient(r.left + W * 0.55f, hz, H * 0.55f, night ? 0x558FA8D8 : 0x88FFC28A, 0x00FFC28A, Shader.TileMode.CLAMP));
        cv.drawRect(r.left, r.top, r.right, hz + 1, p);
        p.setShader(null);
        if (night) {
            p.setColor(0xCCFFFFFF);
            Random rn = new Random(7);
            for (int i = 0; i < 40; i++) cv.drawCircle(r.left + rn.nextFloat() * W, r.top + rn.nextFloat() * H * 0.5f, 0.6f + rn.nextFloat() * 1.2f, p);
            p.setColor(0xFFF2F4FF);
            cv.drawCircle(r.left + W * 0.78f, r.top + H * 0.2f, H * 0.05f, p);
        }
        p.setColor(night ? 0xFF1C2656 : 0xFF6A5A98);
        Path m = new Path();
        m.moveTo(r.left, hz); m.lineTo(r.left, hz - H * 0.08f);
        m.quadTo(r.left + W * 0.2f, hz - H * 0.2f, r.left + W * 0.38f, hz - H * 0.07f);
        m.quadTo(r.left + W * 0.55f, hz - H * 0.14f, r.left + W * 0.7f, hz - H * 0.06f);
        m.lineTo(r.right, hz - H * 0.04f); m.lineTo(r.right, hz); m.close();
        cv.drawPath(m, p);
        p.setColor(night ? 0xFF111A44 : 0xFF453F7C);
        Path m2 = new Path();
        m2.moveTo(r.left + W * 0.45f, hz);
        m2.quadTo(r.left + W * 0.7f, hz - H * 0.3f, r.left + W * 0.82f, hz - H * 0.25f);
        m2.lineTo(r.right, hz - H * 0.1f); m2.lineTo(r.right, hz); m2.close();
        cv.drawPath(m2, p);
        p.setShader(new LinearGradient(0, hz, 0, r.bottom, night ? 0xFF2A3E80 : 0xFFEE8097, night ? 0xFF0E1538 : 0xFF3B3470, Shader.TileMode.CLAMP));
        cv.drawRect(r.left, hz, r.right, r.bottom, p);
        p.setShader(null);
        p.setColor(night ? 0xFF050818 : 0xFF12163A);
        Path s = new Path();
        s.moveTo(r.left + W * 0.35f, r.bottom);
        s.quadTo(r.left + W * 0.55f, r.bottom - H * 0.16f, r.right, r.bottom - H * 0.3f);
        s.lineTo(r.right, r.bottom); s.close();
        cv.drawPath(s, p);
        p.setColor(0xFFFFC46B);
        Random rn = new Random(3);
        for (int i = 0; i < 26; i++) {
            float x = r.left + W * (0.5f + rn.nextFloat() * 0.5f);
            float y = r.bottom - H * (0.03f + rn.nextFloat() * 0.12f) - (x - r.left - W * 0.5f) / W * H * 0.2f;
            cv.drawCircle(x, y, 0.8f + rn.nextFloat(), p);
        }
    }

    // ---------- левая часть: часы, день недели, дата ----------
    private static void drawLeft(Canvas cv, JSONObject cfg, int w, int h, float dens, long dateTs, boolean drawClock, int fg) {
        float split = split(cfg, w), L = 0.06f * w, R = split - 0.02f * w;
        Date d = new Date(dateTs);
        JSONObject ck = sub(cfg, "clock"), wd = sub(cfg, "weekday"), dt = sub(cfg, "date");

        if (drawClock && ck.optBoolean("show", true)) {
            String cs = ck.optString("color", "");
            Paint p = paint(cs.isEmpty() ? fg : col(cs, -1, fg), 1f, ck.optString("font", "sans-serif-light"));
            p.setShadowLayer(4 * dens, 0, 2 * dens, 0x66000000);
            String tz = ck.optString("tz", "");
            String s = fmt(ck.optString("fmt", "HH:mm"), "HH:mm", new Date(), TimeZone.getTimeZone(tz.isEmpty() ? TimeZone.getDefault().getID() : tz));
            p.setTextSize(clockSize(cfg, w, h));
            RectF r = clockRect(cfg, w, h);
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(s, r.left, r.centerY() - (fm.ascent + fm.descent) / 2, p);
        }
        if (wd.optBoolean("show", true)) {
            String cs = wd.optString("color", "");
            Paint p = paint(cs.isEmpty() ? fg : col(cs, -1, fg), 0.88f, wd.optString("font", "sans-serif"));
            p.setShadowLayer(3 * dens, 0, 1.5f * dens, 0x66000000);
            String s = cap(fmt(wd.optString("pattern", "EEEE"), "EEEE", d, null), wd.optBoolean("upper", false));
            drawFit(cv, s, p, rf(L, 0.50f * h, R, 0.61f * h), "left", (float) wd.optDouble("scale", 1));
        }
        if (dt.optBoolean("show", true)) {
            String cs = dt.optString("color", "");
            Paint p = paint(cs.isEmpty() ? fg : col(cs, -1, fg), 1f, dt.optString("font", "sans-serif-medium"));
            p.setShadowLayer(3 * dens, 0, 1.5f * dens, 0x66000000);
            String s = fmt(dt.optString("pattern", "d MMMM yyyy"), "d MMMM yyyy", d, null);
            drawFit(cv, s, p, rf(L, 0.62f * h, R, 0.77f * h), "left", (float) dt.optDouble("scale", 1));
        }
    }

    // ---------- собственные иконки погоды (без эмодзи: одинаково на всех версиях Android) ----------
    private static void cloud(Canvas cv, float cx, float cy, float w, int top, int bottom) {
        Path p = new Path();
        p.addCircle(cx - w * 0.2f, cy, w * 0.2f, Path.Direction.CW);
        p.addCircle(cx + w * 0.04f, cy - w * 0.12f, w * 0.26f, Path.Direction.CW);
        p.addCircle(cx + w * 0.28f, cy + w * 0.02f, w * 0.18f, Path.Direction.CW);
        p.addRoundRect(new RectF(cx - w * 0.4f, cy - w * 0.02f, cx + w * 0.46f, cy + w * 0.2f), w * 0.1f, w * 0.1f, Path.Direction.CW);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new LinearGradient(0, cy - w * 0.38f, 0, cy + w * 0.2f, top, bottom, Shader.TileMode.CLAMP));
        cv.drawPath(p, q);
    }

    private static void sun(Canvas cv, float cx, float cy, float r) {
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setColor(0xFFFFC83D);
        q.setStyle(Paint.Style.STROKE);
        q.setStrokeCap(Paint.Cap.ROUND);
        q.setStrokeWidth(r * 0.2f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            cv.drawLine(cx + (float) Math.cos(a) * r * 1.38f, cy + (float) Math.sin(a) * r * 1.38f,
                    cx + (float) Math.cos(a) * r * 1.75f, cy + (float) Math.sin(a) * r * 1.75f, q);
        }
        q.setStyle(Paint.Style.FILL);
        q.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, 0xFFFFE680, 0xFFFFB020, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r, q);
    }

    private static void moon(Canvas cv, float cx, float cy, float r) {
        Path a = new Path(), b = new Path();
        a.addCircle(cx, cy, r, Path.Direction.CW);
        b.addCircle(cx + r * 0.5f, cy - r * 0.3f, r * 0.85f, Path.Direction.CW);
        a.op(b, Path.Op.DIFFERENCE);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setColor(0xFFF3EFD2);
        cv.drawPath(a, q);
    }

    static void drawIcon(Canvas cv, RectF area, int code, boolean day) {
        float s = Math.min(area.width(), area.height());
        if (s <= 0) return;
        float x = area.centerX() - s / 2, y = area.centerY() - s / 2;
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setStrokeCap(Paint.Cap.ROUND);
        int cTop = 0xFFF4F6FF, cBot = 0xFFB4BCE6;
        boolean thunder = code >= 95, snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82), fog = code == 45 || code == 48;
        if (code == 0) {
            if (day) sun(cv, x + s * 0.5f, y + s * 0.5f, s * 0.24f); else moon(cv, x + s * 0.5f, y + s * 0.5f, s * 0.32f);
        } else if (code == 1 || code == 2) {
            if (day) sun(cv, x + s * 0.36f, y + s * 0.36f, s * 0.17f); else moon(cv, x + s * 0.36f, y + s * 0.38f, s * 0.22f);
            cloud(cv, x + s * 0.56f, y + s * 0.64f, s * 0.78f, cTop, cBot);
        } else if (code == 3) {
            cloud(cv, x + s * 0.5f, y + s * 0.55f, s * 0.9f, cTop, cBot);
        } else {
            if (thunder) { cTop = 0xFFA6ABD0; cBot = 0xFF5B6090; }
            else if (rain || snow) { cTop = 0xFFDDE2F7; cBot = 0xFF9AA3D4; }
            cloud(cv, x + s * 0.5f, y + s * 0.4f, s * 0.84f, cTop, cBot);
            if (fog) {
                q.setColor(0xCCFFFFFF); q.setStrokeWidth(s * 0.06f);
                for (int i = 0; i < 3; i++) cv.drawLine(x + s * (0.2f + i * 0.04f), y + s * (0.68f + i * 0.1f), x + s * (0.8f - i * 0.04f), y + s * (0.68f + i * 0.1f), q);
            } else if (rain) {
                q.setColor(0xFF6EC1FF); q.setStrokeWidth(s * 0.06f);
                for (int i = 0; i < 3; i++) cv.drawLine(x + s * (0.34f + i * 0.16f), y + s * 0.7f, x + s * (0.29f + i * 0.16f), y + s * 0.86f, q);
            } else if (snow) {
                q.setColor(0xFFFFFFFF); q.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) cv.drawCircle(x + s * (0.34f + i * 0.16f), y + s * (0.76f + (i % 2) * 0.08f), s * 0.04f, q);
            } else if (thunder) {
                Path b = new Path();
                b.moveTo(x + s * 0.54f, y + s * 0.58f); b.lineTo(x + s * 0.42f, y + s * 0.8f); b.lineTo(x + s * 0.52f, y + s * 0.8f);
                b.lineTo(x + s * 0.46f, y + s * 0.97f); b.lineTo(x + s * 0.64f, y + s * 0.72f); b.lineTo(x + s * 0.53f, y + s * 0.72f); b.close();
                q.setColor(0xFFFFD43B); q.setStyle(Paint.Style.FILL);
                cv.drawPath(b, q);
            }
        }
    }

    private static void drawPin(Canvas cv, RectF r, int color) {
        float s = Math.min(r.width(), r.height()), cx = r.centerX(), top = r.centerY() - s / 2;
        Path p = new Path(), hole = new Path();
        p.addCircle(cx, top + s * 0.36f, s * 0.32f, Path.Direction.CW);
        Path tri = new Path();
        tri.moveTo(cx - s * 0.27f, top + s * 0.5f); tri.lineTo(cx + s * 0.27f, top + s * 0.5f); tri.lineTo(cx, top + s * 0.98f); tri.close();
        p.op(tri, Path.Op.UNION);
        hole.addCircle(cx, top + s * 0.36f, s * 0.12f, Path.Direction.CW);
        p.op(hole, Path.Op.DIFFERENCE);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setColor(color);
        cv.drawPath(p, q);
    }

    // ---------- правая часть: погода ----------
    // Раскладки: компактная (высота < 140 dp: без прогноза, крупнее текущая погода) и полная (с прогнозом).
    private static void drawRight(Context c, Canvas cv, JSONObject cfg, int w, int h, float dens, boolean net, int fg, int fg2) {
        JSONObject wc = sub(cfg, "weather");
        if (!wc.optBoolean("show", true)) return;
        WeatherService.Data wd = wc.has("lat") ? WeatherService.get(c, wc, net) : null;
        boolean compact = h / dens < 140;
        float split = split(cfg, w);
        float L = split + 0.045f * w, R = w - 0.045f * w;
        float btnW = cfg.optBoolean("refresh", true) ? 42 * dens : 0;
        String font = wc.optString("font", "sans-serif");
        float sc = (float) wc.optDouble("scale", 1);

        // город (значок-метка рисуется кодом) и страна
        float cityT = 0.07f * h, cityB = (compact ? 0.21f : 0.16f) * h, pin = cityB - cityT;
        drawPin(cv, rf(L, cityT, L + pin * 0.8f, cityB), fg);
        float cx = L + pin * 0.95f;
        drawFit(cv, wc.optString("city", ""), paint(fg, 1f, font), rf(cx, cityT, R - btnW, cityB), "left", sc);
        String country = wc.optString("country", "");
        if (!compact && wc.optBoolean("showCountry", true) && !country.isEmpty())
            drawFit(cv, country, paint(fg2, 1f, font), rf(cx, 0.165f * h, R - btnW, 0.235f * h), "left", sc);

        // иконка и текущая температура
        String temp = wd == null ? "--°" : Math.round(wd.temp) + "°";
        float iy = compact ? 0.45f : 0.38f, side = Math.min(0.17f * w, (compact ? 0.4f : 0.27f) * h);
        drawIcon(cv, rf(L, iy * h - side / 2, L + side, iy * h + side / 2), wd == null ? 3 : wd.code, wd == null || wd.day);
        float tx = L + side + 0.025f * w;
        drawFit(cv, temp, paint(fg, 1f, font), compact ? rf(tx, 0.26f * h, R, 0.56f * h) : rf(tx, 0.23f * h, R, 0.42f * h), "left", sc);
        if (wd != null && wc.optBoolean("showCond", true))
            drawFit(cv, WeatherService.text(wd.code), paint(fg, 0.85f, font),
                    compact ? rf(L, 0.64f * h, R, 0.78f * h) : rf(tx, 0.425f * h, R, 0.495f * h), "left", sc);
        if (wd != null && wc.optBoolean("showHiLo", true))
            drawFit(cv, "↑" + Math.round(wd.hi) + "°   ↓" + Math.round(wd.lo) + "°", paint(fg, 0.85f, font),
                    compact ? rf(L, 0.79f * h, R, 0.93f * h) : rf(tx, 0.50f * h, R, 0.575f * h), "left", sc);

        // разделитель и прогноз на 1–3 дня вперёд (только в полной раскладке)
        int n = (compact || wd == null) ? 0 : Math.min(Math.min(3, wc.optInt("days", 3)), wd.days.size());
        if (n <= 0) return;
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0x40FFFFFF);
        line.setStrokeWidth(Math.max(1f, dens * 0.8f));
        cv.drawLine(L, 0.605f * h, R, 0.605f * h, line);
        float cw = (R - L) / n;
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new SimpleDateFormat("EEE", Locale.getDefault());
        line.setColor(0x26FFFFFF);
        for (int i = 0; i < n; i++) {
            WeatherService.Day dd = wd.days.get(i);
            float x0 = L + i * cw, x1 = x0 + cw, pad = cw * 0.06f;
            if (i > 0) cv.drawLine(x0, 0.64f * h, x0, 0.95f * h, line);
            String dow;
            try { dow = out.format(in.parse(dd.date)).toUpperCase(Locale.getDefault()); } catch (Exception e) { dow = ""; }
            drawFit(cv, dow, paint(fg, 0.9f, font), rf(x0 + pad, 0.625f * h, x1 - pad, 0.70f * h), "center", sc);
            drawIcon(cv, rf(x0 + pad, 0.705f * h, x1 - pad, 0.81f * h), dd.code, true);
            drawFit(cv, Math.round(dd.hi) + "°", paint(fg, 1f, font), rf(x0 + pad, 0.815f * h, x1 - pad, 0.885f * h), "center", sc);
            drawFit(cv, Math.round(dd.lo) + "°", paint(fg2, 0.8f, font), rf(x0 + pad, 0.885f * h, x1 - pad, 0.955f * h), "center", sc);
        }
    }
}
