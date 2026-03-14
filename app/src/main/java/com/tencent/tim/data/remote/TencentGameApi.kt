package com.tencent.tim.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TencentGameApi {
    @GET("main")
    suspend fun getRoleInfo(
        @Query("game") game: String = "cjm",
        @Query("area") area: Int = 2,
        @Query("platid") platid: Int = 1,
        @Query("sCloudApiName") apiName: String = "ams.gameattr.role",
        @Header("Cookie") cookie: String,
        @Header("Referer") referer: String = "https://gp.qq.com/",
        @Header("User-Agent") ua: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    ): Response<String>
}
