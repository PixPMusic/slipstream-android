package cc.pixp.slipstream.data

data class Creator(val id: String, val name: String, val initials: String, val icon: String? = null, val cover: String? = null)
data class Account(val id: String, val name: String, val avatar: String? = null)
data class CreatorChannel(val id: String, val creator: String, val title: String, val icon: String?, val cover: String?, val order: Int)
data class FeedPost(val id: String, val title: String, val creator: Creator, val text: String, val duration: String, val thumbnail: String? = null, val releaseDate: String = "", val channelId: String? = null, val channelTitle: String? = null, val accessible: Boolean = true)

data class MediaAttachment(val id: String, val title: String, val kind: String, val accessible: Boolean)
data class MediaQuality(val id: String, val label: String, val url: String, val order: Int)
data class PostDetail(val text: String, val media: List<MediaAttachment>, val reactions: Reactions, val comments: Int)
data class Reactions(val likes: Int, val dislikes: Int, val liked: Boolean, val disliked: Boolean) {
    fun applying(interactions: Set<String>) = Reactions(
        (likes + (if("like" in interactions) 1 else 0) - (if(liked) 1 else 0)).coerceAtLeast(0),
        (dislikes + (if("dislike" in interactions) 1 else 0) - (if(disliked) 1 else 0)).coerceAtLeast(0),
        "like" in interactions, "dislike" in interactions
    )
}
data class PostComment(val id: String, val text: String, val username: String, val avatar: String?, val date: String, val likes: Int, val liked: Boolean, val totalReplies: Int, val replies: List<PostComment>)
