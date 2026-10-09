package dev.debugkit.network.internal.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import dev.debugkit.network.internal.MockRule
import dev.debugkit.network.internal.TransactionSummary
import kotlinx.coroutines.flow.Flow

/**
 * Blocking calls run on OkHttp's threads from the interceptor; suspend calls come from the UI.
 * Destructive migrations are fine: this is debug data, and losing it on a schema bump is cheaper
 * than maintaining migrations for it.
 */
@Database(entities = [TransactionEntity::class, MockRule::class], version = 1, exportSchema = false)
internal abstract class NetworkDatabase : RoomDatabase() {

    abstract fun transactions(): TransactionDao

    abstract fun mockRules(): MockRuleDao

    companion object {
        @Volatile
        private var instance: NetworkDatabase? = null

        fun get(context: Context): NetworkDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NetworkDatabase::class.java, "debugkit-network.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}

@Dao
internal interface TransactionDao {

    @Insert
    fun insert(transaction: TransactionEntity): Long

    @Update
    fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query(
        "SELECT id, requestedAt, method, url, durationMs, responseCode, responseBodySize, error, mockRuleName " +
            "FROM transactions ORDER BY id DESC",
    )
    fun observeSummaries(): Flow<List<TransactionSummary>>

    @Query("DELETE FROM transactions WHERE id NOT IN (SELECT id FROM transactions ORDER BY id DESC LIMIT :keep)")
    fun trim(keep: Int)

    /** Calls still open from an earlier process will never complete; mark them so they don't look pending forever. */
    @Query("UPDATE transactions SET error = :message WHERE responseCode IS NULL AND error IS NULL AND requestedAt < :before")
    suspend fun failUnfinished(before: Long, message: String)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
internal interface MockRuleDao {

    @Query("SELECT * FROM mock_rules ORDER BY id")
    fun observeAll(): Flow<List<MockRule>>

    @Query("SELECT * FROM mock_rules ORDER BY id")
    fun getAll(): List<MockRule>

    @Upsert
    suspend fun upsert(rule: MockRule)

    @Query("UPDATE mock_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM mock_rules WHERE id = :id")
    suspend fun delete(id: Long)
}
