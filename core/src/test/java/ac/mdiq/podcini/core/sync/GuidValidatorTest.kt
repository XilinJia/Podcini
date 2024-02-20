package ac.mdiq.podcini.core.sync

import ac.mdiq.podcini.core.sync.GuidValidator.isValidGuid
import junit.framework.TestCase

class GuidValidatorTest : TestCase() {
    fun testIsValidGuid() {
        assertTrue(isValidGuid("skfjsdvgsd"))
    }

    fun testIsInvalidGuid() {
        assertFalse(isValidGuid(""))
        assertFalse(isValidGuid(" "))
        assertFalse(isValidGuid("\n"))
        assertFalse(isValidGuid(" \n"))
        assertFalse(isValidGuid(null))
        assertFalse(isValidGuid("null"))
    }
}