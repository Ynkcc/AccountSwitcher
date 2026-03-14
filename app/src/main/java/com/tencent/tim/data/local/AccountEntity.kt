package com.tencent.tim.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "accounts")
@Serializable
data class AccountEntity(
    @PrimaryKey val openid: String,
    
    // UI Display Fields
    var roleName: String = "未知",
    var roleId: String = "未知",
    var level: String = "0",
    var isOnline: String = "不在线",
    var isBan: String = "正常",
    var isFace: String = "否",
    var aceMark: String = "0",
    var heatValue: String = "0",
    var rank: String = "未知",
    var rankPoints: String = "0",
    var lastLogout: String = "未知",
    var lastUpdateTs: Long = System.currentTimeMillis(),

    // itop_login.txt Reconstruction Fields
    var accessToken: String = "",
    var payToken: String = "",
    var expired: Int = 0,
    var expireTs: Long = 0,
    var healthGameExt: String = "",
    var pf: String = "",
    var pfKey: String = "",
    var uid: String = "",
    var channel: String = "QQ",
    var channelId: Int = 2,
    var token: String = "",
    var gender: Int = 0,
    var birthdate: String = "",
    var pictureUrl: String = "",
    var userName: String = "",
    var isSelected: Boolean = false
)
