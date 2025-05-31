package com.example.summarizer.model.summary

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.summarizer.BuildConfig

/**
 * GPT API를 통해 요약 내용을 기반으로 간결한 제목(15자 이내)을 생성하는 객체
 */
object ChatGPTTitleGenerator {
    private const val API_URL = "https://api.openai.com/v1/chat/completions"
    val API_KEY = BuildConfig.OPENAI_API_KEY

    /**
     * 요약 내용을 기반으로 제목 생성 요청을 보내고, 콜백을 통해 결과를 반환함
     *
     * @param summaryText 요약된 본문 텍스트
     * @param callback 제목 결과 콜백 (성공 시 제목 문자열 전달)
     */
    fun generateTitle(summaryText: String, callback: (String) -> Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        // 시스템 프롬프트: 제목 생성 규칙 지정 (15자 이내, 핵심 주제 반영)
        val systemPrompt = "다음 요약 내용을 한 줄 제목으로 간결하게 만들어줘. 핵심 주제를 잘 드러내되 15자 이내로."
        val userPrompt = "요약 내용:\n$summaryText"

        val jsonBody = JSONObject().apply {
            put("model", "gpt-4")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
            put("max_tokens", 50)
            put("temperature", 0.7)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        // 비동기 네트워크 요청 수행
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatGPTTitleGenerator", "❌ 네트워크 오류: ${e.message}")
                callback("제목 생성 실패")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("ChatGPTTitleGenerator", "📥 제목 응답: $responseBody")

                if (!response.isSuccessful) {
                    Log.e("ChatGPTTitleGenerator", "❌ 응답 실패: HTTP ${response.code}")
                    callback("제목 생성 실패")
                    return
                }

                try {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val choices = jsonResponse.optJSONArray("choices")
                    val title = choices?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "제목 없음")
                        ?.trim() ?: "제목 없음"

                    callback(title)
                } catch (e: Exception) {
                    Log.e("ChatGPTTitleGenerator", "❌ JSON 파싱 오류: ${e.message}")
                    callback("제목 생성 실패")
                }
            }
        })
    }
}
