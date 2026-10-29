package com.example

import com.example.data.scan.UnionFind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UnionFindTest {

    @Test
    fun testInitialComponentsAreDisjoint() {
        val uf = UnionFind(5)
        for (i in 0 until 5) {
            assertEquals(i, uf.find(i))
        }
    }

    @Test
    fun testUnionMergesComponents() {
        val uf = UnionFind(6)
        uf.union(0, 1)
        uf.union(1, 2)

        assertEquals(uf.find(0), uf.find(2))
        assertEquals(uf.find(1), uf.find(2))

        uf.union(3, 4)
        assertEquals(uf.find(3), uf.find(4))

        assertNotEquals(uf.find(0), uf.find(3))

        uf.union(2, 4)
        assertEquals(uf.find(0), uf.find(3))
        assertNotEquals(uf.find(0), uf.find(5))
    }
}
