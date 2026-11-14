@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package cc.pixp.slipstream.ui

import android.Manifest
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cc.pixp.slipstream.PlaybackService
import cc.pixp.slipstream.LocalPipMode
import cc.pixp.slipstream.playbackActivity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import androidx.work.WorkManager
import androidx.work.WorkInfo
import cc.pixp.slipstream.R
import cc.pixp.slipstream.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlaybackScreen(post: FeedPost, onClose:()->Unit, api: FloatplaneApi?) {
    val context=LocalContext.current
    var controller by remember {mutableStateOf<MediaController?>(null)}
    var connectionError by remember {mutableStateOf<String?>(null)}
    DisposableEffect(context) {
        val future=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync()
        future.addListener({try {controller=future.get()} catch(e:Exception){connectionError="Could not connect to playback. Reopen the video to try again."}},ContextCompat.getMainExecutor(context))
        onDispose {MediaController.releaseFuture(future)}
    }
    val player=controller
    if(player==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        if(connectionError==null) CircularProgressIndicator() else Column {Text(connectionError!!);TextButton(onClick=onClose){Text("Back")}}
    } else ConnectedPlayer(post, {
        player.stop();player.clearMediaItems();onClose()
    },api,player)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ConnectedPlayer(post:FeedPost,onClose:()->Unit,api:FloatplaneApi?,player:Player) {
    val context=LocalContext.current
    val activity=context.playbackActivity()
    val inPip=LocalPipMode.current
    val fullscreen=cc.pixp.slipstream.LocalPlayerFullscreen.current
    BackHandler(enabled=!inPip) {if(fullscreen) activity?.setPlayerFullscreen(false) else onClose()}
    DisposableEffect(activity) {onDispose {activity?.setPlayerFullscreen(false)}}
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    var detail by remember(post.id) {mutableStateOf<PostDetail?>(null)}
    var qualities by remember {mutableStateOf<List<MediaQuality>>(emptyList())}
    var mediaId by rememberSaveable(post.id) {mutableStateOf<String?>(null)}
    var qualityId by rememberSaveable(post.id) {mutableStateOf<String?>(null)}
    var liveQuality by rememberSaveable(post.id) {mutableStateOf("auto")}
    var tracks by remember(player) {mutableStateOf(player.currentTracks)}
    var loading by remember {mutableStateOf(api!=null)}
    var error by remember {mutableStateOf<String?>(null)}
    var actionError by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    var busyVote by remember {mutableStateOf(false)}
    var qualityMenu by remember {mutableStateOf(false)}
    var downloadMenu by remember {mutableStateOf(false)}
    var preparingDownload by remember {mutableStateOf(false)}
    var downloadQualities by remember {mutableStateOf<List<MediaQuality>>(emptyList())}
    var buffering by remember(player) {mutableStateOf(player.playbackState==Player.STATE_BUFFERING)}
    var ready by remember(player) {mutableStateOf(player.playbackState==Player.STATE_READY)}
    var controlsVisible by remember(post.id,player) {mutableStateOf(true)}
    val workManager=remember {WorkManager.getInstance(context)}
    val downloads by remember(post.id) {workManager.getWorkInfosForUniqueWorkFlow(VideoDownloads.name(post.id))}.collectAsState(initial=emptyList())
    val download=downloads.firstOrNull()
    val downloading=download?.state in listOf(WorkInfo.State.ENQUEUED,WorkInfo.State.RUNNING,WorkInfo.State.BLOCKED)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val isVideo=rememberUpdatedState(api==null || post.live || detail?.media?.find{it.id==mediaId}?.kind=="video")
    LaunchedEffect(isVideo.value) {activity?.updatePip(shouldAutoPip(isVideo.value,player.playWhenReady,player.playbackState))}
    DisposableEffect(player,activity) {
        fun updatePip() {activity?.updatePip(shouldAutoPip(isVideo.value,player.playWhenReady,player.playbackState))}
        val listener=object:Player.Listener {override fun onEvents(player:Player,events:Player.Events) {updatePip()}}
        player.addListener(listener);updatePip()
        onDispose {player.removeListener(listener);activity?.updatePip(false,null)}
    }
    DisposableEffect(player,owner) {
        val listener=object: Player.Listener {
            override fun onPlaybackStateChanged(state:Int) {buffering=state==Player.STATE_BUFFERING; if(state==Player.STATE_READY) ready=true}
            override fun onTracksChanged(next:Tracks) {tracks=next}
            override fun onPlayerError(exception:androidx.media3.common.PlaybackException) {
                // Live playback resumed after a long pause falls outside the playlist window; rejoin at the live edge.
                if(exception.errorCode==androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {player.seekToDefaultPosition();player.prepare();return}
                error=if(post.live) LIVE_OFFLINE else "Playback failed (${exception.errorCodeName}). Try again.";buffering=false
            }
        }
        player.addListener(listener)
        onDispose {player.removeListener(listener)}
    }
    LaunchedEffect(post.id,api,retry) {
        error=null;loading=api!=null
        if(api==null) {
            if(player.currentMediaItem?.mediaId!="${post.id}|demo") {
                player.setMediaItem(playbackItem(post,"${post.id}|demo","android.resource://${context.packageName}/${R.raw.demo_playback}"));player.prepare();player.play()
            }
        } else if(post.live) mediaId=post.id
        else try {
            val result=api.detail(post.id);detail=result
            if(mediaId==null || result.media.none{it.id==mediaId}) mediaId=result.media.firstOrNull{it.accessible}?.id
        } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message}
        finally {loading=false}
    }
    LaunchedEffect(mediaId,retry) {
        val id=mediaId ?: return@LaunchedEffect
        if(api==null) return@LaunchedEffect
        loading=true;error=null
        try {
            val available=api.qualities(id,if(post.live) "live" else "onDemand");qualities=available
            val retained=player.currentMediaItem?.mediaId?.takeIf{it.startsWith("${post.id}|$id|")}?.substringAfterLast('|')
            val chosen=available.find{it.id==(retained ?: qualityId)} ?: available.firstOrNull() ?: error("No playable media for this subscription.")
            qualityId=chosen.id
            if(player.currentMediaItem?.mediaId!="${post.id}|$id|${chosen.id}") {
                player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                player.setMediaItem(playbackItem(post,"${post.id}|$id|${chosen.id}",chosen.url));player.prepare();player.play()
            }
        } catch(e:CancellationException){throw e} catch(e:Exception){error=if(post.live) LIVE_OFFLINE else e.message}
        finally {loading=false}
    }
    fun switchQuality(quality: MediaQuality) {
        if(post.live) {
            // Live renditions share one adaptive playlist; Auto lets the player adapt again.
            val group=player.currentTracks.groups.firstOrNull{it.type==C.TRACK_TYPE_VIDEO};val index=quality.id.toIntOrNull()
            player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().apply {
                if(group==null || index==null) clearOverridesOfType(C.TRACK_TYPE_VIDEO) else setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup,index))
            }.build()
            liveQuality=quality.id;return
        }
        if(quality.id==qualityId) return
        val position=player.currentPosition;val playing=player.playWhenReady
        qualityId=quality.id;error=null
        player.setMediaItem(playbackItem(post,"${post.id}|$mediaId|${quality.id}",quality.url),position);player.prepare();player.playWhenReady=playing
    }
    fun vote(dislike:Boolean) {
        if(api==null || busyVote || detail==null) return
        busyVote=true;actionError=null
        scope.launch {try {val interactions=api.react(post.id,dislike);detail=detail?.let{it.copy(reactions=it.reactions.applying(interactions))}}
        catch(e:CancellationException){throw e} catch(e:Exception){actionError=e.message} finally{busyVote=false}}
    }
    Column(Modifier.fillMaxSize().then(if(inPip || fullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
        if(api==null || loading || mediaId!=null) Box(Modifier.then(if(inPip || fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(if(detail?.media?.find{it.id==mediaId}?.kind=="audio") 5f else 16f/9f)).background(Color.Black).onGloballyPositioned { coordinates ->
            if(!inPip) activity?.updatePip(shouldAutoPip(isVideo.value,player.playWhenReady,player.playbackState),coordinates.boundsInWindow().let{android.graphics.Rect(it.left.toInt(),it.top.toInt(),it.right.toInt(),it.bottom.toInt())})
        }) {
            AndroidView(factory={PlayerView(it).apply {
                this.player=player;setShowNextButton(false);setShowPreviousButton(false);keepScreenOn=true
                setFullscreenButtonClickListener {activity?.setPlayerFullscreen(it)}
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener {controlsVisible=it==View.VISIBLE})
                controlsVisible=isControllerFullyVisible
            }},update={it.useController=!inPip;it.setFullscreenButtonState(fullscreen)},modifier=Modifier.fillMaxSize(),onRelease={
                it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?)
                it.player=null
            })
            if(!ready && !post.thumbnail.isNullOrBlank()) AsyncImage(model=post.thumbnail,contentDescription=null,contentScale=androidx.compose.ui.layout.ContentScale.Crop,modifier=Modifier.fillMaxSize())
            if((loading || buffering) && error==null) CircularProgressIndicator(Modifier.align(Alignment.Center),color=Color.White)
            if(!inPip && controlsVisible) FilledTonalIconButton(onClick={if(fullscreen) activity?.setPlayerFullscreen(false) else onClose()},modifier=Modifier.align(Alignment.TopStart).padding(8.dp),shape=CircleShape) {Glyph(Symbol.Back,Modifier.semanticsLabel(if(fullscreen) "Exit fullscreen" else "Back to library"))}
        }
        else if(!inPip) IconButton(onClick=onClose) {Glyph(Symbol.Back,Modifier.semanticsLabel("Back to library"))}
        if(!inPip && !fullscreen) Column(Modifier.widthIn(max=720.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(post.title,style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
            if(detail?.media.orEmpty().size>1) {
                var attachments by remember {mutableStateOf(false)}
                Box {
                    OutlinedButton(onClick={attachments=true}) {Text(detail?.media?.find{it.id==mediaId}?.title ?: "Attachment");Glyph(Symbol.Down,Modifier.padding(start=8.dp).size(16.dp))}
                    DropdownMenu(expanded=attachments,onDismissRequest={attachments=false}) {
                        detail?.media.orEmpty().forEach {media -> DropdownMenuItem(text={Text(media.title)},enabled=media.accessible,onClick={mediaId=media.id;qualityId=null;ready=false;attachments=false})}
                    }
                }
            }
            val byline: @Composable () -> Unit = {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                CreatorImage(post.creator.icon,post.creator.initials,28)
                Text(listOfNotNull(post.channelTitle ?: post.creator.name,fullDate(post.releaseDate)).joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
            val actions: @Composable () -> Unit = {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                val menuQualities=if(post.live) liveQualities(tracks) else qualities
                val menuQuality=if(post.live) liveQuality else qualityId
                Box {
                    FilledTonalButton(onClick={qualityMenu=true},enabled=menuQualities.isNotEmpty() && !loading) {Text(menuQualities.find{it.id==menuQuality}?.label ?: if(api==null) "Demo" else "Quality");Glyph(Symbol.Down,Modifier.padding(start=6.dp).size(16.dp))}
                    DropdownMenu(expanded=qualityMenu,onDismissRequest={qualityMenu=false}) {menuQualities.forEach {quality ->
                        DropdownMenuItem(text={Text(quality.label)},leadingIcon={if(quality.id==menuQuality) Glyph(Symbol.Check)},onClick={switchQuality(quality);qualityMenu=false})
                    }}
                }
                // Streams have no downloads or post reactions.
                if(!post.live) {
                Box {
                    FilledTonalIconButton(enabled=api!=null && mediaId!=null && !loading && !preparingDownload && !downloading,onClick={
                        scope.launch {
                            preparingDownload=true;actionError=null
                            try {downloadQualities=api!!.qualities(mediaId!!,"download");if(downloadQualities.isEmpty()) error("No downloads available.");downloadMenu=true}
                            catch(e:CancellationException){throw e} catch(e:Exception){actionError=e.message} finally{preparingDownload=false}
                        }
                    }) {if(preparingDownload) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Glyph(Symbol.Download,Modifier.size(20.dp).semanticsLabel("Download"))}
                    DropdownMenu(expanded=downloadMenu,onDismissRequest={downloadMenu=false}) {downloadQualities.forEach {quality ->
                        DropdownMenuItem(text={Text(quality.label)},onClick={
                            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            VideoDownloads.enqueue(context,post.id,post.title,quality,detail?.media?.find{it.id==mediaId}?.kind=="audio");downloadMenu=false
                        })
                    }}
                }
                val reactions=detail?.reactions ?: Reactions(0,0,false,false)
                val pillColor=MaterialTheme.colorScheme.surfaceContainerHigh
                Row(Modifier.height(48.dp).drawBehind {
                    val height=40.dp.toPx()
                    drawRoundRect(pillColor,Offset(0f,(size.height-height)/2),Size(size.width,height),CornerRadius(height/2))
                },verticalAlignment=Alignment.CenterVertically) {
                    VoteHalf(reactions.liked,reactions.likes,Symbol.ThumbUp,if(reactions.liked) "Unlike post" else "Like post",api!=null && detail!=null && !busyVote) {vote(false)}
                    VerticalDivider(Modifier.height(16.dp),color=MaterialTheme.colorScheme.outlineVariant)
                    VoteHalf(reactions.disliked,reactions.dislikes,Symbol.ThumbDown,if(reactions.disliked) "Remove dislike" else "Dislike post",api!=null && detail!=null && !busyVote) {vote(true)}
                }
                }
            }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if(maxWidth>=600.dp) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) {byline()}
                    actions()
                } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {byline();actions()}
            }
            if(error!=null) {Text(error!!,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}) {Text(if(post.live) "Check live status" else "Retry playback")}}
            if(actionError!=null) Text(actionError!!,color=MaterialTheme.colorScheme.error)
            if(download!=null) when(download!!.state) {
                WorkInfo.State.RUNNING,WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED -> {
                    val progress=download!!.progress.getInt("progress",-1)
                    Row(verticalAlignment=Alignment.CenterVertically) {Text(if(progress>=0) "Downloading · $progress%" else "Download queued",modifier=Modifier.weight(1f));TextButton(onClick={workManager.cancelUniqueWork(VideoDownloads.name(post.id))}) {Text("Cancel")}}
                }
                WorkInfo.State.SUCCEEDED -> Text("Download saved",color=MaterialTheme.colorScheme.primary)
                WorkInfo.State.FAILED -> Text(download!!.outputData.getString("error") ?: "Download failed. Try again.",color=MaterialTheme.colorScheme.error)
                else -> Unit
            }
            val description=remember(detail?.text,post.text) {plainText(detail?.text?.ifBlank{post.text} ?: post.text)}
            if(description.isNotBlank()) {
                var expanded by rememberSaveable(post.id) {mutableStateOf(false)}
                Text(description,style=MaterialTheme.typography.bodyLarge,maxLines=if(expanded) Int.MAX_VALUE else 4,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(description.length>240) TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)) {Text(if(expanded) "Show less" else "Show more")}
            }
            if(!post.live) {
                HorizontalDivider()
                CommentsSection(api,post.id,detail?.comments ?: 0)
            }
        }
    }
}

