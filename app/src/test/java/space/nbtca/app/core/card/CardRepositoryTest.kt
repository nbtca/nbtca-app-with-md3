package space.nbtca.app.core.card
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
class CardRepositoryTest {
    @Test fun readsMaintainerCreditClaimFields() {
        val doc=Jsoup.parse("""<form class="claim"><input name="name" required maxlength="20" value="A"><input name="student_id" required maxlength="10"><input name="class_name" required><input name="phone" required maxlength="11"></form>""")
        val fields=CardRepository.claimFields(doc)
        assertEquals(listOf("name","student_id","class_name","phone"),fields.map {it.name})
        assertTrue(fields.all {it.required})
        assertEquals(11,fields.last().maxLength)
    }
    @Test fun validationErrorIsNotASuccessfulClaim() {
        val doc=Jsoup.parse("""<div class="pass-card full" data-state="preview"></div><form class="claim"><p class="notice error">学号不正确</p></form>""")
        assertEquals("学号不正确",CardRepository.claimError(doc))
        assertNotNull(CardRepository.claimError(Jsoup.parse("""<div class="pass-card full" data-state="preview"></div>""")))
        assertNull(CardRepository.claimError(Jsoup.parse("""<div class="pass-card full" data-state="valid"></div>""")))
    }
    @Test(expected=IllegalStateException::class) fun rejectsUnknownRequiredFormField() {
        CardRepository.claimFields(Jsoup.parse("""<form class="claim"><input name="new_requirement" required></form>"""))
    }
    @Test(expected=IllegalStateException::class) fun rejectsChangedProfileMarkup() {
        CardRepository.validatePage("/me",Jsoup.parse("<main>新版页面</main>"))
    }

    @Test fun readsHuePresetsFromTheProfileForm() {
        val doc = Jsoup.parse("""
            <article class="card full"></article>
            <form class="panel form reveal" method="post">
              <div class="swatches">
                <button type="button" class="swatch" data-hue="212" aria-pressed="true"></button>
                <button type="button" class="swatch" data-hue="18" aria-pressed="false"></button>
                <button type="button" class="swatch" data-hue="361"></button>
                <button type="button" class="swatch" data-hue="212"></button>
              </div>
              <input type="range" name="hue" value="212" />
            </form>
            <button class="swatch" data-hue="42"></button>
        """.trimIndent())
        CardRepository.validatePage("/me", doc)
        assertEquals(listOf(212, 18), CardRepository.profileSwatchHues(doc))
    }

    @Test fun usesOnlyTheLiveCheckInCode() {
        val doc = Jsoup.parse("""
            <div class="pass-card full" data-state="valid">
              <div class="stub"><svg class="qr" aria-hidden="true"><path d="stub" /></svg></div>
              <div class="ticket" data-ticket="fixture" data-check-in="open">
                <div class="under"><svg class="qr" aria-label="入场码"><path d="live" /></svg></div>
                <div class="frame" data-peel></div>
              </div>
            </div>
        """.trimIndent())
        val card = requireNotNull(doc.selectFirst(".pass-card"))
        assertEquals("live", CardRepository.checkInCode(card)?.selectFirst("path")?.attr("d"))
        requireNotNull(card.selectFirst(".ticket")).attr("data-check-in", "locked")
        assertNull(CardRepository.checkInCode(card))
        requireNotNull(card.selectFirst(".ticket")).attr("data-check-in", "open")
        for (state in listOf("preview", "expired", "redeemed")) {
            card.attr("data-state", state)
            assertNull(CardRepository.checkInCode(card))
        }
        assertNull(CardRepository.checkInCode(card.attr("data-state", "valid").removeClass("full").addClass("thumb")))
    }
}
