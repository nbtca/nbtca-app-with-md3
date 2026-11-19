package space.nbtca.app.core.academic

import android.webkit.CookieManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 学期选项（学年 / 学期）。 */
data class TermOption(val value: String, val label: String, val selected: Boolean = false)

data class TermCatalog(
    val years: List<TermOption>,
    val semesters: List<TermOption>,
    val selectedYear: String,
    val selectedSemester: String
)

/**
 * 网页内同步：在已登录的 WebView 里用页面同源 fetch 取数据，
 * 绕开"把浏览器 Cookie 搬到 OkHttp"被判未登录的问题。
 */
object AcademicWebSync {

    internal const val PORTAL_URL = "https://webvpn.nbt.edu.cn/"
    internal const val AUTH_ORIGIN = "https://authserver-443.webvpn.nbt.edu.cn"
    internal const val JWXT_ORIGIN = "https://jwxt-443.webvpn.nbt.edu.cn"
    internal const val JWXT_SSO_URL = "$JWXT_ORIGIN/sso/jziotlogin"
    internal const val JWXT_MENU_PATH = "/jwglxt/xtgl/index_initMenu.html"
    internal const val JWXT_MENU_URL = "$JWXT_ORIGIN$JWXT_MENU_PATH"
    internal const val TIMETABLE_PATH = "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html"
    internal const val TIMETABLE_URL = "$JWXT_ORIGIN$TIMETABLE_PATH?gnmkdm=N2151&layout=default"
    internal const val TIMETABLE_DATA_PATH = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html"

    internal val PORTAL_READY_SCRIPT = """
        (() => location.origin === 'https://webvpn.nbt.edu.cn'
          && (document.body?.innerText ?? '').includes('常用站点')
          && !document.querySelector('#pwdEncryptSalt, input[type="password"]'))()
    """.trimIndent()

    private val timetableRequests = """
        const enc = (values) => new URLSearchParams(values).toString();
        const H = async (url, options) => {
          const res = await fetch(url, Object.assign({ credentials: 'include', cache: 'no-store' }, options));
          if (!res.ok) throw new Error('教务请求失败（HTTP ' + res.status + '）');
          if (new URL(res.url).origin !== '$JWXT_ORIGIN') throw new Error('登录状态已失效，请重新登录');
          return res;
        };
        const timetableRequest = (xnm, xqm) => ({
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest' },
          body: enc({ xnm, xqm })
        });
        const identifyStudent = async (timetable) => {
          const data = JSON.parse(timetable);
          const identity = data?.xsxx;
          const studentId = [identity?.xh, identity?.XH]
            .find(value => (typeof value === 'string' && value.trim()) || typeof value === 'number');
          if (studentId != null) return String(studentId).trim();

          // Some timetable responses only contain course rows. Check the authenticated menu for the ID.
          const menu = await H('$JWXT_MENU_PATH');
          const doc = new DOMParser().parseFromString(await menu.text(), 'text/html');
          const alternate = doc.querySelector('input#xh_id, input[name="xh_id"]')?.value?.trim();
          if (alternate && /^\d{6,}$/.test(alternate)
              && !doc.querySelector('#pwdEncryptSalt, input[type="password"]')) return alternate;
          throw new Error('未能确认当前教务学号，请重新登录');
        };
    """.trimIndent()

    internal val TIMETABLE_IDENTITY_SCRIPT = """
        (() => {
          if (location.origin !== '$JWXT_ORIGIN' || location.pathname !== '$TIMETABLE_PATH'
              || document.querySelector('#pwdEncryptSalt, input[type="password"]')) return null;
          if (window.__nbtIdentityStarted) return window.__nbtStudentId ?? null;
          const choice = (selector) => {
            const select = document.querySelector(selector);
            return select?.value || Array.from(select?.options ?? []).find(option => !option.disabled && option.value)?.value;
          };
          const year = choice('select#xnm, select[name="xnm"]');
          const semester = choice('select#xqm, select[name="xqm"]');
          if (!year || !semester) return null;

          window.__nbtIdentityStarted = true;
          $timetableRequests
          H('$TIMETABLE_DATA_PATH', timetableRequest(year, semester))
            .then(res => res.text())
            .then(identifyStudent)
            .then(studentId => { window.__nbtStudentId = studentId; })
            .catch(() => { window.__nbtStudentId = ''; });
          return null;
        })()
    """.trimIndent()

