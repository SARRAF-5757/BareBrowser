package com.barebrowser.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY lastAccessed DESC")
    fun getAllTabs(): Flow<List<Tab>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun getTabById(id: Long): Tab?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: Tab): Long

    @Update
    suspend fun updateTab(tab: Tab)

    @Delete
    suspend fun deleteTab(tab: Tab)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteTabById(id: Long)
}
