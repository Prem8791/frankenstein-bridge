package com.android.systemui.prodx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.prodx.ProdXManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.policy.KeyguardStateController
import java.util.concurrent.Executor
import javax.inject.Inject

/** Owns the process-lifetime SystemUI registration and security cancellation boundaries. */
@SysUISingleton
class ProdXRuntimeStartable @Inject constructor(
    @Application private val context: Context,
    @Main private val mainExecutor: Executor,
    private val userTracker: UserTracker,
    private val keyguardStateController: KeyguardStateController,
    private val renderer: ProdXTrustedConfirmationRenderer,
) : CoreStartable {
    private var manager = ProdXManager(context)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val indicator = ProdXIndicatorController { manager.emergencyDisable() }
    private var bridge = newBridge()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receivingContext: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> cancelForSecurityEvent()
                ACTION_EMERGENCY_STOP -> {
                    cancelForSecurityEvent()
                    indicator.requestEmergencyStop()
                }
            }
        }
    }
    private val userCallback = object : UserTracker.Callback {
        override fun onBeforeUserSwitching(newUser: Int) = cancelForSecurityEvent()
        override fun onUserChanged(newUser: Int, userContext: Context) = registerBridge()
    }
    private val keyguardCallback = object : KeyguardStateController.Callback {
        override fun onUnlockedChanged() {
            if (!keyguardStateController.isUnlocked) cancelForSecurityEvent()
        }

        override fun onKeyguardShowingChanged() {
            if (keyguardStateController.isShowing && !keyguardStateController.isUnlocked) {
                cancelForSecurityEvent()
            }
        }
    }
    private val healthCheck = object : Runnable {
        override fun run() {
            if (!manager.health.isOperational) {
                bridge.securityCancel(notifyAuthority = false)
                registered = false
                manager = ProdXManager(context)
                bridge = newBridge()
            } else if (!registered) {
                registerBridge()
            }
            updateIndicator()
            handler.postDelayed(this, HEALTH_CHECK_MILLIS)
        }
    }

    override fun start() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                context.getString(R.string.prodx_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                addAction(ACTION_EMERGENCY_STOP)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        userTracker.addCallback(userCallback, mainExecutor)
        keyguardStateController.addCallback(keyguardCallback)
        registerBridge()
        handler.post(healthCheck)
    }

    private fun registerBridge() {
        if (registered) return
        registered = bridge.start()
    }

    private fun newBridge(): ProdXConfirmationBridge =
        ProdXConfirmationBridge(manager, mainExecutor, renderer, indicator, ::updateIndicator)

    private fun cancelForSecurityEvent() {
        bridge.securityCancel()
        updateIndicator()
    }

    private fun updateIndicator() {
        if (!indicator.hasActiveOperations()) {
            notificationManager.cancelAsUser(TAG, NOTIFICATION_ID, UserHandle.ALL)
            return
        }
        val stopIntent = Intent(ACTION_EMERGENCY_STOP).setPackage(context.packageName)
        val stop = PendingIntent.getBroadcast(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.prodx_active_title))
            .setContentText(context.getString(R.string.prodx_active_message))
            .setOngoing(true)
            .addAction(0, context.getString(R.string.prodx_emergency_stop), stop)
            .build()
        notificationManager.notifyAsUser(TAG, NOTIFICATION_ID, notification, UserHandle.ALL)
    }

    private companion object {
        const val TAG = "ProdXRuntime"
        const val NOTIFICATION_CHANNEL = "prodx_runtime"
        const val NOTIFICATION_ID = 0x5058
        const val HEALTH_CHECK_MILLIS = 5_000L
        const val ACTION_EMERGENCY_STOP = "com.android.systemui.prodx.EMERGENCY_STOP"
    }
}
