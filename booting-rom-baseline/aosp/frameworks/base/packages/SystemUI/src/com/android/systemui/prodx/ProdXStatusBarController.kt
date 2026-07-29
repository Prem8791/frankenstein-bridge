package com.android.systemui.prodx

class ProdXStatusBarController(
    private val indicator: ProdXIndicatorController = ProdXIndicatorController(),
) {
    fun getStatusIcon(): String = if (indicator.hasActiveOperations()) "prodx_active" else ""
}
