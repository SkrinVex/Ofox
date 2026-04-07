package su.SkrinVex.ofox.account

import android.app.Service
import android.content.Intent
import android.os.IBinder

class OfoxAuthenticatorService : Service() {
    private val authenticator by lazy { OfoxAuthenticator(this) }
    override fun onBind(intent: Intent): IBinder = authenticator.iBinder
}
