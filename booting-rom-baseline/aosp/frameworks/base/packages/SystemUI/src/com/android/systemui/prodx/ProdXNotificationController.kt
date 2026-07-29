package com.android.systemui.prodx

class ProdXNotificationController(
    private val indicator: ProdXIndicatorController = ProdXIndicatorController(),
) {
    fun getPendingNotifications(): Int = indicator.activeOperationCount()
}
