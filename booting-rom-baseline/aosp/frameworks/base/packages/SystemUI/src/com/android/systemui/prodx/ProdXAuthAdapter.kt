package com.android.systemui.prodx

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.phone.KeyguardDismissUtil
import com.android.systemui.statusbar.policy.KeyguardStateController
import java.security.SecureRandom
import javax.inject.Inject

/** Produces a short-lived reference only after Android's device-entry policy succeeds. */
interface ProdXAuthAdapter {
    fun authenticate(completion: (ByteArray?) -> Unit)
}

@SysUISingleton
class ProdXDeviceEntryAuthAdapter @Inject constructor(
    private val keyguardDismissUtil: KeyguardDismissUtil,
    private val keyguardStateController: KeyguardStateController,
) : ProdXAuthAdapter {
    private val random = SecureRandom()

    override fun authenticate(completion: (ByteArray?) -> Unit) {
        if (!keyguardStateController.isMethodSecure) {
            completion(null)
            return
        }
        keyguardDismissUtil.executeWhenUnlocked(
            {
                if (!keyguardStateController.isUnlocked) {
                    completion(null)
                } else {
                    completion(ByteArray(AUTH_REFERENCE_BYTES).also(random::nextBytes))
                }
                false
            },
            false,
            true,
        )
    }

    private companion object {
        const val AUTH_REFERENCE_BYTES = 32
    }
}
