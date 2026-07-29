package com.android.systemui.prodx

import android.content.Context
import android.content.DialogInterface
import android.view.WindowManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import java.security.MessageDigest
import javax.inject.Inject

/** SystemUI-owned confirmation UI. Opaque challenge content is never rendered as text. */
@SysUISingleton
class ProdXTrustedConfirmationRenderer @Inject constructor(
    @Application private val context: Context,
    private val dialogFactory: SystemUIDialog.Factory,
    private val authAdapter: ProdXAuthAdapter,
) : ProdXConfirmationBridge.Renderer {
    private var dialog: SystemUIDialog? = null
    private var completed = false

    override fun show(
        canonicalChallenge: ByteArray,
        completion: (approved: Boolean, proof: ByteArray?) -> Unit,
    ) {
        dismiss()
        completed = false
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(canonicalChallenge)
            .take(FINGERPRINT_BYTES)
            .joinToString("") { "%02X".format(it) }
        val shown = dialogFactory.create().also { created ->
            val pendingClickListener = DialogInterface.OnClickListener { _, _ -> }
            created.setTitle(R.string.prodx_confirmation_title)
            created.setMessage(context.getString(R.string.prodx_confirmation_message, fingerprint))
            created.setCanceledOnTouchOutside(false)
            created.setButton(
                DialogInterface.BUTTON_POSITIVE,
                context.getString(R.string.prodx_allow),
                pendingClickListener,
            )
            created.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                context.getString(R.string.prodx_deny),
                pendingClickListener,
            )
            created.setOnShowListener {
                created.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    authenticateAndComplete(true, completion)
                }
                created.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                    authenticateAndComplete(false, completion)
                }
            }
            created.setOnCancelListener { completeOnce(false, null, completion) }
            created.setOnDismissListener { completeOnce(false, null, completion) }
            created.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            created.window?.setHideOverlayWindows(true)
            created.window?.decorView?.filterTouchesWhenObscured = true
        }
        dialog = shown
        shown.show()
    }

    override fun dismiss() {
        completed = true
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun authenticateAndComplete(
        approved: Boolean,
        completion: (Boolean, ByteArray?) -> Unit,
    ) {
        authAdapter.authenticate { reference -> completeOnce(approved, reference, completion) }
    }

    private fun completeOnce(
        approved: Boolean,
        proof: ByteArray?,
        completion: (Boolean, ByteArray?) -> Unit,
    ) {
        if (completed) return
        completed = true
        dialog = null
        completion(approved, proof)
    }

    private companion object {
        const val FINGERPRINT_BYTES = 6
    }
}
