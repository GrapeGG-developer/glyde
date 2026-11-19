package gpr.greentune.unblock.core

object Args {
    private const val MAX_GROUPS = 60

    fun tokenize(input: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var has = false
        for (ch in input) {
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '"' || ch == '\'') -> {
                    quote = ch
                    has = true
                }
                quote == null && ch.isWhitespace() -> {
                    if (has) {
                        out.add(cur.toString())
                        cur.setLength(0)
                        has = false
                    }
                }
                else -> {
                    cur.append(ch)
                    has = true
                }
            }
        }
        if (has) out.add(cur.toString())
        return out
    }

    fun withSni(args: String, sni: String): String = args.replace("{sni}", sni)

    fun isAutoToken(t: String): Boolean =
        t == "-A" || t == "--auto" || t.startsWith("--auto=") || (t.startsWith("-A") && !t.startsWith("--") && t.length > 2)

    fun groups(tokens: List<String>): List<List<String>> {
        val out = mutableListOf<MutableList<String>>(mutableListOf())
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (isAutoToken(t)) {
                val g = mutableListOf(t)
                if ((t == "-A" || t == "--auto") && i + 1 < tokens.size) {
                    g.add(tokens[i + 1])
                    i++
                }
                out.add(g)
            } else {
                out.last().add(t)
            }
            i++
        }
        if (out.size > 1 && out[0].isEmpty()) out.removeAt(0)
        return out
    }

    fun strategyTokens(presets: PresetData, strategy: Strategy): List<String> =
        tokenize(withSni(strategy.args, presets.fakeSni))

    fun compose(
        presets: PresetData,
        choices: Map<String, String>,
        direct: Set<String>,
        defaultId: String?,
    ): List<String> {
        val out = mutableListOf<String>()
        var count = 0

        fun add(gs: List<List<String>>, limiter: List<String>) {
            for (g in gs) {
                val startsAuto = g.isNotEmpty() && isAutoToken(g[0])
                if (!startsAuto && out.isNotEmpty()) out.add("-An")
                out.addAll(g)
                out.addAll(limiter)
                count++
            }
        }

        val directDomains = presets.services.filter { it.id in direct }.flatMap { it.domains }.distinct()
        if (directDomains.isNotEmpty()) {
            add(listOf(emptyList()), listOf("-Kt,h", "-H", ":" + directDomains.joinToString(" ")))
        }

        val byStrategy = choices.entries.groupBy({ it.value }, { it.key })
        for ((sid, serviceIds) in byStrategy) {
            val st = presets.strategy(sid) ?: continue
            val gs = groups(strategyTokens(presets, st))
            val services = serviceIds.mapNotNull { presets.service(it) }
            val domains = services.flatMap { it.domains }.distinct()
            val ips = services.flatMap { it.ipset }.distinct()
            val need = gs.size * ((if (domains.isNotEmpty()) 1 else 0) + (if (ips.isNotEmpty()) 1 else 0))
            if (count + need > MAX_GROUPS) continue
            if (domains.isNotEmpty()) add(gs, listOf("-H", ":" + domains.joinToString(" ")))
            if (ips.isNotEmpty()) add(gs, listOf("-j", ":" + ips.joinToString(" ")))
        }

        presets.strategy(defaultId)?.let { st ->
            val gs = groups(strategyTokens(presets, st))
            if (count + gs.size <= MAX_GROUPS) add(gs, emptyList())
        }
        return out
    }
}
