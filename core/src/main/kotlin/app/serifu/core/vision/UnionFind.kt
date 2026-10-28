package app.serifu.core.vision

class UnionFind(private val size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size)

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        var node = x
        while (parent[node] != root) {
            val next = parent[node]
            parent[node] = root
            node = next
        }
        return root
    }

    fun union(a: Int, b: Int) {
        val left = find(a)
        val right = find(b)
        if (left == right) return
        when {
            rank[left] < rank[right] -> parent[left] = right
            rank[left] > rank[right] -> parent[right] = left
            else -> {
                parent[right] = left
                rank[left]++
            }
        }
    }

    fun groups(): List<List<Int>> {
        val buckets = HashMap<Int, MutableList<Int>>()
        for (index in 0 until size) {
            buckets.getOrPut(find(index)) { mutableListOf() }.add(index)
        }
        return buckets.values.toList()
    }
}
