package com.pengxh.daily.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pengxh.daily.app.service.ForegroundRunningService
import com.pengxh.kt.lite.utils.SaveKeyValues
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskResetReceiver : BroadcastReceiver() {

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    override fun onReceive(context: Context, intent: Intent?) {
        if (hasResetToday()) {
            LogFileManager.writeLog("今天已经执行过重置，跳过")
            return
        }

        val autoStart = SaveKeyValues.getValue(Constant.TASK_AUTO_START_KEY, true) as Boolean
        markTodayAsReset()

        if (autoStart) {
            startResetTaskService(context)
        }

        // 重新注册明天同一时刻的 Alarm（循环触发）
        AlarmScheduler.schedule(context, ResetTime.getMinutes())
    }

    private fun hasResetToday(): Boolean {
        val today = dateFormat.format(Date())
        val lastResetDate = SaveKeyValues.getValue(Constant.LAST_RESET_DATE_KEY, "") as String
        return today == lastResetDate
    }

    private fun markTodayAsReset() {
        val today = dateFormat.format(Date())
        SaveKeyValues.putValue(Constant.LAST_RESET_DATE_KEY, today)
        // 每日重置时清掉运行状态，防止第二天打开 app 还显示"停止"
        SaveKeyValues.putValue(Constant.TASK_RUNNING_STATE_KEY, false)
        LogFileManager.writeLog("标记 $today 已重置")
    }

    private fun startResetTaskService(context: Context) {
        val serviceIntent = Intent(context, ForegroundRunningService::class.java).apply {
            action = ForegroundRunningService.ACTION_RESET_DAILY_TASK
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
