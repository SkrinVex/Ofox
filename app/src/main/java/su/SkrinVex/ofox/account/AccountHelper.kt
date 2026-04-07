package su.SkrinVex.ofox.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context

const val ACCOUNT_TYPE = "su.SkrinVex.ofox"
const val AUTH_TOKEN_TYPE = "jwt"

fun addOfoxAccount(context: Context, username: String, token: String) {
    val am = AccountManager.get(context)
    val account = Account(username, ACCOUNT_TYPE)
    // Удаляем старый если есть (смена аккаунта)
    am.getAccountsByType(ACCOUNT_TYPE).forEach { am.removeAccountExplicitly(it) }
    am.addAccountExplicitly(account, null, null)
    am.setAuthToken(account, AUTH_TOKEN_TYPE, token)
}

fun removeOfoxAccount(context: Context) {
    val am = AccountManager.get(context)
    am.getAccountsByType(ACCOUNT_TYPE).forEach { am.removeAccountExplicitly(it) }
}
