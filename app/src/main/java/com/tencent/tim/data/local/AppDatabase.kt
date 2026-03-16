package com.tencent.tim.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AccountEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN isSelected INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounts_new (
                        openid TEXT NOT NULL,
                        roleName TEXT NOT NULL,
                        roleId TEXT NOT NULL,
                        level TEXT NOT NULL,
                        isOnline TEXT NOT NULL,
                        isBan TEXT NOT NULL,
                        isFace TEXT NOT NULL,
                        aceMark TEXT NOT NULL,
                        heatValue TEXT NOT NULL,
                        rank TEXT NOT NULL,
                        rankPoints TEXT NOT NULL,
                        lastLogoutTs INTEGER NOT NULL,
                        lastUpdateTs INTEGER NOT NULL,
                        accessToken TEXT NOT NULL,
                        payToken TEXT NOT NULL,
                        expired INTEGER NOT NULL,
                        expireTs INTEGER NOT NULL,
                        healthGameExt TEXT NOT NULL,
                        pf TEXT NOT NULL,
                        pfKey TEXT NOT NULL,
                        uid TEXT NOT NULL,
                        channel TEXT NOT NULL,
                        channelId INTEGER NOT NULL,
                        token TEXT NOT NULL,
                        gender INTEGER NOT NULL,
                        birthdate TEXT NOT NULL,
                        pictureUrl TEXT NOT NULL,
                        userName TEXT NOT NULL,
                        isSelected INTEGER NOT NULL,
                        PRIMARY KEY(openid)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO accounts_new (
                        openid,
                        roleName,
                        roleId,
                        level,
                        isOnline,
                        isBan,
                        isFace,
                        aceMark,
                        heatValue,
                        rank,
                        rankPoints,
                        lastLogoutTs,
                        lastUpdateTs,
                        accessToken,
                        payToken,
                        expired,
                        expireTs,
                        healthGameExt,
                        pf,
                        pfKey,
                        uid,
                        channel,
                        channelId,
                        token,
                        gender,
                        birthdate,
                        pictureUrl,
                        userName,
                        isSelected
                    )
                    SELECT
                        openid,
                        roleName,
                        roleId,
                        level,
                        isOnline,
                        isBan,
                        isFace,
                        aceMark,
                        heatValue,
                        rank,
                        rankPoints,
                        0,
                        lastUpdateTs,
                        accessToken,
                        payToken,
                        expired,
                        expireTs,
                        healthGameExt,
                        pf,
                        pfKey,
                        uid,
                        channel,
                        channelId,
                        token,
                        gender,
                        birthdate,
                        pictureUrl,
                        userName,
                        isSelected
                    FROM accounts
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE accounts")
                db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")
            }
        }
    }
}
