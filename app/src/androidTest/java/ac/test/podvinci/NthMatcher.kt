package de.test.podvinci

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import java.util.concurrent.atomic.AtomicInteger

object NthMatcher {
    fun <T> first(matcher: Matcher<T>): Matcher<T> {
        return nth(matcher, 1)
    }

    fun <T> nth(matcher: Matcher<T>, index: Int): Matcher<T> {
        return object : BaseMatcher<T>() {
            var count: AtomicInteger = AtomicInteger(0)

            override fun matches(item: Any): Boolean {
                if (matcher.matches(item)) {
                    return count.incrementAndGet() == index
                }
                return false
            }

            override fun describeTo(description: Description) {
                description.appendText("Item #$index ")
                description.appendDescriptionOf(matcher)
            }
        }
    }
}
