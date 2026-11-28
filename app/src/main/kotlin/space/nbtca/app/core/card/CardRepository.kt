package space.nbtca.app.core.card

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import space.nbtca.app.core.data.DiskCache
import java.io.File
import java.io.IOException

data class CardClaimField(val name: String, val label: String, val value: String, val required: Boolean, val maxLength: Int, val hidden: Boolean)

internal data class CardPage(val document: Document, val usingCachedCopy: Boolean = false)

/** Compatibility adapter for the maintainer's existing website/HTTP contract. */
internal class CardRepository(
    private val cache: DiskCache,
    private val activeSession: suspend () -> CardCacheSession?,
    private val requestPage: suspend (String, Map<String, String>?) -> Document,
    private val now: () -> Long = System::currentTimeMillis,
    private val legacyCache: DiskCache? = null
) {
    private val mutex = Mutex()
    private var snapshot: PageSnapshot? = null

    suspend fun load(
        path: String,
        forceRefresh: Boolean = false,
        onCached: (Document) -> Unit = {}
    ): CardPage = withSession { session ->
        val livePass = path.startsWith("/e/")
        val entry = if (livePass) null else snapshot?.pages?.get(path)
        val cached = entry?.takeIf { now() - it.savedAt in 0L..MAX_CACHE_AGE_MS }?.let {
            withContext(Dispatchers.IO) {
                runCatching { Jsoup.parse(it.html, it.url).also { doc -> validatePage(path, doc) } }.getOrNull()
            }
        }
        val ttl = if (path == "/e") EVENT_LIST_TTL_MS else CARD_TTL_MS
        if (entry != null && cached != null && !forceRefresh && now() - entry.savedAt in 0L..ttl) {
            return@withSession CardPage(cached)
        }
        cached?.let(onCached)
        val document = try {
            fetch(path)
        } catch (failure: IOException) {
            if (cached != null && activeSession()?.key == session) {
                return@withSession CardPage(cached, usingCachedCopy = true)
            }
            throw failure
        }
        if (!livePass) rememberPage(session, path, document)
        CardPage(document)
    }

    suspend fun saveProfile(fields: Map<String, String>, uploadMedia: suspend () -> Unit): Document =
        withSession { session ->
            invalidate { it == "/me" || it == "/members" || it.startsWith("/members/") }
            val saved = fetch("/me", fields)
            check(CardHome.parse(saved) is CardHome.Member) { "只有社员卡可以编辑" }
            check(saved.select(".notice").any { it.text().contains("已保存") }) { "服务端未确认保存，请刷新查看" }
            uploadMedia()
            fetch("/me").also { rememberPage(session, "/me", it) }
        }

    suspend fun claim(path: String, fields: Map<String, String>): Document = withSession {
        invalidate { it == "/e" || it == "/me" }
        fetch(path, fields).also { doc ->
            claimError(doc)?.let { throw IllegalStateException(it) }
        }
    }

    private suspend fun fetch(path: String, fields: Map<String, String>? = null): Document =
        requestPage(path, fields).also { validatePage(path, it) }

    private suspend fun <T> withSession(block: suspend (String?) -> T): T = mutex.withLock {
        val identity = activeSession()
        val session = identity?.key
        if (snapshot == null) {
            val (stored, fromLegacyDirectory) = withContext(Dispatchers.IO) {
                val current = cache.readStale(CACHE_KEY, PageSnapshot.serializer())
                if (current != null) current to false
                else legacyCache?.readStale(CACHE_KEY, PageSnapshot.serializer()) to true
            }
            val belongsToSession = identity != null && stored != null &&
                (stored.session == session || stored.session == identity.legacyKey)
            val restored = if (belongsToSession) checkNotNull(stored).copy(session = session)
                else PageSnapshot(session)
            if (fromLegacyDirectory || restored != stored) store(restored) else snapshot = restored
        }
        if (snapshot?.session != session) store(PageSnapshot(session))
        try {
            block(session)
        } catch (failure: CardAuthorizationException) {
            store(PageSnapshot(session))
            throw failure
        }
    }

    private suspend fun rememberPage(session: String?, path: String, doc: Document) {
        if (session == null || activeSession()?.key != session) return
        if (doc.selectFirst("[data-ticket], form.claim") != null) {
            invalidate { it == path }
            return
        }
        val savedAt = now()
        val page = withContext(Dispatchers.IO) {
            doc.outputSettings().prettyPrint(false)
            CachedPage(doc.outerHtml(), doc.location(), savedAt)
        }
        val recent = (checkNotNull(snapshot).pages + (path to page)).entries
            .filter { savedAt - it.value.savedAt in 0L..MAX_CACHE_AGE_MS }
            .sortedByDescending { it.value.savedAt }
            .take(MAX_PAGES)
            .associate { it.toPair() }
        store(PageSnapshot(session, recent))
    }

    private suspend fun invalidate(matches: (String) -> Boolean) {
        val current = checkNotNull(snapshot)
        val remaining = current.pages.filterKeys { !matches(it) }
        if (remaining.size != current.pages.size) {
            store(current.copy(pages = remaining))
        }
    }

    private suspend fun store(value: PageSnapshot) {
        snapshot = value
        withContext(Dispatchers.IO) {
            if (value.pages.isEmpty()) {
                legacyCache?.remove(CACHE_KEY)
                cache.remove(CACHE_KEY)
            } else if (cache.write(CACHE_KEY, PageSnapshot.serializer(), value)) {
                legacyCache?.remove(CACHE_KEY)
            }
        }
    }

    @Serializable
    private data class PageSnapshot(val session: String?, val pages: Map<String, CachedPage> = emptyMap())

    @Serializable
    private data class CachedPage(val html: String, val url: String, val savedAt: Long)

    companion object {
        private const val CACHE_KEY = "card-pages-v1"
        private const val EVENT_LIST_TTL_MS = 60_000L
        private const val CARD_TTL_MS = 5 * 60_000L
        private const val MAX_CACHE_AGE_MS = 7 * 24 * 60 * 60_000L
        private const val MAX_PAGES = 24
        @Volatile private var instance: CardRepository? = null

        fun getInstance(context: Context): CardRepository = instance ?: synchronized(this) {
            instance ?: CardRepository(
                DiskCache(File(context.applicationContext.noBackupFilesDir, "card-pages")),
                CardClient::cacheSession,
                CardClient::page,
                legacyCache = DiskCache(File(context.applicationContext.cacheDir, "card-pages"))
            ).also { instance = it }
        }

        private val labels = mapOf("name" to "姓名", "student_id" to "学号", "class_name" to "班级", "phone" to "手机号")
        fun validatePage(path: String, doc: Document) {
            val supported = when {
                path == "/me" -> CardHome.parse(doc) != null
                path.startsWith("/members/") -> doc.selectFirst(".card.full") != null
                path == "/members" -> doc.selectFirst("main ul") != null
                path == "/e" -> CardEventPage.parse(doc) != null
                path.startsWith("/e/") -> doc.selectFirst(".info h1") != null && doc.selectFirst(".pass-card") != null
                else -> false
            }
            check(supported) { "Card 页面结构已变化，请更新 App 后重试" }
        }

        fun profileSwatchHues(doc: Document): List<Int> =
            doc.select("form.form[method=post] .swatches button.swatch[data-hue]")
                .mapNotNull { it.attr("data-hue").toIntOrNull()?.takeIf { hue -> hue in 0..360 } }
                .distinct()

        fun checkInCode(card: Element): Element? {
            if (!card.hasClass("full") || card.attr("data-state") != "valid") return null
            return card.selectFirst(".ticket[data-ticket][data-check-in=open] > .under > svg.qr")
        }

        fun ticketNotice(doc: Document): String? =
            doc.selectFirst(".act .notice, .act .issued .keepsake")?.text()

        fun ticketQrSvg(code: Element): ByteArray {
            val document = Document("")
            document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false)
            val svg = code.clone().attr("xmlns", "http://www.w3.org/2000/svg")
                .attr("width", "256").attr("height", "256")
            document.appendChild(svg)
            return svg.outerHtml().toByteArray(Charsets.UTF_8)
        }

        fun claimFields(doc: Document): List<CardClaimField> = doc.select("form.claim input[name]").map { input ->
            val name = input.attr("name")
            val hidden = input.attr("type") == "hidden"
            check(hidden || name in labels) { "活动增加了未支持的报名字段，请在官方网页领取" }
            CardClaimField(name, labels[name] ?: name, input.`val`(), input.hasAttr("required"), input.attr("maxlength").toIntOrNull()?.coerceIn(1, 200) ?: 200, hidden)
        }

        fun claimError(doc: Document): String? {
            doc.selectFirst("form.claim .error")?.text()?.takeIf { it.isNotBlank() }?.let { return it }
            val state = doc.selectFirst(".pass-card.full")?.attr("data-state")
            return if (state in listOf("valid", "redeemed", "expired")) null
                else doc.selectFirst(".act .notice")?.text()?.takeIf { it.isNotBlank() } ?: "活动券尚未领取成功，请检查信息或名额后重试"
        }
    }
}
