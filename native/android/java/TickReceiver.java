package __APP_ID__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Не экспортируется: тик от AlarmManager и кнопка обновления на виджете. */
public class TickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int mode = Updater.ACT_REFRESH.equals(i.getAction()) ? Updater.FORCE : Updater.TICK;
        Updater.async(c, mode, goAsync());
    }
}
