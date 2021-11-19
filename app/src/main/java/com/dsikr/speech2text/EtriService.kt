package com.dsikr.speech2text

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * ETRI 인공지능 오픈 API 서비스 호출을 위한 인터페이스
 */
interface EtriService {
    @POST("WiseASR/Recognition")
    fun voiceRecognition(@Body body: VoiceRecognitionRequest): Call<VoiceRecognitionResponse>
}

data class VoiceRecognitionRequest(
    val request_id: String,
    val access_key: String,
    val argument: VoiceRecognitionRequestArgument
)

data class VoiceRecognitionRequestArgument(
    val language_code: String,
    val audio: String
)

data class VoiceRecognitionResponse(
    val request_id: String,
    val result: Int,
    val return_object: VoiceRecognitionResponseResult?,
    val reason: String?
)

data class VoiceRecognitionResponseResult(
    val recognized: String
)
