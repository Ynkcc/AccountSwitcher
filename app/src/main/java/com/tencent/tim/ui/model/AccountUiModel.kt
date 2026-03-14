package com.tencent.tim.ui.model

import com.tencent.tim.data.local.AccountEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AccountUiModel(
    val openid: String,
    val roleName: String,
    val roleId: String,
    val level: String,
    val isOnline: String,
    val isBan: String,
    val isFace: String,
    val aceMark: String,
    val heatValue: String,
    val rank: String,
    val rankPoints: String,
    val lastLogoutText: String,
    val isSelected: Boolean
)

fun AccountEntity.toUiModel(): AccountUiModel {
    return AccountUiModel(
        openid = openid,
        roleName = roleName,
        roleId = roleId,
        level = level,
        isOnline = isOnline,
        isBan = isBan,
        isFace = isFace,
        aceMark = aceMark,
        heatValue = heatValue,
        rank = rank,
        rankPoints = rankPoints,
        lastLogoutText = formatLastLogoutText(lastLogoutTs),
        isSelected = isSelected
    )
}

private fun formatLastLogoutText(lastLogoutTs: Long): String {
    if (lastLogoutTs <= 0L) {
        return "未知"
    }

    val instant = if (lastLogoutTs >= 1_000_000_000_000L) {
        Instant.ofEpochMilli(lastLogoutTs)
    } else {
        Instant.ofEpochSecond(lastLogoutTs)
    }

    return LAST_LOGOUT_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
}

private val LAST_LOGOUT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
