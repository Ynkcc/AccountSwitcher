package com.tencent.tim.data.repository

import android.util.Log
import com.tencent.tim.data.local.AccountEntity
import java.net.URLDecoder

object TencentRoleParser {
    private const val TAG = "TencentRoleParser"

    fun parseRoleInfo(openid: String, htmlResponse: String): AccountEntity? {
        return try {
            val match = Regex("data:'(.*?)'").find(htmlResponse) ?: run {
                Log.e(TAG, "Failed to find 'data' field in HTML response: $htmlResponse")
                return null
            }
            
            val decodedData = URLDecoder.decode(match.groupValues[1], "UTF-8")
            
            val params = mutableMapOf<String, String>()
            decodedData.split("&").forEach { pair ->
                val parts = pair.split("=")
                if (parts.size == 2) params[parts[0]] = parts[1]
            }

            if (params.isEmpty()) {
                Log.e(TAG, "Parsed params map is empty. Decoded data: $decodedData")
                return null
            }

            AccountEntity(
                openid = openid,
                roleName = params["charac_name"] ?: openid,
                roleId = params["charac_no"] ?: "未知",
                level = params["level"] ?: "0",
                isOnline = if (params["is_online"] == "1") "在线" else "不在线",
                isBan = if (params["isbanuser"] == "1") "已封号" else "正常",
                isFace = if (params["schemeindex"] == "1") "是" else "否",
                aceMark = params["historyhighestranktimes"] ?: "0",
                heatValue = params["reli"] ?: "0",
                rank = getRank(params["tppseasonduorating"] ?: "0"),
                rankPoints = params["tppseasonduorating"] ?: "0",
                lastLogout = params["lastlogouttime"] ?: "未知"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception during parsing role info", e)
            null
        }
    }

    private fun getRank(pointsStr: String): String {
        val points = pointsStr.toFloatOrNull() ?: return "未知段位"
        return when {
            points >= 4300 -> "王牌 ${((points - 4300) / 100).toInt() + 1} 星"
            points >= 4200 -> "王牌"
            points >= 3700 -> "皇冠"
            points >= 3200 -> "钻石"
            points >= 2700 -> "铂金"
            points >= 2200 -> "黄金"
            points >= 1700 -> "白银"
            points >= 1200 -> "青铜"
            else -> "未知段位"
        }
    }
}
