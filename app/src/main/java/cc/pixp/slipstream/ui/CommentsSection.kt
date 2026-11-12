package cc.pixp.slipstream.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.pixp.slipstream.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun CommentsSection(api:FloatplaneApi?,post:String,count:Int) {
    var comments by remember(post) {mutableStateOf<List<PostComment>>(emptyList())}
    var loading by remember {mutableStateOf(false)}
    var sending by remember {mutableStateOf(false)}
    var hasMore by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var draft by rememberSaveable(post) {mutableStateOf("")}
    var added by remember {mutableIntStateOf(0)}
    var retry by remember {mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    suspend fun load(more:Boolean) {
        if(api==null) return
        loading=true;error=null
        try {
            val batch=api.comments(post,if(more) comments.lastOrNull()?.id else null)
            comments=(if(more) comments+batch else batch).distinctBy{it.id};hasMore=batch.size==20
        } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message} finally{loading=false}
    }
    LaunchedEffect(api,post,retry) {load(false)}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text("Comments",style=MaterialTheme.typography.titleMedium);Text((count+added).toString(),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=draft,onValueChange={draft=it},placeholder={Text("Add a comment…")},enabled=api!=null && !sending,maxLines=6,modifier=Modifier.weight(1f))
            TextButton(enabled=api!=null && !sending && draft.isNotBlank(),onClick={
                val text=draft.trim();sending=true;error=null
                scope.launch {try {
                    val comment=api!!.addComment(post,text);comments=(listOf(comment)+comments).distinctBy{it.id};added++
                    if(draft.trim()==text) draft=""
                } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message} finally{sending=false}}
            }) {Text(if(sending) "Posting…" else "Post")}
        }
        comments.forEach {comment -> key(comment.id) {
            CommentThread(api,post,comment)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        }}
        if(loading) CircularProgressIndicator(Modifier.size(24.dp))
        if(error!=null) {Text(error!!,color=MaterialTheme.colorScheme.error);TextButton(onClick={scope.launch{load(comments.isNotEmpty())}}) {Text("Try again")}}
        else if(hasMore && !loading) TextButton(onClick={scope.launch{load(true)}}) {Text("More comments")}
        else if(comments.isEmpty() && !loading) Text(if(api==null) "Comments are unavailable in demo mode." else "No comments yet.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommentThread(api:FloatplaneApi?,post:String,comment:PostComment) {
    var expanded by remember {mutableStateOf(false)}
    var replies by remember {mutableStateOf(comment.replies)}
    var loading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var hasMore by remember {mutableStateOf(comment.totalReplies>replies.size)}
    val scope=rememberCoroutineScope()
    suspend fun load(more:Boolean) {
        if(api==null) return
        loading=true;error=null
        try {
            val batch=api.comments(post,if(more) replies.lastOrNull()?.id else null,parent=comment.id)
            replies=(if(more) replies+batch else batch).distinctBy{it.id}
            hasMore=batch.size==20 && replies.size<comment.totalReplies
        } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message} finally{loading=false}
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        CommentRow(api,post,comment)
        if(comment.totalReplies>0 || replies.isNotEmpty()) TextButton(onClick={
            expanded=!expanded
            if(expanded && replies.isEmpty()) scope.launch{load(false)}
        },modifier=Modifier.padding(start=36.dp)) {Text(if(expanded) "Hide replies" else "View replies (${maxOf(comment.totalReplies,replies.size)})")}
        if(expanded) Column(Modifier.padding(start=36.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            replies.forEach {reply -> key(reply.id) {CommentRow(api,post,reply)}}
            if(loading) CircularProgressIndicator(Modifier.size(20.dp))
            if(error!=null) {Text(error!!,color=MaterialTheme.colorScheme.error);TextButton(onClick={scope.launch{load(replies.isNotEmpty())}}){Text("Try again")}}
            else if(hasMore && !loading) TextButton(onClick={scope.launch{load(true)}}){Text("More replies")}
        }
    }
}

@Composable
private fun CommentRow(api:FloatplaneApi?,post:String,comment:PostComment) {
    var liked by remember(comment.id) {mutableStateOf(comment.liked)}
    var likes by remember(comment.id) {mutableIntStateOf(comment.likes)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        CreatorImage(comment.avatar,comment.username.take(1),30)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(comment.username,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
            postDate(comment.date)?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text(comment.text,style=MaterialTheme.typography.bodyMedium)
            TextButton(enabled=api!=null && !busy,onClick={
                busy=true;error=null
                scope.launch {try {
                    val value=api!!.likeComment(post,comment.id)
                    likes=(likes+(if(value) 1 else 0)-(if(liked) 1 else 0)).coerceAtLeast(0);liked=value
                } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message} finally{busy=false}}
            },contentPadding=PaddingValues(0.dp)) {
                Glyph(Symbol.ThumbUp,Modifier.size(18.dp).semanticsLabel(if(liked) "Unlike comment" else "Like comment"),if(liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if(likes>0) Text(likes.toString(),modifier=Modifier.padding(start=6.dp))
            }
            if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    }
}
