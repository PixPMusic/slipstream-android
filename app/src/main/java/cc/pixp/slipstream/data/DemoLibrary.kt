package cc.pixp.slipstream.data

object DemoLibrary {
    val workshop = Creator("studio", "The Workshop", "W")
    val fieldNotes = Creator("fieldnotes", "Field Notes", "F")
    val creators = listOf(workshop, fieldNotes)
    val posts = listOf(
        "A quieter kind of workstation",
        "Behind the build: the details that matter",
        "One camera, a whole new perspective",
        "The studio tour you asked for",
        "Taking the long way home",
        "Small experiments, surprising results"
    ).mapIndexed { index, title ->
        FeedPost("demo-$index", title, creators[index % creators.size],
            "A sample post from the Slipstream demo library. Your subscribed creators and their videos appear here after sign-in.",
            "1:00", releaseDate="2026-11-0${5-index%4}T12:00:00Z")
    }
    fun filter(creator: String?, query: String): List<FeedPost> = posts.filter {
        (creator == null || it.creator.id == creator) &&
            (query.isBlank() || "${it.title} ${it.creator.name}".contains(query.trim(), ignoreCase=true))
    }
}
