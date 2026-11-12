package cc.pixp.slipstream.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.pixp.slipstream.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SessionApp() {
    val context=LocalContext.current
    val store=remember { SessionStore(context) }
    val scope=rememberCoroutineScope()
    var restoring by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf<Account?>(null) }
    var api by remember { mutableStateOf<FloatplaneApi?>(null) }
    var login by remember { mutableStateOf(false) }
    var demo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val cookie=withContext(Dispatchers.IO) { store.load() }
            if(cookie!=null) {
                val client=FloatplaneApi(cookie)
                account=client.account(); api=client
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { error=e.message ?: "Could not restore your session. Try signing in again." }
        finally { restoring=false }
    }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        when {
            restoring -> Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            login -> LoginScreen(onCancel={login=false},onVerified={cookie,user,client ->
                withContext(Dispatchers.IO) { store.save(cookie) }
                account=user;api=client;login=false;error=null
            })
            api!=null && account!=null -> LibraryScreen(api!!,account!!,onSignOut={
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {store.clear()}
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                        context.stopService(android.content.Intent(context,cc.pixp.slipstream.PlaybackService::class.java))
                        api=null;account=null;error=null
                    } catch(e: Exception) {error=e.message}
                }
            },onSessionExpired={api=null;account=null;error="Your session has expired. Sign in again."},sessionError=error)
            demo -> LibraryScreen(null,Account("demo", "Demo"),onSignOut={demo=false},onSessionExpired={},sessionError=null)
            else -> Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
                Column(Modifier.widthIn(max=360.dp).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Glyph(Symbol.Logo,Modifier.size(48.dp),MaterialTheme.colorScheme.primary)
                    Text("Slipstream",style=MaterialTheme.typography.headlineLarge)
                    if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error)
                    Button(onClick={login=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Sign in to Floatplane")}
                    TextButton(onClick={demo=true}) {Text("Try Demo")}
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(onCancel:()->Unit,onVerified:suspend (String,Account,FloatplaneApi)->Unit) {
    val scope=rememberCoroutineScope()
    var checking by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    var webView by remember { mutableStateOf<WebView?>(null) }
    var lastAttempt by remember {mutableStateOf<String?>(null)}
    var lastAttemptAt by remember {mutableLongStateOf(0L)}
    fun verify(manual: Boolean=false) {
        val cookie=LoginPolicy.sessionCookie(CookieManager.getInstance().getCookie("https://www.floatplane.com/"))
        if(cookie==null) {if(manual) error="Complete sign-in on Floatplane first, then try again.";return}
        if(checking || (!manual && cookie==lastAttempt && android.os.SystemClock.elapsedRealtime()-lastAttemptAt<5000)) return
        checking=true;lastAttempt=cookie;lastAttemptAt=android.os.SystemClock.elapsedRealtime();error=null
        scope.launch {
            try { val client=FloatplaneApi(cookie);val user=client.account();onVerified(cookie,user,client) }
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(manual || e !is FloatplaneException || e.status!=401) error=e.message ?: "Could not verify the session. Try again."}
            finally {checking=false}
        }
    }
    LaunchedEffect(Unit) {
        while(true) { delay(1000); verify() }
    }
    BackHandler {if(webView?.canGoBack()==true) webView?.goBack() else onCancel()}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onCancel) {Glyph(Symbol.Close,Modifier.semanticsLabel("Close sign in"))}
            Column(Modifier.weight(1f)) {Text("Sign in to Floatplane",style=MaterialTheme.typography.titleMedium);Text("www.floatplane.com",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
            TextButton(onClick={verify(true)},enabled=!checking) {Text(if(checking) "Verifying…" else "Continue")}
        }
        if(loading || checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error!=null) Surface(color=MaterialTheme.colorScheme.errorContainer) {
            Text(error!!,color=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.fillMaxWidth().padding(16.dp))
        }
        AndroidView(factory={context -> WebView(context).apply {
            webView=this
            layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(android.graphics.Color.rgb(31,46,62))
            webChromeClient=WebChromeClient()
            settings.useWideViewPort=true
            settings.loadWithOverviewMode=true
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
            webViewClient=object: WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    val allowed=LoginPolicy.allows(request.url.toString(),request.isForMainFrame)
                    if(!allowed && request.isForMainFrame) error="That link opens outside the supported sign-in services. Return to Floatplane to continue."
                    return !allowed
                }
                override fun onPageStarted(view:WebView,url:String,favicon:android.graphics.Bitmap?) {loading=true}
                override fun onPageFinished(view:WebView,url:String) {loading=false;verify()}
                override fun onReceivedError(view:WebView,request:WebResourceRequest,webError:WebResourceError) {
                    if(request.isForMainFrame) {loading=false;error="The sign-in page could not load. Check your connection and retry."}
                }
            }
            loadUrl("https://www.floatplane.com/login")
        } },modifier=Modifier.fillMaxWidth().weight(1f),onRelease={it.stopLoading();it.destroy();webView=null;CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }})
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Credentials stay on Floatplane.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            TextButton(onClick={error=null;webView?.reload()}) {Text("Reload")}
        }
    }
}
