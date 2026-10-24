package com.toxictrace.nexusconnect.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.provider.ContactsContract
import com.toxictrace.nexusconnect.util.AppLogger

/**
 * Lets Android schedule a widget refresh when contact records change,
 * even when the app process and ContactsObserverService are not running.
 */
class ContactsChangeJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val changedUris = params.triggeredContentUris
            ?.joinToString(separator = ",") { it.toString() }
            ?: "<not supplied>"
        AppLogger.i(TAG, "onStartJob: changedUris=$changedUris")
        try {
            PhotoProvider.invalidateCache()
            ContactWidgetProvider.updateAllWidgets(applicationContext)
            AppLogger.i(TAG, "onStartJob: widget refresh completed")
        } catch (e: Exception) {
            AppLogger.e(TAG, "onStartJob: widget refresh failed", e)
        } finally {
            // Content-triggered jobs are one-shot. Re-register after each run
            // so later contact changes are observed as well.
            val widgetIds = AppWidgetManager.getInstance(applicationContext)
                .getAppWidgetIds(ComponentName(applicationContext, ContactWidgetProvider::class.java))
            if (widgetIds.isNotEmpty()) {
                AppLogger.i(TAG, "onStartJob: re-registering content observer job")
                schedule(applicationContext)
            } else {
                AppLogger.i(TAG, "onStartJob: no widgets remain; canceling content observer job")
                cancel(applicationContext)
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        AppLogger.w(TAG, "onStopJob: Android stopped content-change job")
        return true
    }

    companion object {
        private const val TAG = "ContactsChangeJob"
        private const val JOB_ID = 41027

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val component = ComponentName(context, ContactsChangeJobService::class.java)
            val job = JobInfo.Builder(JOB_ID, component)
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        ContactsContract.Contacts.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        ContactsContract.Data.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                .setTriggerContentUpdateDelay(2_000L)
                .setTriggerContentMaxDelay(10_000L)
                .build()
            val result = scheduler.schedule(job)
            AppLogger.i(TAG, "schedule: result=$result jobId=$JOB_ID")
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            AppLogger.i(TAG, "cancel: jobId=$JOB_ID")
        }
    }
}
