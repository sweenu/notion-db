package com.notiondb.widgets.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.notiondb.widgets.App
import kotlinx.coroutines.launch

/**
 * Catches the OAuth redirect (notiondbwidgets://oauth?code=…) after the user
 * authorizes in the browser, exchanges the code via the Worker, then returns to
 * [MainActivity]. Registered with a BROWSABLE intent filter in the manifest.
 */
class OAuthRedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val code = intent?.data?.getQueryParameter("code")
        val error = intent?.data?.getQueryParameter("error")
        val oauth = (application as App).container.oauthManager

        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Notion sign-in cancelled${error?.let { ": $it" } ?: ""}", Toast.LENGTH_LONG).show()
            goHome()
            return
        }

        lifecycleScope.launch {
            val result = oauth.exchangeCode(code)
            val message = result.fold(
                onSuccess = { "Connected to $it 🎉" },
                onFailure = { "Sign-in failed: ${it.message}" },
            )
            Toast.makeText(this@OAuthRedirectActivity, message, Toast.LENGTH_LONG).show()
            goHome()
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
