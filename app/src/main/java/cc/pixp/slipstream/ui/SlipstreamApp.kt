@file:OptIn(ExperimentalMaterial3Api::class)
package cc.pixp.slipstream.ui

import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.pixp.slipstream.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LibraryScreen(api: FloatplaneApi?, account: Account, onSignOut: () -> Unit, onSessionExpired: () -> Unit, sessionError: String?) {
    val inPip = cc.pixp.slipstream.LocalPipMode.current
    val fullscreen = cc.pixp.slipstream.LocalPlayerFullscreen.current
    val demo = api == null
    val windowSize = LocalWindowInfo.current.containerSize
    val compact = with(LocalDensity.current) { minOf(windowSize.width, windowSize.height).toDp() < 600.dp }
    var creators by remember(api) { mutableStateOf(if(demo) DemoLibrary.creators else emptyList()) }
    var channels by remember(api) { mutableStateOf<List<CreatorChannel>>(emptyList()) }
    var selection by rememberSaveable { mutableStateOf("all") }
    var posts by remember(api) { mutableStateOf<List<FeedPost>>(emptyList()) }
    var offsets by remember { mutableStateOf<Map<String,Int>>(emptyMap()) }
    var exhausted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val listState = key(selection, searching) { rememberLazyListState() }
    val pullState = rememberPullToRefreshState()
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var bannerHeight by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<FeedPost?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val channel = channels.find { it.id == selection }
    val activeCreator = creators.find { it.id == (channel?.creator ?: selection) }
    val activeCreators = if(activeCreator != null) listOf(activeCreator) else creators
    val title = when(selection) { "all" -> "Library"; "live" -> "Live"; else -> channel?.title ?: activeCreator?.name ?: "Library" }
    val banner = channel?.cover ?: activeCreator?.cover
    val hasBanner = !banner.isNullOrBlank() && query.isBlank() && !searching && selected == null && selection != "live"
    val bannerBehindToolbar by remember(hasBanner, toolbarHeight, bannerHeight, listState) {
        derivedStateOf { hasBanner && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < (bannerHeight - toolbarHeight).coerceAtLeast(1) }
    }
    fun refreshFeed() {
        if(!loading && !loadingMore) { refreshing = true; refresh++ }
    }

    fun handle(exception: Exception) {
        if(exception is FloatplaneException && exception.status == 401) onSessionExpired()
        else error = exception.message ?: "Couldn’t load posts. Try again."
    }
    LaunchedEffect(api) {
        if(api == null) return@LaunchedEffect
        try {
            val nextCreators = api.creators()
            val nextChannels = api.channels(nextCreators)
            creators = nextCreators
            channels = nextChannels
        } catch(e: CancellationException) { throw e } catch(e: Exception) { handle(e) }
    }
    LaunchedEffect(selection, query, creators, channels, refresh) {
        if(selection == "live") { loading=false; return@LaunchedEffect }
        loading=true; error=null
        try {
            if(query.isNotBlank()) delay(300)
            val nextOffsets = mutableMapOf<String,Int>()
            val nextExhausted = mutableSetOf<String>()
            val next = if(api == null) {
                DemoLibrary.filter(activeCreator?.id,query)
            } else activeCreators.flatMap { creator ->
                val batch=api.posts(creator,query,channel=channel?.id)
                nextOffsets[creator.id]=batch.size
                if(batch.size<20) nextExhausted += creator.id
                batch
            }
            posts=next.distinctBy { it.id }.sortedByDescending { it.releaseDate }
            offsets=nextOffsets; exhausted=nextExhausted
        } catch(e: CancellationException) { throw e } catch(e: Exception) { handle(e) }
        finally { if(currentCoroutineContext().isActive) { loading=false; refreshing=false } }
    }
    fun navigate(id: String) {
        selection=id;selected=null;query="";searching=false
        scope.launch {drawer.close()}
    }
    BackHandler(drawer.isOpen || selected!=null || searching || selection!="all") {
        when { drawer.isOpen -> scope.launch { drawer.close() };selected!=null -> selected=null;searching -> {searching=false;query=""};else -> selection="all" }
    }
    ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=!inPip && !fullscreen,drawerContent={
        ModalDrawerSheet(drawerContainerColor=MaterialTheme.colorScheme.surfaceContainerLow,modifier=Modifier.width(300.dp)) {
            Sidebar(account,creators,channels,selection,::navigate,onAccount={confirmSignOut=true})
        }
    }) {
        Scaffold(
            modifier=Modifier.windowInsetsPadding(
                if(compact && !inPip && !fullscreen) WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) else WindowInsets(0)
            ).imePadding(),
            topBar={ if(selected==null) TopAppBar(
                modifier=Modifier.onSizeChanged { toolbarHeight=it.height },
                colors=TopAppBarDefaults.topAppBarColors(containerColor=if(bannerBehindToolbar) Color.Transparent else MaterialTheme.colorScheme.surface),
                title={ if(searching) ToolbarSearch(query, {query=it}) else if(!bannerBehindToolbar) Text(title) },
                navigationIcon={
                    if(searching) IconButton(onClick={searching=false;query=""}) {Glyph(Symbol.Back,Modifier.semanticsLabel("Close search"))}
                    else ToolbarButton(Symbol.Menu,"Show creators",bannerBehindToolbar) {scope.launch {drawer.open()}}
                },
                actions={ if(searching) {
                    if(query.isNotEmpty()) IconButton(onClick={query=""}) {Glyph(Symbol.Close,Modifier.semanticsLabel("Clear search"))}
                } }
            ) },
            floatingActionButton={ if(selected==null && selection!="live" && !searching) FloatingActionButton(onClick={searching=true}) {
                Glyph(Symbol.Search,Modifier.semanticsLabel("Search"),MaterialTheme.colorScheme.onPrimaryContainer)
            } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(
                start=padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                end=padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                top=if(hasBanner || inPip || fullscreen) 0.dp else padding.calculateTopPadding(),
                bottom=if(inPip || fullscreen) 0.dp else padding.calculateBottomPadding()
            ).consumeWindowInsets(padding)) {
                if(selected!=null) {
                    val post=selected!!
                    PlaybackScreen(post.copy(channelTitle=post.channelTitle ?: channels.find{it.id==post.channelId}?.title,creator=post.creator.copy(icon=channels.find{it.id==post.channelId}?.icon ?: post.creator.icon)),{selected=null},api)

                } else if(selection=="live") Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    Text("Live playback is not available yet.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val columns = ((maxWidth - 16.dp) / 256.dp).toInt().coerceAtLeast(1)
                        val indicatorInset = if(hasBanner) with(LocalDensity.current) { toolbarHeight.toDp() } else 0.dp
                        PullToRefreshBox(
                            isRefreshing=refreshing, onRefresh=::refreshFeed, state=pullState,
                            modifier=Modifier.fillMaxSize(),
                            indicator={ PullToRefreshDefaults.Indicator(
                                state=pullState, isRefreshing=refreshing,
                                modifier=Modifier.align(Alignment.TopCenter).padding(top=indicatorInset)
                            ) }
                        ) {
                            LazyColumn(state=listState,verticalArrangement=Arrangement.spacedBy(22.dp),
                                contentPadding=PaddingValues(top=if(hasBanner || inPip || fullscreen) 0.dp else 12.dp,bottom=96.dp),
                                modifier=Modifier.fillMaxSize().semantics { customActions=listOf(CustomAccessibilityAction("Refresh") {refreshFeed();true}) }) {
                                if(hasBanner) item(key="banner") {
                                    Box(Modifier.fillMaxWidth().aspectRatio(3.83f).onSizeChanged { bannerHeight=it.height }) {
                                        AsyncImage(model=banner,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                                        Box(Modifier.fillMaxWidth().height(indicatorInset).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.35f),Color.Transparent))))
                                    }
                                }
                                if(error!=null || sessionError!=null) item {
                                    Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text(error ?: sessionError!!,color=MaterialTheme.colorScheme.error);TextButton(onClick={refresh++}){Text("Try again")}}
                                }
                                items(posts.chunked(columns),key={it.first().id}) { row ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                        row.forEach {post -> Box(Modifier.weight(1f)) {
                                            FeedCard(post,if(channel==null) post.channelTitle ?: channels.find{it.id==post.channelId}?.title ?: post.creator.name else null,onClick={selected=post})
                                        } }
                                        repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                                    }
                                }
                                if(loading && !refreshing) item {Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
                                else if(posts.isEmpty() && error==null) item {Text(if(query.isBlank()) "No posts" else "No results",modifier=Modifier.padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                if(!demo && activeCreators.any{it.id !in exhausted} && posts.isNotEmpty()) item {
                                    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                                        TextButton(enabled=!loadingMore,onClick={
                                            val requestSelection=selection; val requestQuery=query;val requestCreators=activeCreators
                                            scope.launch {
                                                loadingMore=true
                                                try {
                                                    val nextOffsets=offsets.toMutableMap();val nextExhausted=exhausted.toMutableSet()
                                                    val more=requestCreators.filter{it.id !in exhausted}.flatMap {creator ->
                                                        val batch=api!!.posts(creator,requestQuery,nextOffsets[creator.id] ?: 0,channel?.id)
                                                        nextOffsets[creator.id]=(nextOffsets[creator.id] ?: 0)+batch.size
                                                        if(batch.size<20) nextExhausted+=creator.id
                                                        batch
                                                    }
                                                    if(selection==requestSelection && query==requestQuery) {
                                                        posts=(posts+more).distinctBy{it.id}.sortedByDescending{it.releaseDate};offsets=nextOffsets;exhausted=nextExhausted
                                                    }
                                                } catch(e:CancellationException){throw e} catch(e:Exception){handle(e)} finally{loadingMore=false}
                                            }
                                        }) {Text(if(loadingMore) "Loading…" else "Load more")}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(confirmSignOut) AlertDialog(onDismissRequest={confirmSignOut=false},title={Text(if(demo) "Leave demo?" else "Sign out?")},
        confirmButton={TextButton(onClick={confirmSignOut=false;onSignOut()}){Text(if(demo) "Leave demo" else "Sign out")}},dismissButton={TextButton(onClick={confirmSignOut=false}){Text("Cancel")}})
}

@Composable
private fun Sidebar(account: Account,creators: List<Creator>,channels: List<CreatorChannel>,selection: String,onSelect:(String)->Unit,onAccount:()->Unit) {
    Text("Slipstream",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(horizontal=28.dp,vertical=24.dp))
    Column(Modifier.fillMaxHeight()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            NavigationDrawerItem(label={Text("Library")},selected=selection=="all",onClick={onSelect("all")},icon={Glyph(Symbol.Library)})
            NavigationDrawerItem(label={Text("Live")},selected=selection=="live",onClick={onSelect("live")},icon={Glyph(Symbol.Live)})
            Text("Creators",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(start=16.dp,top=24.dp,bottom=12.dp))
            creators.forEach {creator ->
                var expanded by rememberSaveable(creator.id) {mutableStateOf(true)}
                val children=channels.filter{it.creator==creator.id}
                NavigationDrawerItem(label={Text(creator.name,maxLines=1,overflow=TextOverflow.Ellipsis)},selected=selection==creator.id,onClick={onSelect(creator.id)},
                    icon={CreatorImage(creator.icon,creator.initials)},badge={if(children.isNotEmpty()) IconButton(onClick={expanded=!expanded},modifier=Modifier.size(32.dp)) {
                        Glyph(if(expanded) Symbol.Down else Symbol.Arrow,Modifier.size(18.dp).semanticsLabel(if(expanded) "Collapse ${creator.name}" else "Expand ${creator.name}"))
                    }})
                if(expanded) children.forEach {channel ->
                    NavigationDrawerItem(label={Text(channel.title,maxLines=1,overflow=TextOverflow.Ellipsis)},selected=selection==channel.id,onClick={onSelect(channel.id)},
                        icon={CreatorImage(channel.icon,channel.title.take(1))},modifier=Modifier.padding(start=20.dp))
                }
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().clickable(onClick=onAccount).padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CreatorImage(account.avatar,account.name.take(1),32)
            Text(account.name,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f));Glyph(Symbol.Down,Modifier.size(18.dp))
        }
    }
}

@Composable
fun CreatorImage(url: String?,initials: String,size: Int=24) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center) {
        if(url.isNullOrBlank()) Text(initials,style=MaterialTheme.typography.labelMedium)
        else AsyncImage(model=url,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
    }
}

@Composable
private fun FeedCard(post: FeedPost,byline: String?,onClick:()->Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled=post.accessible,onClick=onClick).padding(4.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            if(!post.thumbnail.isNullOrBlank()) AsyncImage(model=post.thumbnail,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            else Text(plainText(post.text),style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(20.dp).align(Alignment.CenterStart))
            if(!post.accessible || post.duration.isNotEmpty()) Surface(color=androidx.compose.ui.graphics.Color.Black.copy(alpha=.8f),contentColor=androidx.compose.ui.graphics.Color.White,shape=RoundedCornerShape(4.dp),modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                if(!post.accessible) Glyph(Symbol.Lock,Modifier.padding(5.dp).size(16.dp),androidx.compose.ui.graphics.Color.White)
                else Text(post.duration,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(horizontal=5.dp,vertical=3.dp))
            }
        }
        Text(post.title,style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Medium),maxLines=2,overflow=TextOverflow.Ellipsis)
        Text(listOfNotNull(byline,postDate(post.releaseDate)).joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

internal fun plainText(text:String):String = Html.fromHtml(text,Html.FROM_HTML_MODE_COMPACT).toString().trim()
internal fun postDate(date:String):String? = runCatching { OffsetDateTime.parse(date).format(DateTimeFormatter.ofPattern("MMM d")) }.getOrNull()

@Composable
private fun ToolbarButton(symbol: Symbol, label: String, overBanner: Boolean, onClick: () -> Unit) {
    if(overBanner) FilledTonalIconButton(onClick=onClick,colors=IconButtonDefaults.filledTonalIconButtonColors(
        containerColor=MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface
    )) {Glyph(symbol,Modifier.semanticsLabel(label))}
    else IconButton(onClick=onClick) {Glyph(symbol,Modifier.semanticsLabel(label))}
}

@Composable
private fun ToolbarSearch(query: String, onQuery: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(value=query,onValueChange=onQuery,placeholder={Text("Search")},singleLine=true,
        modifier=Modifier.fillMaxWidth().focusRequester(focus),
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}),
        colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,
            focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent))
}
