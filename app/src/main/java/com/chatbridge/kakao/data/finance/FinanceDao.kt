package com.chatbridge.kakao.data.finance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM finance_snapshots ORDER BY capturedAt DESC")
    fun observeSnapshots(): Flow<List<FinanceSnapshot>>

    @Insert
    suspend fun insert(snapshot: FinanceSnapshot): Long
}
