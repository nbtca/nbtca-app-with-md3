package space.nbtca.app.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun stableReleaseReplacesPrereleaseOfSameVersion() {
        assertTrue(UpdateChecker.isNewer("v1.0.1", "1.0.1-beta.4"))
        assertFalse(UpdateChecker.isNewer("v1.0.1-beta.4", "1.0.1"))
    }

    @Test
    fun prereleasesUseNumericSuffixAndReleaseStage() {
        assertTrue(UpdateChecker.isNewer("v1.0.1-beta.10", "1.0.1-beta.9"))
        assertTrue(UpdateChecker.isNewer("v1.0.1-rc.1", "1.0.1-beta.10"))
        assertFalse(UpdateChecker.isNewer("v1.0.1-beta.10", "1.0.1-rc.1"))
    }

    @Test
    fun buildMetadataDoesNotChangeVersionPrecedence() {
        assertFalse(UpdateChecker.isNewer("v1.0.1+build.2", "1.0.1+build.1"))
    }
}
