package dev.debugkit.network.internal

import dev.debugkit.network.internal.db.NetworkDatabase
import dev.debugkit.network.internal.db.TransactionDao
import dev.debugkit.network.internal.db.toEntity
import dev.debugkit.network.internal.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Recorded calls, newest first, kept across restarts so the calls leading up to a crash are
 * still there on the next launch. Shared by every interceptor so apps with several
 * OkHttpClients see one list.
 */
internal object TransactionStore {

    private const val MAX_TRANSACTIONS = 500

    private lateinit var dao: TransactionDao

    /** One instance for the process, so composables collecting it never restart on recomposition. */
    lateinit var summaries: Flow<List<TransactionSummary>>
        private set

    /**
     * In-memory copy of [summaries] for crash records: crashes usually happen on the main thread,
     * where Room refuses to query.
     */
    @Volatile
    var latest: List<TransactionSummary> = emptyList()
        private set

    fun init(database: NetworkDatabase) {
        dao = database.transactions()
        summaries = dao.observeSummaries()
        NetworkModule.scope.launch { summaries.collect { latest = it } }
        val startedAt = System.currentTimeMillis()
        NetworkModule.scope.launch {
            dao.failUnfinished(before = startedAt, message = "App closed before the response arrived")
        }
    }

    fun observe(id: Long): Flow<HttpTransaction?> = dao.observe(id).map { it?.toModel() }

    /** Blocking; call from OkHttp's threads. */
    fun add(transaction: HttpTransaction): Long {
        val id = dao.insert(transaction.toEntity())
        dao.trim(MAX_TRANSACTIONS)
        return id
    }

    /** Blocking; call from OkHttp's threads. Each call is only ever updated by its own interceptor. */
    fun update(id: Long, transform: (HttpTransaction) -> HttpTransaction) {
        val current = dao.get(id)?.toModel() ?: return
        dao.update(transform(current).toEntity())
    }

    fun clear() {
        NetworkModule.scope.launch { dao.clear() }
    }
}
