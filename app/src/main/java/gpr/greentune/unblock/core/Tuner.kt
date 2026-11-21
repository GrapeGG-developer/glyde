package gpr.greentune.unblock.core

import android.content.Context
import gpr.greentune.unblock.App
import gpr.greentune.unblock.R
import gpr.greentune.unblock.data.ServiceChoice
import gpr.greentune.unblock.data.TuneResult
import gpr.greentune.unblock.data.TuneStore
import gpr.greentune.unblock.net.Backend
import gpr.greentune.unblock.net.Geo
import gpr.greentune.unblock.net.NetKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TuneProgress(
    val running: Boolean = false,
    val full: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val label: String = "",
    val message: String? = null,
) {
    val fraction: Float? get() = if (total > 0) done.toFloat() / total else null
}

object Tuner {
    private const val GENERAL = "_general"
    private val _progress = MutableStateFlow(TuneProgress())
    val progress: StateFlow<TuneProgress> get() = _progress
    private var job: Job? = null

    fun isRunning(): Boolean = job?.isActive == true

    fun start(context: Context, full: Boolean, only: Set<String>? = null) {
        if (isRunning()) return
        val app = context.applicationContext
        job = App.scope.launch {
            try {
                gpr.greentune.unblock.net.Live.busy { run(app, full, only) }
            } catch (e: CancellationException) {
                _progress.value = TuneProgress(message = app.getString(R.string.tune_cancelled))
                throw e
            } catch (e: Exception) {
                _progress.value = TuneProgress(message = app.getString(R.string.tune_failed, e.message ?: ""))
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun clearMessage() {
        if (!_progress.value.running) _progress.value = TuneProgress()
    }

    private data class Score(val ok: Int, val ms: Long)

    private suspend fun run(context: Context, full: Boolean, only: Set<String>?) {
        val all = Presets.active()
        val presets = if (only == null) all else all.copy(services = all.services.filter { it.id in only })
        if (presets.services.isEmpty() && only != null) {
            _progress.value = TuneProgress()
            return
        }
        _progress.value = TuneProgress(true, full, 0, 0, context.getString(R.string.tune_phase_network))
        val geo = Geo.lookup()
        val key = NetKey.key(context, geo?.asn)
        val label = geo?.label ?: NetKey.transport(context)

        _progress.value = TuneProgress(true, full, 0, 0, context.getString(R.string.tune_phase_baseline))
        val direct = Prober.client(null, 5000)
        val baseline = coroutineScope {
            presets.services.map { s ->
                async(Dispatchers.IO) { s.id to s.tests.map { url -> Prober.probe(direct, url) } }
            }.awaitAll().toMap()
        }
        val generalBase = coroutineScope {
            presets.generalTests.map { url -> async(Dispatchers.IO) { Prober.probe(direct, url) } }.awaitAll()
        }
        val blocked = presets.services.filter { s ->
            val r = baseline[s.id].orEmpty()
            r.isEmpty() || r.any { !it.ok }
        }
        val generalBlocked = only == null && generalBase.any { !it.ok }
        val directChoices = presets.services.filter { it !in blocked }.map { s ->
            val r = baseline[s.id].orEmpty()
            ServiceChoice(s.id, null, true, true, if (r.isEmpty()) 0 else r.sumOf { it.ms } / r.size)
        }

        if (blocked.isEmpty() && !generalBlocked) {
            finish(context, merge(only, TuneResult(key, label, System.currentTimeMillis(), full, null, directChoices)), geo?.asn, geo?.country, emptyMap())
            return
        }

        val ranking = try {
            Backend.best(geo?.asn)
        } catch (_: Exception) {
            emptyList()
        }
        val ordered = order(presets.strategies, ranking)
        val strategies = if (full) ordered else ordered.take(24)
        val timeout = if (full) 5500L else 4500L

        val probes = blocked.flatMap { s -> s.tests.map { s.id to it } } +
            (if (generalBlocked) presets.generalTests.map { GENERAL to it } else emptyList())
        val need = probes.groupingBy { it.first }.eachCount()
        val scores = ConcurrentHashMap<String, Map<String, Score>>()
        val done = AtomicInteger(0)
        val total = strategies.size
        val sem = Semaphore(if (probes.size > 24) 3 else if (full) 6 else 4)

        fun goodEnough(): Boolean {
            if (full || scores.size < 6) return false
            return need.all { (sid, n) -> scores.values.any { (it[sid]?.ok ?: 0) >= n } } &&
                scores.values.any { m -> need.all { (sid, n) -> (m[sid]?.ok ?: 0) >= n } }
        }

        _progress.value = TuneProgress(true, full, 0, total, context.getString(R.string.tune_phase_strategies))
        coroutineScope {
            for (st in strategies) {
                launch(Dispatchers.IO) {
                    sem.withPermit {
                        ensureActive()
                        if (!goodEnough()) {
                            val m = testStrategy(context, presets, st, probes, timeout)
                            if (m != null) scores[st.id] = m
                        }
                        val d = done.incrementAndGet()
                        _progress.value = TuneProgress(
                            true, full, d, total,
                            context.getString(R.string.tune_phase_strategy, st.id, d, total)
                        )
                    }
                }
            }
        }

        val index = strategies.withIndex().associate { it.value.id to it.index }
        fun bestFor(sid: String): Pair<String, Score>? = scores.entries
            .mapNotNull { (stId, m) -> m[sid]?.let { stId to it } }
            .filter { it.second.ok > 0 }
            .sortedWith(
                compareByDescending<Pair<String, Score>> { it.second.ok }
                    .thenBy { it.second.ms / maxOf(1, it.second.ok) }
                    .thenBy { index[it.first] ?: Int.MAX_VALUE }
            )
            .firstOrNull()

        val firstPass = blocked.map { s ->
            val b = bestFor(s.id)
            ServiceChoice(
                serviceId = s.id,
                strategyId = b?.first,
                direct = false,
                ok = b != null && b.second.ok >= (need[s.id] ?: 1),
                ms = b?.let { it.second.ms / maxOf(1, it.second.ok) } ?: 0,
            )
        }

        val defaultId = scores.entries
            .map { (stId, m) -> stId to Score(m.values.sumOf { it.ok }, m.values.sumOf { it.ms }) }
            .filter { it.second.ok > 0 }
            .sortedWith(
                compareByDescending<Pair<String, Score>> { it.second.ok }
                    .thenBy { it.second.ms / maxOf(1, it.second.ok) }
                    .thenBy { index[it.first] ?: Int.MAX_VALUE }
            )
            .firstOrNull()?.first

        _progress.value = TuneProgress(true, full, total, total, context.getString(R.string.tune_phase_verify))
        val verified = verify(context, all, firstPass, directChoices.map { it.serviceId }.toSet(), defaultId, timeout)
        val chosen = firstPass.map { ch ->
            val okNow = verified[ch.serviceId]
            when {
                okNow == true -> ch.copy(ok = true, route = "dpi")
                okNow == false && ch.ok -> ch.copy(ok = false, route = "none")
                else -> ch
            }
        }.toMutableList()

        val tgIdx = chosen.indexOfFirst { it.serviceId == "telegram" }
        if (tgIdx >= 0 && !chosen[tgIdx].ok) {
            _progress.value = TuneProgress(true, full, total, total, context.getString(R.string.tune_phase_proxy))
            if (withContext(Dispatchers.IO) { gpr.greentune.unblock.tg.TgBridge.probe() }) {
                chosen[tgIdx] = chosen[tgIdx].copy(ok = true, route = "proxy")
                gpr.greentune.unblock.data.SettingsStore.update { it.copy(tgBridge = true) }
            }
        }

        val result = TuneResult(key, label, System.currentTimeMillis(), full, defaultId, chosen + directChoices)
        finish(context, merge(only, result), geo?.asn, geo?.country, scores)
    }

    private suspend fun verify(
        context: Context,
        presets: PresetData,
        chosen: List<ServiceChoice>,
        direct: Set<String>,
        defaultId: String?,
        timeout: Long,
    ): Map<String, Boolean> {
        val map = chosen.filter { it.strategyId != null }.associate { it.serviceId to it.strategyId!! }
        val targets = chosen.filter { it.strategyId != null }.mapNotNull { presets.service(it.serviceId) }
        if (targets.isEmpty()) return emptyMap()
        val tokens = Args.compose(presets, map, direct, defaultId)
        val port = Ciadpi.freePort()
        val proc = withContext(Dispatchers.IO) { Ciadpi.start(context, port, tokens, false) }
        try {
            if (!Ciadpi.awaitReady(port, proc, 3000)) return emptyMap()
            val client = Prober.client(port, timeout + 1500)
            return coroutineScope {
                targets.map { s ->
                    async(Dispatchers.IO) {
                        val res = s.tests.map { Prober.probe(client, it) }
                        val ok = res.count { it.ok } >= maxOf(1, (s.tests.size + 1) / 2)
                        val again = if (ok) true else s.tests.map { Prober.probe(client, it) }.count { it.ok } >= maxOf(1, (s.tests.size + 1) / 2)
                        s.id to again
                    }
                }.awaitAll().toMap()
            }
        } catch (_: Exception) {
            return emptyMap()
        } finally {
            withContext(Dispatchers.IO) { Ciadpi.stop(proc) }
        }
    }

    private suspend fun testStrategy(
        context: Context,
        presets: PresetData,
        st: Strategy,
        probes: List<Pair<String, String>>,
        timeout: Long,
    ): Map<String, Score>? {
        val port = Ciadpi.freePort()
        val proc = withContext(Dispatchers.IO) { Ciadpi.start(context, port, Args.strategyTokens(presets, st), false) }
        try {
            if (!Ciadpi.awaitReady(port, proc, 2500)) return null
            val client = Prober.client(port, timeout)
            val res = coroutineScope {
                probes.map { (sid, url) -> async(Dispatchers.IO) { sid to Prober.probe(client, url) } }.awaitAll()
            }
            val m = mutableMapOf<String, Score>()
            for ((sid, r) in res) {
                val cur = m[sid] ?: Score(0, 0)
                m[sid] = if (r.ok) Score(cur.ok + 1, cur.ms + r.ms) else cur
            }
            return m
        } finally {
            withContext(Dispatchers.IO) { Ciadpi.stop(proc) }
        }
    }

    private fun merge(only: Set<String>?, fresh: TuneResult): TuneResult {
        if (only == null) return fresh
        val prev = TuneStore.get(fresh.networkKey) ?: return fresh
        val kept = prev.choices.filter { it.serviceId !in only }
        return prev.copy(
            time = fresh.time,
            choices = kept + fresh.choices,
            defaultStrategy = prev.defaultStrategy ?: fresh.defaultStrategy,
        )
    }

    private fun order(all: List<Strategy>, ranking: List<String>): List<Strategy> {
        if (ranking.isEmpty()) return all
        val pos = ranking.withIndex().associate { it.value to it.index }
        return all.sortedBy { pos[it.id] ?: (ranking.size + all.indexOf(it)) }
    }

    private suspend fun finish(
        context: Context,
        result: TuneResult,
        asn: String?,
        country: String?,
        scores: Map<String, Map<String, Score>>,
    ) {
        TuneStore.save(result)
        if (Engine.isActive()) Engine.reload(context)
        val okCount = result.choices.count { it.ok }
        _progress.value = TuneProgress(
            message = context.getString(R.string.tune_done, okCount, result.choices.size)
        )
        try {
            val body = JSONObject()
                .put("asn", asn ?: JSONObject.NULL)
                .put("country", country ?: JSONObject.NULL)
                .put("full", result.full)
                .put("default", result.defaultStrategy ?: JSONObject.NULL)
                .put("choices", JSONArray().apply {
                    result.choices.forEach {
                        put(
                            JSONObject().put("service", it.serviceId)
                                .put("strategy", it.strategyId ?: JSONObject.NULL)
                                .put("direct", it.direct).put("ok", it.ok).put("ms", it.ms).put("route", it.route)
                        )
                    }
                })
                .put("scores", JSONObject().apply {
                    scores.forEach { (st, m) -> put(st, m.values.sumOf { it.ok }) }
                })
            Backend.report(body)
        } catch (_: Exception) {
        }
    }
}