private const val LIVE_OFFLINE="This stream is offline or unavailable. Try again when it’s live."
private fun fullDate(date:String):String? = runCatching {java.time.OffsetDateTime.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))}.getOrNull()
internal fun Modifier.semanticsLabel(label: String): Modifier = this.then(Modifier.semantics { contentDescription = label })

@Composable
private fun VoteHalf(active:Boolean,count:Int,symbol:Symbol,label:String,enabled:Boolean,onClick:()->Unit) {
    TextButton(onClick=onClick,enabled=enabled,
        shape=if(symbol==Symbol.ThumbUp) RoundedCornerShape(topStart=20.dp,bottomStart=20.dp) else RoundedCornerShape(topEnd=20.dp,bottomEnd=20.dp),
        modifier=Modifier.semantics {selected=active},
        contentPadding=PaddingValues(horizontal=14.dp),
        colors=ButtonDefaults.textButtonColors(
            containerColor=if(active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor=if(active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )) {
        Glyph(symbol,Modifier.size(20.dp).semanticsLabel(label),LocalContentColor.current)
        Spacer(Modifier.width(8.dp));Text(count.toString())
    }
}

private fun playbackItem(post:FeedPost,id:String,url:String):MediaItem = MediaItem.Builder().setMediaId(id).setUri(url)
    .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(post.title).setArtist(post.channelTitle ?: post.creator.name)
        .setArtworkUri(post.thumbnail?.let{Uri.parse(it)}).build()).build()

/** Live delivery returns one adaptive playlist; its video renditions become the quality choices. */
internal fun liveQualities(tracks:Tracks):List<MediaQuality> {
    val group=tracks.groups.firstOrNull{it.type==C.TRACK_TYPE_VIDEO} ?: return emptyList()
    return listOf(MediaQuality("auto","Auto","",Int.MAX_VALUE)) + (0 until group.length).filter(group::isTrackSupported).map { index ->
        val format=group.getTrackFormat(index)
        MediaQuality("$index","${format.height}p${format.frameRate.takeIf{it>31f}?.roundToInt() ?: ""}","",format.height*1000+format.frameRate.roundToInt())
    }.distinctBy{it.label}.sortedByDescending{it.order}
}

internal fun shouldAutoPip(video:Boolean,wantsPlayback:Boolean,state:Int):Boolean =
    video && wantsPlayback && (state==Player.STATE_READY || state==Player.STATE_BUFFERING)
