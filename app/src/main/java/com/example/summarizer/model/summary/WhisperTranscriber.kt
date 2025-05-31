package com.example.summarizer.model.summary

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.summarizer.model.common.Segment // Segment 클래스는 model.common 패키지에 위치
import com.example.summarizer.BuildConfig

/**
 * Whisper API를 이용해 오디오 파일을 텍스트로 전사(transcribe)하는 유틸리티 객체
 */
object WhisperTranscriber {
    private const val API_URL = "https://api.openai.com/v1/audio/transcriptions"
    val API_KEY = BuildConfig.OPENAI_API_KEY

    /**
     * 오디오 파일을 Whisper API로 전송해 텍스트 세그먼트를 반환함
     *
     * @param audioFile 전사 대상의 MP3/WAV 파일
     * @param callback 전사 성공 시 세그먼트 리스트와 오류 메시지(null), 실패 시 빈 리스트와 오류 메시지
     */
    fun transcribe(audioFile: File, callback: (List<Segment>, String?) -> Unit) {
        val client = OkHttpClient.Builder()
            .connectTimeout(1200, TimeUnit.SECONDS)
            .readTimeout(1200, TimeUnit.SECONDS)
            .writeTimeout(1200, TimeUnit.SECONDS)
            .build()

        // 요청 본문 구성 (multipart/form-data)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                RequestBody.create("audio/mpeg".toMediaTypeOrNull(), audioFile)
            )
            .addFormDataPart("model", "whisper-1") // 사용 모델
            .addFormDataPart("response_format", "verbose_json") // segment 정보 포함
            .build()

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $API_KEY")
            .post(requestBody)
            .build()

        // 비동기 요청 수행
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WhisperTranscriber", "❌ 네트워크 오류 발생: ${e.message}", e)
                callback(emptyList(), "❌ 네트워크 오류 발생: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("WhisperTranscriber", "📥 응답 데이터: $responseBody")

                // 실패 응답 처리
                if (!response.isSuccessful) {
                    val errorMsg = "❌ API 요청 실패: HTTP ${response.code}\n${responseBody ?: "오류 내용 없음"}"
                    Log.e("WhisperTranscriber", errorMsg)
                    callback(emptyList(), errorMsg)
                    return
                }

                try {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val segmentsArray = jsonResponse.optJSONArray("segments") ?: run {
                        callback(emptyList(), "❌ 'segments' 필드가 응답에 없습니다.")
                        return
                    }

                    // 응답에서 segments 파싱
                    val segments = mutableListOf<Segment>()
                    for (i in 0 until segmentsArray.length()) {
                        val seg = segmentsArray.getJSONObject(i)
                        segments.add(
                            Segment(
                                start = seg.getDouble("start").toFloat(),
                                end = seg.getDouble("end").toFloat(),
                                text = seg.getString("text")
                            )
                        )
                    }

                    callback(segments, null)
                } catch (e: Exception) {
                    Log.e("WhisperTranscriber", "❌ JSON 파싱 오류: ${e.message}", e)
                    callback(emptyList(), "❌ JSON 파싱 오류: ${e.message}")
                }
            }
        })
    }
}
