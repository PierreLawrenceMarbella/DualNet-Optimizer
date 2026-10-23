package com.dualnet.optimizer.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import com.dualnet.optimizer.utils.periodStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageRepository(private val context: Context) {
    fun hasAccess(): Boolean = context.getSystemService(AppOpsManager::class.java).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    @Suppress("DEPRECATION")
    suspend fun load(period: UsagePeriod): UsageState = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext UsageState()
        val manager = context.getSystemService(NetworkStatsManager::class.java)
        val start = periodStart(period); val end = System.currentTimeMillis()
        val perUid = mutableMapOf<Int, Long>()
        var attributionIncomplete = false
        fun read(type: Int): Long? {
            val total = runCatching { manager.querySummaryForDevice(type, null, start, end).let { it.rxBytes + it.txBytes } }.getOrNull()
            try {
                val stats = manager.querySummary(type, null, start, end)
                try {
                    val bucket = NetworkStats.Bucket()
                    while (stats.hasNextBucket()) { stats.getNextBucket(bucket); perUid[bucket.uid] = (perUid[bucket.uid] ?: 0) + bucket.rxBytes + bucket.txBytes }
                } finally { stats.close() }
            } catch (_: Exception) { attributionIncomplete = true }
            return total
        }
        val wifi = read(ConnectivityManager.TYPE_WIFI); val mobile = read(ConnectivityManager.TYPE_MOBILE)
        val apps = perUid.filterValues { it > 0 }.map { (uid, amount) ->
            val packages = context.packageManager.getPackagesForUid(uid)
            val pkg = packages?.firstOrNull()
            val name = pkg?.let { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull() }
            AppUsage(uid, if (packages != null && packages.size > 1) "${name ?: "Apps"} (shared UID)" else name ?: "System / UID $uid", pkg, amount)
        }.sortedByDescending { it.bytes }
        UsageState(wifi, mobile, apps, message = when {
            wifi == null && mobile == null -> "Android did not provide usage totals. Check Usage access and your device's restrictions."
            attributionIncomplete -> "Some app attribution is unavailable. Android statistics are delayed and may include shared UIDs."
            else -> "Android accounting can lag by hours. App percentages use attributed bytes; shared UIDs and hidden system apps cannot be separated. Top 12 apps shown."
        })
    }
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0).map { InstalledApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }
}
