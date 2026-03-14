package com.tencent.tim.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastUpdateTs DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE openid = :openid LIMIT 1")
    suspend fun getAccountByOpenid(openid: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE openid = :openid")
    suspend fun deleteAccountByOpenid(openid: String)

    @Query("UPDATE accounts SET isSelected = 0")
    suspend fun clearAllSelected()

    @Transaction
    suspend fun setSelectedAccount(openid: String) {
        clearAllSelected()
        updateSelected(openid, true)
    }

    @Query("UPDATE accounts SET isSelected = :selected WHERE openid = :openid")
    suspend fun updateSelected(openid: String, selected: Boolean)

    @Query("SELECT * FROM accounts WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedAccount(): AccountEntity?
}
