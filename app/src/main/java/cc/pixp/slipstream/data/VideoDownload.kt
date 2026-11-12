package cc.pixp.slipstream.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

object VideoDownloads {
    fun name(post: String) = "video-$post"
    fun enqueue(context: Context, post: String, title: String, quality: MediaQuality, audio: Boolean) {
        val work=OneTimeWorkRequestBuilder<VideoDownload>()
            .setInputData(workDataOf("url" to quality.url,"title" to title,"audio" to audio))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(post),ExistingWorkPolicy.KEEP,work)
    }
}

/** WorkManager owns the transfer, so navigating away does not cancel a download. */
class VideoDownload(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    private val notifications=applicationContext.getSystemService(NotificationManager::class.java)
    private val notificationId=id.hashCode()
    private val title=inputData.getString("title") ?: "Slipstream video"
    private fun notification(progress: Int, text: String) = NotificationCompat.Builder(applicationContext,"downloads")
        .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(title).setContentText(text)
        .setOnlyAlertOnce(true).setOngoing(true).setProgress(100,progress,progress<0)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Cancel",WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
    private fun foreground(progress: Int, text: String): ForegroundInfo {
        val note=notification(progress,text)
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(notificationId,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId,note)
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        notifications.createNotificationChannel(NotificationChannel("downloads","Downloads",NotificationManager.IMPORTANCE_LOW))
        var destination: Uri?=null
        var legacyFile: File?=null
        try {
            setForeground(foreground(-1,"Preparing download…"))
            val cookie=SessionStore(applicationContext).load() ?: error("Sign in before downloading.")
            val url=inputData.getString("url") ?: error("No download URL.")
            require(Uri.parse(url).scheme=="https")
            val audio=inputData.getBoolean("audio",false)
            val extension=if(audio) "m4a" else "mp4"
            val mime=if(audio) "audio/mp4" else "video/mp4"
            val filename=title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").take(120).ifBlank {"Slipstream"}+"-${id.toString().take(8)}.$extension"
            val resolver=applicationContext.contentResolver
            val stream=if(Build.VERSION.SDK_INT>=29) {
                destination=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,filename);put(MediaStore.MediaColumns.MIME_TYPE,mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Slipstream")
                    put(MediaStore.MediaColumns.IS_PENDING,1)
                }) ?: error("Could not create the download.")
                resolver.openOutputStream(destination!!) ?: error("Could not open the download.")
            } else {
                legacyFile=File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),filename)
                legacyFile!!.outputStream()
            }
            val client=FloatplaneApi(cookie).mediaClient()
            val call=client.newCall(okhttp3.Request.Builder().url(url).header("Referer","https://www.floatplane.com/").build())
            try {
                stream.use { output -> call.execute().use { response ->
                    check(response.isSuccessful) {"Download failed (${response.code}). Try again."}
                    val body=response.body ?: error("Empty download.")
                    val total=body.contentLength();var copied=0L;var lastUpdate=0L
                    body.byteStream().use { input ->
                        val buffer=ByteArray(64*1024)
                        while(true) {
                            coroutineContext.ensureActive()
                            val count=input.read(buffer);if(count<0) break
                            output.write(buffer,0,count);copied+=count
                            val now=System.currentTimeMillis()
                            if(now-lastUpdate>1000) {
                                val progress=if(total>0) (copied*100/total).toInt().coerceIn(0,100) else -1
                                setProgress(workDataOf("progress" to progress))
                                setForeground(foreground(progress,if(progress<0) "Downloading…" else "$progress%"));lastUpdate=now
                            }
                        }
                    }
                } }
            } finally {call.cancel()}
            if(Build.VERSION.SDK_INT>=29) resolver.update(destination!!,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            val done=NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title).setContentText(if(destination!=null) "Saved to Downloads/Slipstream" else "Saved in Slipstream’s download folder").setAutoCancel(true)
            destination?.let {uri -> done.setContentIntent(PendingIntent.getActivity(applicationContext,notificationId,Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))}
            if(Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(applicationContext,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.notify(notificationId+1,done.build())
            Result.success(workDataOf("uri" to (destination?.toString() ?: legacyFile?.absolutePath.orEmpty())))
        } catch(e: Exception) {
            destination?.let {applicationContext.contentResolver.delete(it,null,null)};legacyFile?.delete()
            if(e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(workDataOf("error" to (e.message ?: "Download failed. Try again.")))
        }
    }
}
