package app.clockweather

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Один раз в сутки, пакетно с системой (без будильников и без пробуждения устройства по таймеру). */
class RefreshJob : JobService() {
    override fun onStartJob(p: JobParameters): Boolean {
        val c = applicationContext
        Thread { try { Refresh.run(c, true) } finally { jobFinished(p, false) } }.start()
        return true
    }

    override fun onStopJob(p: JobParameters) = true

    companion object {
        const val ID = 1
        fun schedule(c: Context) {
            val js = c.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(ID) != null) return
            js.schedule(JobInfo.Builder(ID, ComponentName(c, RefreshJob::class.java))
                .setPeriodic(24 * 3_600_000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true).build())
        }
    }
}
