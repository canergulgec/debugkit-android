package dev.debugkit.network.internal

import dev.debugkit.network.internal.db.MockRuleDao
import dev.debugkit.network.internal.db.NetworkDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Persisted mock rules. The interceptor matches against an in-memory copy kept in sync with the
 * database, so a call never waits on a query once the first one has loaded it.
 */
internal object MockRuleStore {

    private lateinit var dao: MockRuleDao

    @Volatile
    private var cached: List<MockRule>? = null

    /** One instance for the process, so composables collecting it never restart on recomposition. */
    lateinit var rules: Flow<List<MockRule>>
        private set

    fun init(database: NetworkDatabase) {
        dao = database.mockRules()
        rules = dao.observeAll()
        NetworkModule.scope.launch { rules.collect { cached = it } }
    }

    /** Enabled rules as last loaded, without touching the database; safe on any thread. */
    val enabledRules: List<MockRule> get() = cached.orEmpty().filter { it.enabled }

    /** First enabled rule matching the call, in creation order. Blocking on first use only. */
    fun match(method: String, url: String): MockRule? {
        val rules = cached ?: dao.getAll().also { cached = it }
        return rules.firstOrNull { it.enabled && MockMatcher.matches(it, method, url) }
    }

    fun save(rule: MockRule) {
        NetworkModule.scope.launch { dao.upsert(rule) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        NetworkModule.scope.launch { dao.setEnabled(id, enabled) }
    }

    fun delete(id: Long) {
        NetworkModule.scope.launch { dao.delete(id) }
    }
}
