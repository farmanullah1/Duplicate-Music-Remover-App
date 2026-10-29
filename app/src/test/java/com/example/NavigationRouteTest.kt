package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteTest {

    private fun matchesRoute(currentRoute: String?, targetRoute: String): Boolean {
        if (currentRoute == null) return false
        if (currentRoute == targetRoute) return true
        val baseCurrent = currentRoute.substringBefore('?').substringBefore('/')
        val baseTarget = targetRoute.substringBefore('?').substringBefore('/')
        return baseCurrent == baseTarget
    }

    @Test
    fun testExactRouteMatches() {
        assertTrue(matchesRoute("home", "home"))
        assertTrue(matchesRoute("duplicates", "duplicates"))
        assertTrue(matchesRoute("junk", "junk"))
        assertTrue(matchesRoute("recycle", "recycle"))
        assertTrue(matchesRoute("settings", "settings"))
    }

    @Test
    fun testParameterizedDuplicatesRouteMatchesTopLevel() {
        // When navigating to duplicates with category filter or query args
        assertTrue(matchesRoute("duplicates?category=AUDIO", "duplicates"))
        assertTrue(matchesRoute("duplicates?category=IMAGE", "duplicates"))
        assertTrue(matchesRoute("duplicates/123", "duplicates"))
    }

    @Test
    fun testUnrelatedRouteDoesNotMatch() {
        assertFalse(matchesRoute("compare/42", "duplicates"))
        assertFalse(matchesRoute("settings", "home"))
        assertFalse(matchesRoute(null, "home"))
    }
}

