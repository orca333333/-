package com.chatbridge.kakao.data.news

import java.net.URLEncoder

/**
 * Google News RSS (Korean edition) backs every category. Picking a single-publisher RSS feed per
 * category would be more curated, but Google News's URL scheme is the one stable, well-documented,
 * no-key-required source that reliably aggregates both domestic and international coverage in
 * Korean — which is what "국내외 뉴스 종합" needs. Swap these per category if you'd rather pin to
 * specific outlets.
 */
object NewsSources {

    fun feedUrlFor(category: NewsCategory): String = when (category) {
        NewsCategory.POLITICS -> searchFeed("정치")
        NewsCategory.ECONOMY -> topicFeed("BUSINESS")
        NewsCategory.WAR -> searchFeed("전쟁")
        NewsCategory.SCIENCE -> topicFeed("SCIENCE")
        NewsCategory.ENTERTAINMENT -> topicFeed("ENTERTAINMENT")
        NewsCategory.SPORTS -> topicFeed("SPORTS")
        NewsCategory.WEATHER -> searchFeed("날씨")
    }

    private fun topicFeed(topic: String) =
        "https://news.google.com/rss/headlines/section/topic/$topic?hl=ko&gl=KR&ceid=KR:ko"

    private fun searchFeed(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://news.google.com/rss/search?q=$encoded&hl=ko&gl=KR&ceid=KR:ko"
    }
}
