package cc.pixp.slipstream.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class FloatplaneException(val status: Int, message: String): Exception(message)

class FloatplaneApi(private val cookie: String) {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun mediaDataSourceFactory(): androidx.media3.datasource.DataSource.Factory {
        return androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaClient())
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.floatplane.com/"))
    }

    internal fun mediaClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .followSslRedirects(false).cookieJar(FloatplaneCookies(cookie)).build()

    private suspend fun request(path: String, vararg query: Pair<String,String>, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val params = query.joinToString("&") { (key,value) -> "$key=${URLEncoder.encode(value,"UTF-8")}" }
        val connection = URL("https://www.floatplane.com$path${if(params.isEmpty()) "" else "?$params"}").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false
            connection.connectTimeout=20_000; connection.readTimeout=20_000
            connection.setRequestProperty("Cookie", "sails.sid=$cookie")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Referer", "https://www.floatplane.com/")
            if(body!=null) {
                connection.requestMethod="POST"; connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json")
                connection.setRequestProperty("Origin","https://www.floatplane.com")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            when(val status=connection.responseCode) {
                in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                401 -> throw FloatplaneException(status,"Your session has expired. Sign in again.")
                403 -> throw FloatplaneException(status,"Floatplane denied access. Check your subscription or try signing in again.")
                429 -> throw FloatplaneException(status,"Too many requests. Please try again shortly.")
                else -> throw FloatplaneException(status,"Floatplane returned an error ($status). Please try again.")
            }
        } finally { connection.disconnect() }
    }
    suspend fun account(): Account = JSONObject(request("/api/v3/user/self")).let {
        Account(it.getString("id"),it.optString("displayName").ifBlank { it.getString("username") },it.optJSONObject("profileImage")?.optString("path"))
    }
    suspend fun creators(): List<Creator> {
        val subscriptions = JSONArray(request("/api/v3/user/subscriptions"))
        val ids = (0 until subscriptions.length()).map { subscriptions.getJSONObject(it).getString("creator") }.distinct()
        return ids.map { id -> JSONObject(request("/api/v3/creator/info", "id" to id)).let {
            Creator(it.getString("id"),it.getString("title"),it.getString("title").take(1),it.optJSONObject("icon")?.optString("path"),it.optJSONObject("cover")?.optString("path"))
        } }.sortedBy { it.name }
    }
    suspend fun posts(creator: Creator, query: String="", offset: Int=0, channel: String?=null): List<FeedPost> {
        val params=mutableListOf("id" to creator.id,"limit" to "20","fetchAfter" to offset.toString(),"search" to query)
        if(channel!=null) params += "channel" to channel
        val array = JSONArray(request("/api/v3/content/creator", *params.toTypedArray()))
        return (0 until array.length()).map { index -> array.getJSONObject(index).let {
            val seconds = it.optJSONObject("metadata")?.optDouble("videoDuration",0.0)?.toInt() ?: 0
            FeedPost(it.getString("id"),it.getString("title"),creator,it.optString("text"),if(seconds>0) if(seconds>=3600) "${seconds/3600}:${(seconds/60%60).toString().padStart(2,'0')}:${(seconds%60).toString().padStart(2,'0')}" else "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}" else "",
                it.optJSONObject("thumbnail")?.optString("path"),it.optString("releaseDate"),
                it.optJSONObject("channel")?.optString("id") ?: it.optString("channel").takeIf { value -> value.isNotBlank() && value!="null" },
                it.optJSONObject("channel")?.optString("title"),it.optBoolean("isAccessible",true))
        } }
    }
    suspend fun channels(creators: List<Creator>): List<CreatorChannel> {
        if(creators.isEmpty()) return emptyList()
        val array=JSONArray(request("/api/v3/creator/channels/list", *creators.map { "ids" to it.id }.toTypedArray()))
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            CreatorChannel(it.getString("id"),it.getString("creator"),it.getString("title"),
                it.optJSONObject("icon")?.optString("path"),it.optJSONObject("cover")?.optString("path"),it.optInt("order"))
        }.sortedBy { it.order }
    }
    suspend fun detail(post: String): PostDetail = parseDetail(JSONObject(request("/api/v3/content/post", "id" to post)))

    suspend fun qualities(media: String, download: Boolean = false): List<MediaQuality> = parseQualities(JSONObject(request(
        "/api/v3/delivery/info", "scenario" to if(download) "download" else "onDemand", "entityId" to media,
        "outputKind" to if(download) "flat" else "hls.fmp4"
    )))

    suspend fun react(post: String, dislike: Boolean): Set<String> = JSONArray(request(
        if(dislike) "/api/v3/content/dislike" else "/api/v3/content/like",
        body=JSONObject(mapOf("contentType" to "blogPost", "id" to post))
    ).let { if(it.trim()=="null") "[]" else it }).strings().toSet()

    suspend fun comments(post: String, after: String? = null, parent: String? = null): List<PostComment> {
        val params=mutableListOf("blogPost" to post,"limit" to "20")
        if(parent!=null) {params += "comment" to parent; params += "rid" to (after ?: parent)}
        else if(after!=null) params += "fetchAfter" to after
        val array=JSONArray(request(if(parent==null) "/api/v3/comment" else "/api/v3/comment/replies",*params.toTypedArray()))
        return (0 until array.length()).map {parseComment(array.getJSONObject(it))}
    }
    suspend fun addComment(post: String, text: String): PostComment = parseComment(JSONObject(request(
        "/api/v3/comment",body=JSONObject(mapOf("blogPost" to post,"text" to text))
    )))
    suspend fun likeComment(post: String, comment: String): Boolean = request("/api/v3/comment/like",
        body=JSONObject(mapOf("blogPost" to post,"comment" to comment))).trim().trim('"')=="like"

}

