package __APP_ID__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Системные события: загрузка, обновление приложения, смена времени/пояса. */
public class SysReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a);
        Updater.async(c, boot ? Updater.SYSTEM : Updater.TICK, goAsync());
    }
}
