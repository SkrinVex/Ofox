package su.SkrinVex.ofox.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import su.SkrinVex.ofox.MainActivity

class OfoxAuthenticator(private val context: Context) : AbstractAccountAuthenticator(context) {

    override fun addAccount(
        response: AccountAuthenticatorResponse, accountType: String, authTokenType: String?,
        requiredFeatures: Array<out String>?, options: Bundle?
    ): Bundle {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        }
        return Bundle().apply { putParcelable(AccountManager.KEY_INTENT, intent) }
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle?
    ): Bundle {
        val am = AccountManager.get(context)
        val token = am.peekAuthToken(account, authTokenType)
        return Bundle().apply {
            putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            putString(AccountManager.KEY_AUTHTOKEN, token)
        }
    }

    override fun confirmCredentials(r: AccountAuthenticatorResponse, a: Account, b: Bundle?) = null
    override fun editProperties(r: AccountAuthenticatorResponse, t: String) = Bundle()
    override fun getAuthTokenLabel(authTokenType: String) = authTokenType
    override fun updateCredentials(r: AccountAuthenticatorResponse, a: Account, t: String?, b: Bundle?) = null
    override fun hasFeatures(r: AccountAuthenticatorResponse, a: Account, f: Array<out String>) =
        Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}