object LoginPolicy {
    private val hosts = listOf("floatplane.com", "discord.com", "linustechtips.com", "linusmediagroup.com", "challenges.cloudflare.com")
    fun allows(url: String, mainFrame: Boolean): Boolean = runCatching {
        val uri=URI(url)
        if(!mainFrame && url in listOf("about:blank","about:srcdoc")) true
        else uri.scheme=="https" && uri.host!=null && (!mainFrame || hosts.any { uri.host==it || uri.host.endsWith(".$it") })
    }.getOrDefault(false)
    fun sessionCookie(header: String?): String? = header?.split(';')?.map { it.trim() }
        ?.firstOrNull { it.startsWith("sails.sid=") }?.substringAfter('=')?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }
}

/** CookieJar reapplies cookies per redirect target; credentials never follow CDN requests. */
internal class FloatplaneCookies(private val cookie: String): okhttp3.CookieJar {
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) = Unit
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
        if(url.isHttps && url.host == "www.floatplane.com") listOf(
            okhttp3.Cookie.Builder().name("sails.sid").value(cookie).hostOnlyDomain("www.floatplane.com").path("/").secure().httpOnly().build()
        ) else emptyList()
}

internal fun JSONArray.strings(): List<String> = (0 until length()).map {getString(it)}

internal fun parseDetail(json: JSONObject): PostDetail {
    val media=listOf("video" to "videoAttachments","audio" to "audioAttachments").flatMap { (kind,key) ->
        val array=json.optJSONArray(key) ?: JSONArray()
        (0 until array.length()).map { index ->
            val item=array.get(index)
            if(item is JSONObject) MediaAttachment(item.getString("id"),item.optString("title","Attachment"),kind,item.optBoolean("isAccessible",true))
            else MediaAttachment(item.toString(),"Attachment",kind,true)
        }
    }
    val order=json.optJSONArray("attachmentOrder")?.strings().orEmpty()
    val interactions=json.optJSONArray("userInteraction")?.strings().orEmpty()
    return PostDetail(json.optString("text").takeUnless {it=="null"}.orEmpty(),media.sortedBy {order.indexOf(it.id).takeIf {it>=0} ?: Int.MAX_VALUE},
        Reactions(json.optInt("likes"),json.optInt("dislikes"),"like" in interactions,"dislike" in interactions),json.optInt("comments"))
}

internal fun parseQualities(json: JSONObject): List<MediaQuality> {
    val groups=json.getJSONArray("groups")
    return (0 until groups.length()).flatMap { groupIndex ->
        val group=groups.getJSONObject(groupIndex);val variants=group.getJSONArray("variants")
        (0 until variants.length()).mapNotNull { index ->
            val variant=variants.getJSONObject(index)
            if(!variant.optBoolean("enabled") || variant.optBoolean("hidden")) return@mapNotNull null
            val origin=(variant.optJSONArray("origins") ?: group.optJSONArray("origins"))?.optJSONObject(0)?.optString("url") ?: "https://www.floatplane.com"
            val url=runCatching { URI(origin).resolve(variant.getString("url")) }.getOrNull() ?: return@mapNotNull null
            if(url.scheme!="https" || url.host==null) return@mapNotNull null
            MediaQuality("$groupIndex-${variant.optString("name")}",variant.optString("label"),url.toString(),variant.optInt("order"))
        }
    }.sortedByDescending {it.order}
}

internal fun parseComment(json: JSONObject): PostComment {
    val user=json.getJSONObject("user");val replies=json.optJSONArray("replies") ?: JSONArray()
    return PostComment(json.getString("id"),json.getString("text"),user.getString("username"),user.optJSONObject("profileImage")?.optString("path"),
        json.optString("postDate"),json.optInt("likes"),json.optJSONArray("userInteraction")?.strings()?.contains("like")==true,
        json.optInt("totalReplies"),(0 until replies.length()).map {parseComment(replies.getJSONObject(it))})
}
