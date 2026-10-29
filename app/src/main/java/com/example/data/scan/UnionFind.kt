package com.example.data.scan

import com.example.domain.model.MatchType

class UnionFind(n: Int) {
    private val p = IntArray(n) { it }

    fun find(x: Int): Int {
        var r = x
        while (p[r] != r) {
            p[r] = p[p[r]]
            r = p[r]
        }
        return r
    }

    fun union(a: Int, b: Int) {
        p[find(a)] = find(b)
    }
}

data class Edge(val a: Int, val b: Int, val type: MatchType, val score: Float)
