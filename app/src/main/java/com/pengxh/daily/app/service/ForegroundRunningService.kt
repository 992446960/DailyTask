package com.pengxh.daily.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.pengxh.daily.app.R
import com.pengxh.daily.app.ui.MainActivity
import com.pengxh.daily.app.utils.AlarmScheduler
import com.pengxh.daily.app.utils.ApplicationEvent
import com.pengxh.daily.app.utils.ChinaHolidayRemoteUpdater
import com.pengxh.daily.app.utils.Constant
import com.pengxh.daily.app.utils.EmailManager
import com.pengxh.daily.app.utils.HttpRequestManager
import com.pengxh.daily.app.utils.LogFileManager
import com.pengxh.daily.app.utils.ResetTime
import com.pengxh.daily.app.utils.TimeKit
import com.pengxh.kt.lite.utils.SaveKeyValues
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Calendar
import java.util.Locale

/**
 * APP前台服务，降低APP被系统杀死的可能性
 * */
class ForegroundRunningService : Service() {

    companion object {
        const val ACTION_RESET_DAILY_TASK = "com.pengxh.daily.app.RESET_DAILY_TASK"
    }

    private val batteryManager by lazy { getSystemService(BatteryManager::class.java) }
    private val httpRequestManager by lazy { HttpRequestManager(this) }
    private val emailManager by lazy { EmailManager(this) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var lastRemindTime = 0L
    private val resetTickerRunnable = object : Runnable {
        override fun run() {
            checkMissedDailyReset()
            updateResetTimeView()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        EventBus.getDefault().register(this)

        val notificationManager = getSystemService(NotificationManager::class.java)
        val name = "${resources.getString(R.string.app_name)}前台服务"
        val channel = NotificationChannel(
            "foreground_running_service_channel", name, NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Channel for Foreground Running Service"
        }
        notificationManager.createNotificationChannel(channel)
        val notificationBuilder =
            NotificationCompat.Builder(this, "foreground_running_service_channel").apply {
                setSmallIcon(R.mipmap.ic_launcher)
                setContentText("为保证程序正常运行，请勿移除此通知")
                setPriority(NotificationCompat.PRIORITY_LOW) // 设置通知优先级
                setOngoing(true)
                setOnlyAlertOnce(true)
                setSilent(true)
                setCategory(NotificationCompat.CATEGORY_SERVICE)
                setShowWhen(true)
                setSound(null) // 禁用声音
                setVibrate(null) // 禁用振动
            }
        val notification = notificationBuilder.build()
        startForeground(Constant.FOREGROUND_RUNNING_SERVICE_NOTIFICATION_ID, notification)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK) // 每分钟广播
            addAction(Intent.ACTION_BATTERY_CHANGED) // 电池状态改变广播
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemBroadcastReceiver, filter)
        }

        mainHandler.post(resetTickerRunnable)

        // 每次 Service 启动时重新注册 Alarm
        AlarmScheduler.schedule(this, ResetTime.getMinutes())

        ChinaHolidayRemoteUpdater.refreshIfNeeded(this)

        // 检查电量
        checkLowBattery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET_DAILY_TASK) {
            startResetDailyTask()
        }
        checkLowBattery()
        return START_STICKY
    }

    private fun startResetDailyTask() {
        LogFileManager.writeLog("定时刷新每日任务，通过主界面自动启动任务")
        EventBus.getDefault().postSticky(ApplicationEvent.ResetDailyTask)
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(this)
        }
    }

    private fun checkMissedDailyReset() {
        val resetMinutes = ResetTime.getMinutes()
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(Calendar.MINUTE)
        if (currentMinutes < resetMinutes || hasResetToday()) {
            return
        }

        markTodayAsReset()
        AlarmScheduler.schedule(this, resetMinutes)

        val autoStart = SaveKeyValues.getValue(Constant.TASK_AUTO_START_KEY, true) as Boolean
        if (!autoStart) {
            LogFileManager.writeLog("定时刷新每日任务，自动循环已关闭")
            return
        }

        startResetDailyTask()
    }

    private fun hasResetToday(): Boolean {
        val lastResetDate = SaveKeyValues.getValue(Constant.LAST_RESET_DATE_KEY, "") as String
        return lastResetDate == TimeKit.getTodayDate()
    }

    private fun markTodayAsReset() {
        val today = TimeKit.getTodayDate()
        SaveKeyValues.putValue(Constant.LAST_RESET_DATE_KEY, today)
        SaveKeyValues.putValue(Constant.TASK_RUNNING_STATE_KEY, false)
        LogFileManager.writeLog("标记 $today 已重置")
    }

    private val systemBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let {
                when (it) {
                    Intent.ACTION_TIME_TICK -> updateResetTimeView()

                    Intent.ACTION_BATTERY_CHANGED -> checkLowBattery()
                }
            }
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun handleApplicationEvent(event: ApplicationEvent) {
        if (event is ApplicationEvent.SetResetTaskTime) {
            // 重新计算并更新倒计时显示
            updateResetTimeView()
        }
    }

    private fun updateResetTimeView() {
        val seconds = resetTaskSeconds(ResetTime.getMinutes())

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        val time = String.format(
            Locale.getDefault(),
            "%02d小时%02d分钟%02d秒",
            hours,
            minutes,
            remainingSeconds
        )
        EventBus.getDefault().post(ApplicationEvent.UpdateResetTickTime("${time}后刷新每日任务"))
    }

    private fun resetTaskSeconds(minutes: Int): Int {
        val calendar = Calendar.getInstance()
        val safeMinutes = minutes.coerceIn(0, 1439)
        val hour = safeMinutes / 60
        val minute = safeMinutes % 60

        val todayTargetMillis = calendar.clone() as Calendar
        todayTargetMillis.set(Calendar.HOUR_OF_DAY, hour)
        todayTargetMillis.set(Calendar.MINUTE, minute)
        todayTargetMillis.set(Calendar.SECOND, 0)
        todayTargetMillis.set(Calendar.MILLISECOND, 0)

        val targetMillis = if (todayTargetMillis.timeInMillis <= System.currentTimeMillis()) {
            todayTargetMillis.add(Calendar.DATE, 1)
            todayTargetMillis.timeInMillis
        } else {
            todayTargetMillis.timeInMillis
        }

        val delta = (targetMillis - System.currentTimeMillis()) / 1000
        return delta.toInt()
    }

    private fun checkLowBattery() {
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (battery < 20) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRemindTime < 5 * 60 * 1000) {
                return
            }

            when (SaveKeyValues.getValue(Constant.CHANNEL_TYPE_KEY, 0) as Int) {
                0 -> httpRequestManager.sendMessage("低电量提醒", "")
                1 -> emailManager.sendEmail("低电量提醒", "", false)
                else -> LogFileManager.writeLog("低电量提醒未发送，消息渠道未配置，当前电量：$battery%")
            }
            lastRemindTime = currentTime
        } else {
            // 电量恢复到20%以上，重置提醒时间
            lastRemindTime = 0L
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(systemBroadcastReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        EventBus.getDefault().unregister(this)
        mainHandler.removeCallbacks(resetTickerRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
