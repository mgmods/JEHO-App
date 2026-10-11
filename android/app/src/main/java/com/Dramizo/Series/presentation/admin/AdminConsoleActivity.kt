package com.Dramizo.Series.presentation.admin

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.Dramizo.Series.data.remote.dto.AuthDtos
import com.Dramizo.Series.presentation.common.ThemedActivity
import com.Dramizo.Series.presentation.common.ContainerProvider
import com.Dramizo.Series.util.StaffRoleHelper

/**
 * Secure in-app entry to the existing server-backed admin dashboard.
 * This activity does not implement admin mutations locally: every operation
 * continues through the dashboard's authenticated /admin API and AdminGuard.
 */
class AdminConsoleActivity : ThemedActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var statusPanel: LinearLayout
    private lateinit var retryButton: Button
    private var loadFailed = false
    private var uploadCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = uploadCallback
        uploadCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val selected = if (result.resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback.onReceiveValue(selected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user: AuthDtos.UserDto? = try {
            ContainerProvider.from(this).sessionManager.user
        } catch (_: Exception) {
            null
        }
        if (!StaffRoleHelper.isSuper(user)) {
            Toast.makeText(this, "هذه الصفحة مخصصة للسوبر أدمن فقط", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 252))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(16), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(Color.rgb(37, 42, 91), Color.rgb(91, 64, 158))
            ).apply { cornerRadius = dp(0).toFloat() }
        }
        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "مركز إدارة JEHO"
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "لوحة السوبر أدمن المركزية"
            textSize = 12f
            setTextColor(Color.rgb(226, 224, 246))
            setPadding(0, dp(3), 0, 0)
        }
        titleGroup.addView(title)
        titleGroup.addView(subtitle)
        header.addView(titleGroup, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = TextView(this).apply {
            text = "إغلاق  ×"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(Color.argb(45, 255, 255, 255), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }
        header.addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(42)))
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(111, 84, 199))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(232, 228, 245))
        }
        root.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
        ))

        statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = rounded(Color.WHITE, dp(20))
            elevation = dp(2).toFloat()
        }
        val panelParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(18), dp(24), dp(18), dp(16))
        }
        status = TextView(this).apply {
            text = "جارٍ الاتصال بلوحة الإدارة الآمنة…"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(66, 71, 97))
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        statusPanel.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        retryButton = Button(this).apply {
            text = "إعادة المحاولة"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(91, 64, 158), dp(12))
            visibility = View.GONE
            setOnClickListener {
                loadFailed = false
                status.text = "جارٍ إعادة الاتصال بلوحة الإدارة…"
                retryButton.visibility = View.GONE
                statusPanel.visibility = View.VISIBLE
                webView.visibility = View.GONE
                statusPanel.visibility = View.VISIBLE
        webView.loadUrl(DASHBOARD_URL)
            }
        }
        statusPanel.addView(retryButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(46)
        ))
        root.addView(statusPanel, panelParams)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.visibility = View.GONE
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == DASHBOARD_HOST) return false
                if (uri.scheme == "http" || uri.scheme == "https" ||
                    uri.scheme == "mailto" || uri.scheme == "tel") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        Toast.makeText(this@AdminConsoleActivity, "تعذر فتح الرابط", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadFailed = false
                statusPanel.visibility = View.VISIBLE
                status.text = "جارٍ تحميل لوحة الإدارة…"
                retryButton.visibility = View.GONE
                progress.visibility = View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame != true) return
                loadFailed = true
                showLoadError()
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                if (request?.isForMainFrame != true) return
                val code = errorResponse?.statusCode ?: 0
                if (code >= 400) {
                    loadFailed = true
                    showLoadError()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                if (!loadFailed) {
                    statusPanel.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    filePicker.launch(intent)
                    true
                } catch (_: Exception) {
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    false
                }
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        webView.loadUrl(DASHBOARD_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        super.onDestroy()
    }

    private fun showLoadError() {
        progress.visibility = View.GONE
        webView.visibility = View.GONE
        statusPanel.visibility = View.VISIBLE
        status.text = "تعذّر فتح لوحة الإدارة. تحقّق من اتصال الإنترنت ثم أعد المحاولة."
        retryButton.visibility = View.VISIBLE
    }

    private fun rounded(color: Int, radiusPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DASHBOARD_HOST = "jeho-own-backend.mgs.blitz.cloud"
        private const val DASHBOARD_URL = "https://jeho-own-backend.mgs.blitz.cloud/admin/"
    }
}
