package cc.pixp.slipstream.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayerDataTest {
    @Test fun switchingReactionAdjustsBothCountsAndCanRemoveVote() {
        val liked=Reactions(10,2,true,false)
        val disliked=liked.applying(setOf("dislike"))
        assertEquals(Reactions(9,3,false,true),disliked)
        assertEquals(Reactions(9,2,false,false),disliked.applying(emptySet()))
        assertEquals(Reactions(0,0,false,false),Reactions(0,0,true,true).applying(emptySet()))
    }
    @Test fun detailHandlesStringAttachmentsAndServerOrdering() {
        val detail=parseDetail(JSONObject("""{"text":"Post","videoAttachments":["video",{"id":"extra","title":"Extra","isAccessible":false}],"audioAttachments":[{"id":"audio","title":"Podcast"}],"attachmentOrder":["audio","extra","video"],"likes":12,"dislikes":1,"comments":4,"userInteraction":["like"]}"""))
        assertEquals(listOf("audio","extra","video"),detail.media.map{it.id})
        assertFalse(detail.media[1].accessible)
        assertEquals("audio",detail.media.first().kind)
        assertEquals(Reactions(12,1,true,false),detail.reactions)
    }
    @Test fun deliveryFiltersUnsafeDisabledAndHiddenVariantsAndSortsQuality() {
        val qualities=parseQualities(JSONObject("""{"groups":[{"origins":[{"url":"https://cdn.example/"}],"variants":[{"name":"low","label":"480p","enabled":true,"url":"/low.m3u8","order":1},{"name":"high","label":"1080p","enabled":true,"url":"/high.m3u8","order":3},{"enabled":false,"url":"/disabled"},{"enabled":true,"hidden":true,"url":"/hidden"},{"enabled":true,"url":"http://unsafe.example/video"}]}]}"""))
        assertEquals(listOf("1080p","480p"),qualities.map{it.label})
        assertEquals("https://cdn.example/high.m3u8",qualities.first().url)
    }
    @Test fun creatorKeepsItsCurrentOrMostRecentStream() {
        val creator=parseCreator(JSONObject("""{"id":"creator","title":"Studio","icon":{"path":"https://cdn.example/icon.png"},"liveStream":{"id":"stream","title":"Weekly show","description":"<p>Live now</p>","thumbnail":{"path":"https://cdn.example/live.jpg"}}}"""))
        assertEquals(LiveStream("stream","Weekly show","<p>Live now</p>","https://cdn.example/live.jpg"),creator.liveStream)
        assertNull(parseCreator(JSONObject("""{"id":"creator","title":"Studio"}""")).liveStream)
    }
    @Test fun liveDeliveryLetsTheServiceChooseItsFormat() {
        assertEquals(listOf("scenario" to "live","entityId" to "stream","entityKind" to "livestream"),deliveryQuery("stream","live"))
        assertEquals("hls.fmp4",deliveryQuery("video","onDemand").toMap()["outputKind"])
        assertEquals("flat",deliveryQuery("video","download").toMap()["outputKind"])
    }
    @Test fun commentsPreserveThreadAndVoteState() {
        val comment=parseComment(JSONObject("""{"id":"parent","text":"Hello","user":{"username":"Sample"},"likes":3,"totalReplies":1,"userInteraction":["like"],"replies":[{"id":"reply","text":"Reply","user":{"username":"Another"}}]}"""))
        assertTrue(comment.liked)
        assertEquals("reply",comment.replies.single().id)
        assertEquals(1,comment.totalReplies)
        assertFalse(comment.replies.single().liked)
    }
}
