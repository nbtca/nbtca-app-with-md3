package space.nbtca.app.core.card

import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import space.nbtca.app.core.data.DiskCache
import java.io.IOException

class CardPageCacheTest {
    @get:Rule val directory = TemporaryFolder()

    private var session: String? = "alice"
    private var time = 1_000_000L
    private var requests = 0
    private var response: (String, Map<String, String>?) -> Document = { path, _ -> profile("卡片", path) }

    @Test fun reusesFreshPageAfterRestartAndBypassesItOnManualRefresh() = runBlocking {
        repository().load("/me")
        val reopened = repository()
        response = { path, _ -> profile("新版卡片", path) }

        val cached = reopened.load("/me")
        assertEquals("卡片", cached.document.selectFirst(".card")?.text())
        assertEquals(CardClient.ORIGIN + "/me", cached.document.location())
        assertEquals(1, requests)

        val refreshed = reopened.load("/me", forceRefresh = true)
        assertEquals("新版卡片", refreshed.document.selectFirst(".card")?.text())
        assertEquals(2, requests)
    }

    @Test fun previewsExpiredPageAndFallsBackOnNetworkFailure() = runBlocking {
        val repo = repository()
        repo.load("/me")
        time += 5 * 60_000L + 1
        response = { _, _ -> throw IOException("offline") }
        var preview: Document? = null

        val fallback = repo.load("/me") { preview = it }
        assertEquals(2, requests)
        assertSame(preview, fallback.document)
        assertTrue(fallback.usingCachedCopy)

        time += 7 * 24 * 60 * 60_000L
        assertTrue(runCatching { repo.load("/me") }.exceptionOrNull() is IOException)
    }

    @Test fun clearsCacheOnAuthorizationFailureOrSessionChange() = runBlocking {
        val repo = repository()
        repo.load("/me")
        response = { _, _ -> throw CardLoginRequired() }
        assertTrue(runCatching { repo.load("/me", forceRefresh = true) }.exceptionOrNull() is CardLoginRequired)
        response = { _, _ -> throw IOException("offline") }
        assertTrue(runCatching { repository().load("/me") }.exceptionOrNull() is IOException)

        response = { path, _ -> profile("卡片", path) }
        repo.load("/me")
        session = "bob"
        response = { _, _ -> throw IOException("offline") }
        assertTrue(runCatching { repository().load("/me") }.exceptionOrNull() is IOException)
        session = "alice"
        assertTrue(runCatching { repository().load("/me") }.exceptionOrNull() is IOException)
    }

    @Test fun reloadsProfileAfterUploadsAndInvalidatesMemberWall() = runBlocking {
        var updated = false
        response = { path, fields ->
            when {
                path == "/members" -> Jsoup.parse("<main><ul><li>$updated</li></ul></main>")
                fields != null -> profile("已提交", path).also { it.body().append("<p class=notice>已保存</p>") }
                else -> profile(if (updated) "保存后" else "保存前", path)
            }
        }
        val repo = repository()
        repo.load("/me")
        repo.load("/members")
        val saved = repo.saveProfile(mapOf("motto" to "新签名")) { updated = true }

        assertEquals("保存后", saved.selectFirst(".card")?.text())
        assertEquals("保存后", repo.load("/me").document.selectFirst(".card")?.text())
        assertEquals("true", repo.load("/members").document.selectFirst("li")?.text())
        assertEquals(5, requests)
    }

    @Test fun fetchesTicketDetailsLiveAndInvalidatesEventListOnClaim() = runBlocking {
        val repo = repository()
        response = { path, _ ->
            if (path == "/e") Jsoup.parse("<main><h1>活动</h1></main>")
            else Jsoup.parse("<div class=info><h1>活动</h1></div><div class='pass-card full' data-state=valid data-ticket></div>")
        }
        repo.load("/e")
        repo.load("/e/fixture")
        repo.load("/e/fixture")
        repo.claim("/e/fixture", emptyMap())
        repo.load("/e")
        assertEquals(5, requests)

        response = { _, _ -> throw IOException("offline") }
        assertTrue(runCatching { repo.load("/e/fixture") }.exceptionOrNull() is IOException)
    }

    private fun repository() = CardRepository(DiskCache(directory.root), { session }, { path, fields ->
        requests++
        response(path, fields)
    }, { time })

    private fun profile(text: String, path: String): Document =
        Jsoup.parse("<article class='card full'>$text</article>", CardClient.ORIGIN + path)
}
