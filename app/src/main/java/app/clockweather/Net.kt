package app.clockweather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/**
 * Единая точка выхода в сеть.
 *
 * Энергоэффективность:
 *  - перед открытием сокета проверяется реальная доступность сети, поэтому на заглушённом
 *    радио / в авиарежиме модем не «будится» впустую (нет 10 секунд таймаутов);
 *  - заголовок `If-None-Match` + кэш ответа в SharedPreferences: повторный запрос отдаёт
 *    `304 Not Modified` (~0 байт трафика) вместо полного JSON;
 *  - соединение переиспользуется (`keepAlive`, нет явного `disconnect()`), поэтому DNS и
 *    TLS handshake выполняются один раз на серию обращений, а не на каждое.
 */
object Net {
    private const val TO = 8000

    /** Есть ли реально работающая сеть (интернет, а не «подключён к Wi-Fi без доступа»). */
    fun online(c: Context): Boolean = try {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        true // не удалось определить — лучше попробовать, чем молча потерять обновление
    }

    /**
     * GET с HTTP-кэшем. Возвращает тело ответа либо null, если сервер ответил 304 (данные не
     * изменились) либо произошла ошибка сети. При успехе обновляет пару (body, etag) в [Store].
     */
    fun get(c: Context, url: String, cacheKey: String): String? {
        if (!online(c)) return null
        val p = Store.p(c)
        val cn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TO; readTimeout = TO; instanceFollowRedirects = true
                useCaches = true
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "ClockWeather/2.0 (App Widget)")
                p.getString("etag:$cacheKey", null)?.let { setRequestProperty("If-None-Match", it) }
            }
        } catch (_: Exception) {
            return null
        }
        try {
            val code = cn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return null
            if (code in 300..399) return null
            if (code >= 400) return null
            val body = cn.inputStream.bufferedReader().use { it.readText() }
            val et = cn.getHeaderField("ETag")
            if (!et.isNullOrEmpty()) p.edit().putString("etag:$cacheKey", et).apply()
            p.edit().putString("http:$cacheKey", body).apply()
            return body
        } catch (_: Exception) {
            return null
        } finally {
            // намеренно БЕЗ disconnect(): сокет возвращается в keep-alive пул, следующий
            // запрос (геокодер + прогноз подряд) идёт без повторного TCP/TLS handshake
            cn.inputStream?.close()
        }
    }

    /** Тело последнего успешного ответа из локального кэша (для офлайн-отрисовки). */
    fun cached(c: Context, cacheKey: String): String? = Store.p(c).getString("http:$cacheKey", null)
}