    internal fun sessionCookies(): String? {
        val manager = CookieManager.getInstance()
        val timetableCookies = manager.getCookie(TIMETABLE_URL)?.takeIf { it.isNotBlank() } ?: return null
        return listOf(manager.getCookie(AUTH_ORIGIN), manager.getCookie(PORTAL_URL), timetableCookies)
            .filterNotNull()
            .flatMap { it.split(';') }
            .mapNotNull { cookie ->
                val name = cookie.substringBefore('=').trim()
                val value = cookie.substringAfter('=', "").trim()
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }
            .toMap()
            .entries
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    internal fun decodeStudentId(result: String): String =
        runCatching { json.parseToJsonElement(result).jsonPrimitive.contentOrNull.orEmpty().trim() }
            .getOrDefault("")

    /** 读取可选学期列表。 */
    /** 读取可选学期列表：先看当前页面/子框架，再依次尝试多个课表入口 URL。 */
    /** 读取可选学期列表：当前页面/子框架 → 多个课表入口；结果写 localStorage 防页面刷新丢失。 */
    val TERMS_SCRIPT: String = """
        (function () {
          const KEY = 'nbt_terms';
          window.__nbtTerms = null;
          try { localStorage.removeItem(KEY); } catch (e) {}
          const emit = (v) => { window.__nbtTerms = v; try { localStorage.setItem(KEY, v); } catch (e) {} };
          const pickIn = (doc) => {
            if (!doc) return null;
            const y = doc.getElementById('xnm'), s = doc.getElementById('xqm');
            if (!y || !s) return null;
            const current = (el) => {
              const sel = el.querySelector('option[selected]') || el.options[el.options.length - 1];
              return sel ? sel.value : '';
            };
            const items = (el) => Array.from(el.options).map(o => ({
              v: o.value || (o.textContent || '').trim(),
              t: (o.textContent || '').trim()
            })).filter(o => o.v && o.t);
            return { yHtml: y.outerHTML || '', sHtml: s.outerHTML || '' };
          };
          (async () => {
            let found = pickIn(document);
            if (!found) {
              for (let i = 0; i < window.frames.length && !found; i++) {
                try { found = pickIn(window.frames[i].document); } catch (e) {}
              }
            }
            const urls = [
              '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default',
              '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default&jsdm=xs',
              '/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151'
            ];
            let debug = '';
            for (const u of urls) {
              if (found) break;
              try {
                const res = await fetch(u, { credentials: 'include', headers: { 'X-Requested-With': 'XMLHttpRequest' } });
                const html = await res.text();
                debug += u + '=>' + res.status + '/' + html.length + '/xnm=' + (html.indexOf('id="xnm"') >= 0) + '; ';
                found = pickIn(new DOMParser().parseFromString(html, 'text/html'));
              } catch (e) {
                debug += u + '=>err ' + e + '; ';
              }
            }
            emit(found
              ? JSON.stringify(Object.assign({ debug: debug }, found))
              : JSON.stringify({ error: 'no-term', debug: debug, href: location.href }));
          })().catch(e => emit(JSON.stringify({ error: String(e) })));
        })();
    """.trimIndent()

    /** 指定学期的课表 / 考试 / 成绩。 */
    fun dataScript(term: AcademicTerm, requestId: String): String = """
        (function () {
          const requestId = ${json.encodeToString(requestId)};
          window.__nbtSyncRequest = requestId;
          window.__nbtSync = null;
          const emit = (result) => {
            if (window.__nbtSyncRequest === requestId) window.__nbtSync = { requestId, result };
          };
          $timetableRequests
          const XNM = ${json.encodeToString(term.academicYear)}, XQM = ${json.encodeToString(term.semester)};
          (async () => {
            if (location.origin !== '$JWXT_ORIGIN') throw new Error('登录状态已失效，请重新登录');
            const request = timetableRequest(XNM, XQM);
            const ttRes = await H('$TIMETABLE_DATA_PATH', request);
            const tt = await ttRes.text();
            const studentId = await identifyStudent(tt);
            const periodsAbort = new AbortController();
            const periodsTimeout = setTimeout(() => periodsAbort.abort(), 5000);
            const periods = H('$TIMETABLE_PERIODS_PATH', Object.assign({}, request, { signal: periodsAbort.signal }))
              .then(res => res.text()).catch(() => null).finally(() => clearTimeout(periodsTimeout));
            const pages = async (path, code, sort) => {
              let out = [], page = 1, total = 1;
              while (page <= total && page <= 5) {
                const body = enc({ xnm: XNM, xqm: XQM, 'queryModel.showCount': '50', 'queryModel.currentPage': String(page), 'queryModel.sortName': sort, 'queryModel.sortOrder': 'asc' });
                const res = await H(path + '?doType=query&gnmkdm=' + code, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest' },
                  body
                });
                const text = await res.text();
                let data = null;
                try { data = JSON.parse(text); } catch (e) { throw new Error('教务返回了非 JSON 响应，请重新登录后重试'); }
                if (!data || !Array.isArray(data.items)) throw new Error('教务返回的数据格式异常，请重试');
                out = out.concat(data.items);
                total = data.totalPage || 1;
                page += 1;
              }
              return out;
            };
            const exams = await pages('/jwglxt/kwgl/kscx_cxXsksxxIndex.html', 'N358105', 'kssj');
            const grades = await pages('/jwglxt/cjcx/cjcx_cxXsgrcj.html', 'N305005', 'kch');
            emit(JSON.stringify({ studentId, tt, periods: await periods, exams, grades }));
          })().catch(e => emit(JSON.stringify({ error: String(e) })));
        })();
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun unwrap(rawResult: String): JsonObject {
        val outer = json.parseToJsonElement(rawResult)
        val text = (outer as? JsonPrimitive)?.contentOrNull ?: rawResult
        return json.parseToJsonElement(text).jsonObject
    }

    fun parseTerms(rawResult: String): TermCatalog {
        val root = unwrap(rawResult)
        (root["error"] as? JsonPrimitive)?.contentOrNull?.let { code ->
            android.util.Log.e("AcademicSync", "terms failed: $code")
            val debug = root["debug"]?.jsonPrimitive?.contentOrNull ?: ""
            throw AcademicException("未取到学期列表（$code）${if (debug.isBlank()) "" else "：$debug"}")
        }
        val years = parseOptions(root["yHtml"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val semesters = parseOptions(root["sHtml"]?.jsonPrimitive?.contentOrNull.orEmpty())
        if (years.isEmpty() || semesters.isEmpty()) {
            android.util.Log.e("AcademicSync", "terms empty")
            throw AcademicException("未取到可选学期，请重试")
        }
        // 学年按标签升序展示；默认选中当前学年（8 月之后算新学年）
        val sortedYears = years.sortedBy { it.label }
        val today = java.time.LocalDate.now()
        val currentStart = if (today.monthValue >= 8) today.year else today.year - 1
        val currentYear = sortedYears.firstOrNull { it.label.startsWith("$currentStart-") }
        val defaultSemester = when (today.monthValue) {
            in 2..7 -> "12"
            in 8..12 -> "3"
            else -> "16"
        }
        return TermCatalog(
            years = sortedYears,
            semesters = semesters,
            selectedYear = currentYear?.value ?: sortedYears.first().value,
            selectedSemester = semesters.firstOrNull { it.value == defaultSemester }?.value
                ?: semesters.firstOrNull { it.selected }?.value
                ?: semesters.first().value
        )
    }

    /** 从 <select> 原始 HTML 里解析 option（JS 侧在该页面取 options 会返回空）。 */
    private fun parseOptions(html: String): List<TermOption> {
        if (html.isBlank()) return emptyList()
        val regex = Regex("<option\\b([^>]*)>([\\s\\S]*?)</option>", RegexOption.IGNORE_CASE)
        return regex.findAll(html).mapNotNull { match ->
            val attrs = match.groupValues[1]
            val value = Regex("value\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            val label = match.groupValues[2].replace(Regex("<[^>]*>"), " ").trim()
            if (value.isEmpty() || label.isEmpty()) null
            else TermOption(value, label, Regex("\\bselected\\b", RegexOption.IGNORE_CASE).containsMatchIn(attrs))
        }.toList()
    }

    internal fun parse(rawResult: String, term: AcademicTerm): AcademicSnapshot {
        val root = unwrap(rawResult)
        (root["error"] as? JsonPrimitive)?.contentOrNull?.let {
            throw AcademicException("教务网页请求失败：$it")
        }
        // 接口全部校验成功后才覆盖本地快照，避免把登录页保存为空课表。
        val timetableRaw = root["tt"]?.jsonPrimitive?.contentOrNull
            ?: throw AcademicException("教务未返回课表数据")
        val timetableJson = runCatching { json.parseToJsonElement(timetableRaw) }.getOrNull()
        if (timetableJson !is JsonArray &&
            (timetableJson !is JsonObject || timetableJson["kbList"] !is JsonArray)) {
            throw AcademicException("课表响应异常或登录已失效，请重新登录后重试")
        }
        val exams = root["exams"] as? JsonArray ?: throw AcademicException("教务未返回考试数据")
        val grades = root["grades"] as? JsonArray ?: throw AcademicException("教务未返回成绩数据")
        val studentId = (root["studentId"] as? JsonPrimitive)?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw AcademicException("未能确认当前教务学号，请重新登录")
        val metadata = TimetableMetadata(timetableRaw, (root["periods"] as? JsonPrimitive)?.contentOrNull)
        return AcademicSnapshot(
            studentId = studentId,
            termLabel = term.label,
            timetable = AcademicClient.parseTimetableRaw(timetableRaw),
            exams = AcademicClient.parseExamRows(exams),
            grades = AcademicClient.parseGradeRows(grades),
            updatedAt = System.currentTimeMillis(),
            timetablePeriods = metadata.periods,
            timetableDays = metadata.days,
            term = term
        )
    }
}
