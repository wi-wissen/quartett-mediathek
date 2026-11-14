package net.wi7.quartettmediathek.source.zdf

/**
 * GraphQL documents sent to api.zdf.de. Field names were copied from the website's own queries
 * (`docs/api/zdf-web-*`), `@client` fields stripped. Introspection is disabled on the server.
 */
internal object ZdfQueries {
    private const val IMAGE = "imageWithoutLogo { layouts { dim768X432 } }"

    private const val VIDEO = """
        id title canonical editorialDate
        episodeInfo { seasonNumber episodeNumber }
        smartCollection { title canonical }
        teaser { title description enableAudioDescription enableSubtitle $IMAGE }
        currentMedia { nodes { ptmdTemplate ... on VodMedia { duration vodMediaType geoLocation } } }
    """

    /** `first` of cluster items must stay <= 26. */
    val START_PAGE = """
        { curatedCollectionByCanonical(canonical: "mt2025-startseite-100") {
            modules(first: 40) { nodes { __typename
              ... on StaticHorizontalCluster { title items(first: 26) { nodes { __typename
                ... on ISmartCollection { id title canonical collectionType teaser { title $IMAGE } contentOwner { title } }
                ... on Video { id title canonical editorialDate smartCollection { title canonical }
                  teaser { title $IMAGE } currentMedia { nodes { ... on VodMedia { duration } } } }
              } } }
            } }
        } }
    """.trimIndent()

    /** One season (offset `i`) with a page of 24 episodes (offset `e`); movies expose a single `video`. */
    val SHOW = """
        query(${'$'}c: String!, ${'$'}i: Int!, ${'$'}e: Int!) {
          smartCollectionByCanonical(canonical: ${'$'}c) {
            __typename id title canonical collectionType editorialDate
            teaser { title description $IMAGE }
            contentOwner { title }
            ... on ISeriesSmartCollection {
              seasons(first: 1, offset: ${'$'}i) { totalCount nodes { title number countEpisodes
                episodes(first: 24, offset: ${'$'}e) { nodes { $VIDEO } } } }
            }
            ... on MovieSmartCollection { video { $VIDEO } }
          }
        }
    """.trimIndent()

    val VIDEO_BY_CANONICAL = """
        query(${'$'}c: String!) { videoByCanonical(canonical: ${'$'}c) { $VIDEO } }
    """.trimIndent()

    val VIDEO_IMAGE = """
        query(${'$'}c: String!) { videoByCanonical(canonical: ${'$'}c) { teaser { $IMAGE } } }
    """.trimIndent()
}
