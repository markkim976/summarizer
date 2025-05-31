package com.example.summarizer.model.summary

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.summarizer.model.common.Paragraph
import com.example.summarizer.model.common.Segment
import com.example.summarizer.BuildConfig


/**
 * GPT API를 활용하여 강의 텍스트를 요약하고 키워드를 추출하는 객체
 */
object ChatGPTSummarizer {
    private const val API_URL = "https://api.openai.com/v1/chat/completions"
    val API_KEY = BuildConfig.OPENAI_API_KEY

    /**
     * 전체 강의 내용을 여러 chunk로 나누어 순차적으로 요약 요청을 수행
     */
    fun summarizeInChunks(
        fullText: String,
        fullParagraphs: List<Paragraph>,
        durationSeconds: Int,
        summaryMode: String = "simple",
        includeKeywords: Boolean = true,
        callback: (String) -> Unit
    ) {
        val chunkSizeSec = 780
        val chunks = fullParagraphs.chunkByTime(chunkSizeSec)
        val combinedSummary = StringBuilder()
        val collectedKeywords = mutableMapOf<String, String>()

        // 각 chunk에 대해 순차적으로 GPT 요약 요청
        fun processNext(index: Int) {
            if (index >= chunks.size) {
                if (includeKeywords && collectedKeywords.isNotEmpty()) {
                    combinedSummary.append("주요 키워드:")
                    collectedKeywords.forEach { (k, v) ->
                        combinedSummary.append(" $k:$v,")
                    }
                    combinedSummary.setLength(combinedSummary.length - 1)
                }
                callback(combinedSummary.toString())
                return
            }

            summarizeText(
                inputText = buildTranscriptTextFromParagraphs(chunks[index]),
                transcriptSegments = emptyList(),
                paragraphs = chunks[index],
                summaryMode = summaryMode,
                includeKeywords = includeKeywords
            ) { summary, _ ->
                combinedSummary.append(summary).append("\n\n")
                parseKeywordDefinitions(summary).forEach { (k, v) -> collectedKeywords[k] = v }
                processNext(index + 1)
            }
        }

        processNext(0)
    }

    /**
     * 주어진 시간 기준으로 문단 리스트를 분할 (시간 단위 chunk 나누기)
     */
    private fun List<Paragraph>.chunkByTime(chunkSizeSec: Int): List<List<Paragraph>> {
        val result = mutableListOf<List<Paragraph>>()
        var current = mutableListOf<Paragraph>()
        var currentStart = this.firstOrNull()?.startTime ?: 0f

        for (p in this) {
            if ((p.startTime - currentStart) >= chunkSizeSec && current.isNotEmpty()) {
                result.add(current)
                current = mutableListOf()
                currentStart = p.startTime
            }
            current.add(p)
        }
        if (current.isNotEmpty()) result.add(current)
        return result
    }

    /**
     * 문단 리스트를 전사 텍스트 형식으로 변환 (시간 + 텍스트)
     */
    private fun buildTranscriptTextFromParagraphs(paragraphs: List<Paragraph>): String {
        return paragraphs.joinToString("\n\n") { p ->
            val min = (p.startTime / 60).toInt()
            val sec = (p.startTime % 60).toInt()
            val time = String.format("[%02d:%02d]", min, sec)
            "$time\n${p.text.trim()}"
        }
    }

    /**
     * GPT API를 호출하여 단일 chunk를 요약 (문단별 요약)
     */
    fun summarizeText(
        inputText: String,
        transcriptSegments: List<Segment>,
        paragraphs: List<Paragraph>,
        summaryMode: String = "simple",
        includeKeywords: Boolean = true,
        callback: (String, Map<String, Double>) -> Unit
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val keywordInstruction = if (includeKeywords) {
            """
            마지막에 '주요 키워드:'라는 제목 아래 다음 형식으로 정리해 주세요:
            
            - 형식: 키워드: 정의
            - 예시: 재귀함수: 자기 자신을 호출하는 함수
            
            요구 사항:
            1. 각 키워드는 고유한 개념어여야 하며, 일반 단어(예: 분석, 효과, 방식, 법칙)는 제외하세요.
            2. 정의에는 다른 키워드나 예시, 쉼표(,)를 포함하지 마세요.
            3. 정의는 자기참조 없이 단독으로 의미를 이해할 수 있어야 하며, 15단어 이내의 간결한 문장으로 작성하세요.
            4. 키워드는 줄바꿈 없이 쉼표(,)로 구분해 주세요.
            """.trimIndent()
        } else ""



        // summaryMode 에 따라 system prompt 다르게 설정
        val systemPrompt = when (summaryMode) {
            "simple" -> "다음 강의 내용을 문단별로 간단하면서도 핵심은 포함하여 반드시 한국어로 요약해 주세요. 각 문단 앞에는 시작 시각(예: [00:00])을 제목처럼 따로 표시해 주세요." + keywordInstruction
            "detailed" -> "다음 강의 내용을 문단별로 자세하고 정확하게 반드시 한국어로 요약해 주세요. 각 문단 앞에는 시작 시각(예: [00:00])을 제목처럼 따로 표시해 주세요." + keywordInstruction
            else -> error("정의되지 않은 summaryMode입니다: $summaryMode") // 요약 모드는 Simple과 Detailed
        }

        // 실제 요약할 사용자 입력 prompt 생성
        val userPrompt = buildString {
            append("다음은 강의의 문단별 내용입니다. 한국어(Korean)이 아닌 외국어일 수 있으나, 반드시 한국어로 요약해 주세요 :\n\n")
            paragraphs.forEach { p ->
                val min = (p.startTime / 60).toInt()
                val sec = (p.startTime % 60).toInt()
                append("[%02d:%02d]\n%s\n\n".format(min, sec, p.text.trim()))
            }
        }

        val jsonBody = JSONObject().apply {
            put("model", "gpt-4-turbo")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
            put("max_tokens", 2000)
            put("temperature", 0.5)
        }

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ChatGPTSummarizer", "❌ 네트워크 오류: ${e.message}")
                callback("네트워크 오류: ${e.message}", emptyMap())
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    callback("API 요청 실패: HTTP ${response.code}", emptyMap())
                    return
                }

                try {
                    val content = JSONObject(responseBody ?: "{}")
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    callback(content, emptyMap())
                } catch (e: Exception) {
                    callback("JSON 파싱 오류: ${e.message}", emptyMap())
                }
            }
        })
    }

    /**
     * 요약 결과에서 "주요 키워드:" 이후의 키워드를 추출하여 Map으로 반환
     */
    private fun parseKeywordDefinitions(summary: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keywordSection = summary.split("주요 키워드:").getOrNull(1) ?: return emptyMap()
        val cleaned = keywordSection.replace("\n", " ").replace(Regex("\\s+"), " ").trim()

        val regex = Regex("""([^:\s]+)\s*:\s*([^:,]+(?:\s[^:,]+)*)""")
        val matches = regex.findAll(cleaned)
        for (match in matches) {
            val keyword = match.groupValues[1].trim()
            val definition = match.groupValues[2].trim()
            map[keyword] = definition
        }

        val lastMatch = regex.findAll(cleaned).lastOrNull()
        val endOfLast = lastMatch?.range?.last?.plus(1) ?: 0
        val remaining = cleaned.substring(endOfLast).trim()
        val pair = remaining.split(":", limit = 2)
        if (pair.size == 2) {
            val keyword = pair[0].trim()
            val definition = pair[1].trim()
            if (keyword.isNotEmpty() && definition.isNotEmpty()) {
                map[keyword] = definition
            }
        }

        return map
    }
}
