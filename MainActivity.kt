package com.nova.browser

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class MainActivity : Activity() {
    private val bg = Color.rgb(6,16,34)
    private val panel = Color.rgb(13,27,52)
    private val blue = Color.rgb(41,152,255)
    private val white = Color.rgb(238,245,255)
    private val muted = Color.rgb(167,186,216)
    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var address: EditText
    private lateinit var tabsButton: Button
    private val webViews = mutableListOf<WebView>()
    private val tabTitles = mutableListOf<String>()
    private val tabUrls = mutableListOf<String>()
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("nova", Context.MODE_PRIVATE)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildShell()
        newTab("https://www.google.com")
    }

    private fun rounded(color: Int, radius: Float = 22f, stroke: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color); cornerRadius = radius
            if (stroke != 0) setStroke(1, stroke)
        }
    }

    private fun label(text: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun button(text: String, action: () -> Unit): Button =
        Button(this).apply {
            this.text = text; textSize = 12f; isAllCaps = false
            setTextColor(white); background = rounded(Color.rgb(18,42,75), 18f, Color.rgb(48,80,120))
            setPadding(8, 4, 8, 4); setOnClickListener { action() }
        }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg)
            setPadding(14, 10, 14, 8)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = label("✦  Nova Browser", 21f, white, true)
        header.addView(brand, LinearLayout.LayoutParams(0, 52, 1f))
        tabsButton = button("Tabs (1)") { showTabs() }
        header.addView(tabsButton)
        root.addView(header)

        val subtitle = label("Your web, one launchpad.", 12f, muted)
        root.addView(subtitle)

        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }
        address = EditText(this).apply {
            hint = "Search or enter website"; textSize = 14f
            setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(Color.rgb(15,30,55)); setHintTextColor(Color.GRAY)
            background = rounded(Color.rgb(245,248,255), 18f)
            setPadding(13, 8, 13, 8)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) { navigateFromBar(); true } else false
            }
        }
        searchRow.addView(address, LinearLayout.LayoutParams(0, 50, 1f))
        val go = button("Go ↗") { navigateFromBar() }
        searchRow.addView(go, LinearLayout.LayoutParams(68, 50).apply { leftMargin = 7 })
        root.addView(searchRow)

        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Facebook" to "https://www.facebook.com", "Instagram" to "https://www.instagram.com",
            "YouTube" to "https://www.youtube.com").forEach { (name, url) ->
            shortcuts.addView(button(name) { newTab(url) }, LinearLayout.LayoutParams(0, 44, 1f).apply { rightMargin = 5 })
        }
        root.addView(shortcuts)

        content = FrameLayout(this).apply {
            background = rounded(panel, 22f, Color.rgb(38,61,99))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 14; bottomMargin = 12 })

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(button("⌂ Home") { showHome() }, LinearLayout.LayoutParams(0, 48, 1f))
        nav.addView(button("▣ Tabs") { showTabs() }, LinearLayout.LayoutParams(0, 48, 1f).apply { leftMargin = 5 })
        nav.addView(button("＋ New") { newTab("https://www.google.com") }, LinearLayout.LayoutParams(0, 48, 1f).apply { leftMargin = 5 })
        nav.addView(button("☆ Saved") { showBookmarks() }, LinearLayout.LayoutParams(0, 48, 1f).apply { leftMargin = 5 })
        root.addView(nav)
        setContentView(root)
    }

    private fun navigateFromBar() {
        val raw = address.text.toString().trim()
        if (raw.isEmpty()) return
        val url = when {
            raw.startsWith("https://") || raw.startsWith("http://") -> raw
            raw.contains(".") && !raw.contains(" ") -> "https://$raw"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(raw, "UTF-8")
        }
        if (webViews.isEmpty()) newTab(url) else webViews[current].loadUrl(url)
        showBrowser()
    }

    private fun newTab(url: String) {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                val idx = webViews.indexOf(view)
                if (idx >= 0 && !title.isNullOrBlank()) tabTitles[idx] = title
                updateTabCount()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return uri.scheme != "https" && uri.scheme != "http"
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                val idx = webViews.indexOf(view)
                if (idx >= 0 && url != null) {
                    tabUrls[idx] = url
                    if (idx == current) address.setText(url)
                }
            }
        }
        webViews.add(web); tabTitles.add("New tab"); tabUrls.add(url)
        current = webViews.lastIndex
        content.removeAllViews()
        content.addView(web, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl(url)
        address.setText(url)
        updateTabCount()
    }

    private fun showBrowser() {
        content.removeAllViews()
        if (webViews.isNotEmpty()) {
            content.addView(webViews[current], FrameLayout.LayoutParams(-1, -1))
            address.setText(tabUrls[current])
        }
    }

    private fun updateTabCount() { tabsButton.text = "Tabs (${webViews.size})" }

    private fun showHome() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 20, 16, 16)
        }
        page.addView(label("Welcome to Nova", 25f, white, true))
        page.addView(label("Fast access to your favourite sites.", 13f, muted))
        listOf("Facebook" to "https://www.facebook.com", "Instagram" to "https://www.instagram.com",
            "Google" to "https://www.google.com", "YouTube" to "https://www.youtube.com",
            "ChatGPT" to "https://chatgpt.com").forEach { (name, url) ->
            val b = button("✦   $name     ↗") { newTab(url) }
            page.addView(b, LinearLayout.LayoutParams(-1, 52).apply { topMargin = 10 })
        }
        page.addView(label("Account safety: no browser can guarantee zero suspensions. Use official registration, accurate details and platform verification.", 12f, muted).apply { setPadding(0,18,0,0) })
        content.removeAllViews()
        val scroll = ScrollView(this); scroll.addView(page)
        content.addView(scroll)
    }

    private fun showTabs() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,14,14,14) }
        page.addView(label("Open tabs", 20f, white, true))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        webViews.indices.reversed().forEach { i ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val info = label("${i+1}. ${tabTitles[i]}\n${tabUrls[i]}", 12f, white)
            row.addView(info, LinearLayout.LayoutParams(0, 62, 1f))
            row.addView(button("Open") {
                current = i; showBrowser()
            })
            row.addView(button("×") { closeTab(i) }, LinearLayout.LayoutParams(52, 48).apply { leftMargin = 5 })
            list.addView(row)
        }
        scroll.addView(list); page.addView(scroll)
        content.removeAllViews(); content.addView(page)
    }

    private fun closeTab(i: Int) {
        if (i !in webViews.indices) return
        content.removeView(webViews[i]); webViews[i].stopLoading(); webViews[i].destroy()
        webViews.removeAt(i); tabTitles.removeAt(i); tabUrls.removeAt(i)
        if (webViews.isEmpty()) { newTab("https://www.google.com"); return }
        current = current.coerceAtMost(webViews.lastIndex)
        updateTabCount(); showTabs()
    }

    private fun getBookmarks(): JSONArray = try { JSONArray(prefs.getString("bookmarks", "[]")) } catch (_: Exception) { JSONArray() }

    private fun showBookmarks() {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,14,14,14) }
        page.addView(label("Saved bookmarks", 20f, white, true))
        val name = EditText(this).apply { hint = "Bookmark name"; setTextColor(white); setHintTextColor(muted) }
        val url = EditText(this).apply { hint = "https://example.com"; setTextColor(white); setHintTextColor(muted) }
        page.addView(name); page.addView(url)
        page.addView(button("＋ Save current page") {
            if (webViews.isNotEmpty()) { url.setText(tabUrls[current]); if(name.text.isNullOrBlank()) name.setText(tabTitles[current]) }
        })
        page.addView(button("Save bookmark") {
            val u = url.text.toString().trim()
            if (!u.startsWith("https://") && !u.startsWith("http://")) { Toast.makeText(this,"Enter a valid http/https URL",Toast.LENGTH_SHORT).show(); return@button }
            val arr = getBookmarks(); arr.put(JSONObject().put("name", name.text.toString().ifBlank { u }).put("url", u))
            prefs.edit().putString("bookmarks", arr.toString()).apply()
            Toast.makeText(this,"Bookmark saved",Toast.LENGTH_SHORT).show(); showBookmarks()
        })
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val arr = getBookmarks()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(label(obj.optString("name") + "\n" + obj.optString("url"), 12f, white), LinearLayout.LayoutParams(0, 58, 1f))
            row.addView(button("Open") { newTab(obj.optString("url")) })
            row.addView(button("×") {
                val next = JSONArray()
                for (j in 0 until arr.length()) if (j != i) next.put(arr.get(j))
                prefs.edit().putString("bookmarks", next.toString()).apply(); showBookmarks()
            }, LinearLayout.LayoutParams(48,48))
            list.addView(row)
        }
        scroll.addView(list); page.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        content.removeAllViews(); content.addView(page)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (webViews.isNotEmpty() && webViews[current].canGoBack()) webViews[current].goBack()
        else showHome()
    }

    override fun onDestroy() {
        webViews.forEach { it.stopLoading(); it.destroy() }
        webViews.clear()
        super.onDestroy()
    }
}
