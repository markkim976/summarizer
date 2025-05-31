package com.example.summarizer.model.quiz

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.example.summarizer.view.quiz.MCQQuestion
import com.example.summarizer.view.quiz.QuizQuestion
import com.example.summarizer.BuildConfig


object ChatGPTQuizGenerator {
    private const val API_URL = "https://api.openai.com/v1/chat/completions"
    val API_KEY = BuildConfig.OPENAI_API_KEY

    fun generateQuizFromSummary(
        summary: String,
        callback: (List<QuizQuestion>) -> Unit,
        onError: (String) -> Unit
    ) {
        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        val prompt = """
        아래 강의 요약을 기반으로 정확히 5개의 OX 퀴즈를 만들어줘. 
        각 문제는 반드시 **진술문 형식의 사실 확인 문장**이어야 하며, 
        객관식처럼 "O, X 중 고르시오" 식의 선택 유도 문장은 절대 쓰면 안 돼.
        
        형식은 반드시 아래와 같아야 해:
        
        문제: (사실을 진술하는 한 문장)
        정답: O 또는 X
        해설: (정답에 대한 해설을 한두 문장으로 설명)
        
        예시:
        문제: 인공지능은 데이터를 기반으로 학습한다.
        정답: O
        해설: 인공지능은 데이터를 통해 패턴을 학습하기 때문에 정답은 O이다.
        
        강의 요약:
        $summary
        """.trimIndent()
        Log.d("Summary", "요약 길이: ${summary.length}")


        val jsonBody = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "너는 한국어로 퀴즈를 만드는 AI야."))
                put(JSONObject().put("role", "user").put("content", prompt))
            })
            put("max_tokens", 1000)
            put("temperature", 0.7)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post {
                    onError("네트워크 오류: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        onError("API 오류: ${response.code}")
                    }
                    return
                }

                try {
                    val content = JSONObject(responseText)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val questions = parseQuizFromGPT(content)
                    Handler(Looper.getMainLooper()).post {
                        callback(questions)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        onError("퀴즈 파싱 오류: ${e.message}")
                    }
                }
            }
        })
    }

    fun generateMCQ(
        summary: String,
        callback: (List<MCQQuestion>) -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = """
        아래 강의 요약을 바탕으로 객관식 퀴즈 5개를 만들어줘. 각 퀴즈는 반드시 아래 형식을 따라야 해:
        
        문제: (질문 내용)
        보기: A. 선택지1 B. 선택지2 C. 선택지3 D. 선택지4
        정답: (정답 하나, A/B/C/D 중 하나만)
        해설: (정답에 대한 이유를 한두 문장으로 설명)
        
        예시:
        문제: 인공지능의 학습 방식으로 올바른 것은?
        보기: A. 무작위 B. 강화학습 C. 추측 D. 마법
        정답: B
        해설: 인공지능은 보상을 통해 학습하는 강화학습을 사용한다.
        
        강의 요약:
        $summary
        """.trimIndent()
        Log.d("MCQQuiz", "📤 Prompt:\n$prompt")



        val client = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()

        val requestBody = JSONObject().apply {
            put("model", "gpt-3.5-turbo")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", "너는 한국어로 퀴즈를 만드는 AI야."))
                put(JSONObject().put("role", "user").put("content", prompt))
            })
            put("max_tokens", 1000)
            put("temperature", 0.7)
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MCQQuiz", "❌ 네트워크 오류", e)
                Handler(Looper.getMainLooper()).post {
                    onError("네트워크 오류: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    Handler(Looper.getMainLooper()).post {
                        onError("API 오류: ${response.code}")
                    }
                    return
                }

                try {
                    val content = JSONObject(body ?: "")
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val mcqs = parseMCQFromGPT(content)
                    Handler(Looper.getMainLooper()).post {
                        callback(mcqs)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        onError("퀴즈 파싱 오류: ${e.message}")
                    }
                }
            }
        })
    }

    private fun parseMCQFromGPT(content: String): List<MCQQuestion> {
        val questions = mutableListOf<MCQQuestion>()
        Log.d("GPT_Parsing", "총 추출된 퀴즈 개수: ${questions.size}")
        val blocks = content.trim().split(Regex("""(?=문제\s*\d*:|\b문제\s*:)"""))
            .map { it.trim() }
            .filter { it.contains("보기:") && it.contains("정답:") }

        Log.d("MCQParse", "📊 파싱 대상 블록 수: ${blocks.size}")
        Log.d("MCQParse", "📄 원본 응답:\n$content")

        for (block in blocks) {
            try {
                val questionLine = Regex("""문제\s*\d*:\s*(.+?)\s*보기:""").find(block)?.groupValues?.get(1)?.trim()
                val optionsLine = Regex("""보기:\s*(.+?)\s*정답:""", RegexOption.DOT_MATCHES_ALL).find(block)?.groupValues?.get(1)?.trim()
                val answerLine = Regex("""정답:\s*([A-D])""").find(block)?.groupValues?.get(1)?.trim()
                val explanationLine = Regex("""해설:\s*(.+)""").find(block)?.groupValues?.get(1)?.trim()

                Log.d("MCQParse", "🧩 블록:\n$block")
                Log.d("MCQParse", "❓ 문제: $questionLine")
                Log.d("MCQParse", "🔢 보기: $optionsLine")
                Log.d("MCQParse", "✅ 정답: $answerLine")
                Log.d("MCQParse", "📘 해설: $explanationLine")

                if (questionLine == null || optionsLine == null || answerLine == null || explanationLine == null) continue

                val optionList = Regex("""[A-D]\.\s*([^A-D]+?)(?=\s+[A-D]\.|$)""")
                    .findAll(optionsLine)
                    .map { it.groupValues[1].trim() }
                    .toList()

                val correctIndex = when (answerLine) {
                    "A" -> 0; "B" -> 1; "C" -> 2; "D" -> 3; else -> -1
                }

                if (optionList.size == 4 && correctIndex in 0..3) {
                    questions.add(MCQQuestion(questionLine, optionList, correctIndex, explanationLine))
                } else {
                    Log.d("MCQParse", "⚠️ 보기 개수 부족 or 정답 인덱스 오류")
                }
            } catch (e: Exception) {
                Log.e("MCQParse", "❌ 파싱 실패: ${e.message}")
            }
        }

        Log.d("MCQParse", "✅ 최종 문제 수: ${questions.size}")
        return questions
    }



    private fun parseQuizFromGPT(content: String): List<QuizQuestion> {
        Log.d("GPT_Response", "🧠 GPT 응답: $content")
        val result = mutableListOf<QuizQuestion>()

        val regex = Regex(
            """문제\s*\d*:\s*(.+?)\s*정답\s*\d*?:\s*([OX])\s*해설\s*\d*?:\s*(.+?)(?=\n문제\s*\d*:|\Z)""",
            RegexOption.DOT_MATCHES_ALL
        )

        regex.findAll(content).forEach { match ->
            val question = match.groupValues[1].trim()
            val answer = match.groupValues[2].trim().equals("O", ignoreCase = true)
            val explanation = match.groupValues[3].trim()
            result.add(QuizQuestion(question, answer, explanation))
            Log.d("GPT_Parsing", "문제: $question / 정답: ${match.groupValues[2]} / 해설: $explanation")
        }

        return result
    }

}
