package cc.pixp.slipstream

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cc.pixp.slipstream.data.FloatplaneCookies
import cc.pixp.slipstream.data.SessionStore

/** Owns the player independently of the activity, including its media notification. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService: MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val client=okhttp3.OkHttpClient.Builder().followSslRedirects(false).cookieJar(object: okhttp3.CookieJar {
            override fun saveFromResponse(url:okhttp3.HttpUrl,cookies:List<okhttp3.Cookie>) = Unit
            override fun loadForRequest(url:okhttp3.HttpUrl):List<okhttp3.Cookie> {
                if(!url.isHttps || url.host!="www.floatplane.com") return emptyList()
                return SessionStore(applicationContext).load()?.let {FloatplaneCookies(it).loadForRequest(url)} ?: emptyList()
            }
        }).build()
        val http=androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.floatplane.com/"))
        val sources=androidx.media3.datasource.DefaultDataSource.Factory(this,http)
        val player=ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(sources))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
            .setHandleAudioBecomingNoisy(true).setSeekBackIncrementMs(15_000).setSeekForwardIncrementMs(15_000).build()
        val activity=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session=MediaSession.Builder(this,player).setSessionActivity(activity).build()
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        session?.run {player.release();release()};session=null
        super.onDestroy()
    }
}
