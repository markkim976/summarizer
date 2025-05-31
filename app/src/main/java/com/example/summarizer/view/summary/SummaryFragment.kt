package com.example.summarizer.view.summary

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.summarizer.R
import com.example.summarizer.model.common.Paragraph
import com.example.summarizer.model.common.RegressionModel
import com.example.summarizer.model.common.Segment
import com.example.summarizer.model.common.TranscriptionItem
import com.example.summarizer.model.summary.ChatGPTSummarizer
import com.example.summarizer.model.summary.ChatGPTTitleGenerator
import com.example.summarizer.model.summary.FirebaseUploader
import com.example.summarizer.model.summary.WhisperTranscriber
import com.example.summarizer.utils.FileUtils
import com.example.summarizer.utils.buildTranscriptTextFromParagraphs
import com.example.summarizer.utils.groupByMeaningImproved
import com.example.summarizer.view.main.HomeFragment
import com.example.summarizer.viewmodel.HomeViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SummaryFragment : Fragment() {
    private lateinit var viewModel: HomeViewModel
    private lateinit var tvSummary: TextView
    private lateinit var tvKeywords: TextView
    private lateinit var scrollSummary: ScrollView
    private lateinit var btnSummarize: FloatingActionButton
    private lateinit var tvTimer: TextView
    private lateinit var pbTimer: ProgressBar


    private val PICK_AUDIO_REQUEST = 1
    private val timerHandler = Handler(Looper.getMainLooper())
    private var predictedTotalTimeSec = 0f
    private var timerStartTime = 0L

    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - timerStartTime) / 1000f
            val remaining = (predictedTotalTimeSec - elapsed).coerceAtLeast(0f)
            val m = (remaining / 60).toInt()
            val s = (remaining % 60).toInt()
            tvTimer.text = String.format("예상시간: %02d:%02d", m, s)

            val fraction = if (predictedTotalTimeSec > 0f)
                (elapsed / predictedTotalTimeSec).coerceIn(0f, 1f)
            else 0f
            pbTimer.progress = (fraction * pbTimer.max.toFloat()).toInt()

            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_summary, container, false)

    @SuppressLint("WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        tvSummary = view.findViewById(R.id.tv_summary)
        tvKeywords = view.findViewById(R.id.tv_keywords)
        scrollSummary = view.findViewById(R.id.scroll_summary)
        btnSummarize = view.findViewById(R.id.btn_summarize)
        tvTimer = requireActivity().findViewById(R.id.tv_timer)
        pbTimer = requireActivity().findViewById(R.id.progress_timer)

        RegressionModel.init(requireContext())

        viewModel.currentItem.observe(viewLifecycleOwner) { item ->
            Log.d("SummaryFragment", "✅ item 받은 paragraphs.size=${item?.paragraphs?.size}")
            if (item == null || item.aiSummary.isNullOrBlank()) {
                tvSummary.text = "요약 데이터가 없습니다."
                return@observe
            }
            displaySummary(item?.aiSummary ?: "")
        }

        btnSummarize.setOnClickListener {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            }.also { startActivityForResult(it, PICK_AUDIO_REQUEST) }
        }
    }

    @SuppressLint("WrongConstant")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_AUDIO_REQUEST || resultCode != Activity.RESULT_OK) return

        val uri = data?.data ?: return
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        )

        viewModel.setAudioUri(uri)
        tvTimer.visibility = View.VISIBLE
        pbTimer.visibility = View.VISIBLE

        val audioSec = getAudioDuration(uri)
        val estTrans = RegressionModel.predict(audioSec)
        predictedTotalTimeSec = estTrans + 30f
        timerStartTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)

        viewModel.mediaPlayer?.release()
        viewModel.mediaPlayer = null
        viewModel.isPlayerPrepared = false
        setupMediaPlayer(uri)

        val audioFile = FileUtils.getFileFromUri(requireContext(), uri)
        if (audioFile == null) {
            Toast.makeText(requireContext(), "파일 변환 오류", Toast.LENGTH_SHORT).show()
            return
        }

        WhisperTranscriber.transcribe(audioFile) { segments, error ->
            requireActivity().runOnUiThread {
                if (error != null || segments.isEmpty()) {
                    val msg = error ?: "전사 결과가 없습니다."
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val paragraphs = groupByMeaningImproved(segments)
                val rawText = buildTranscriptTextFromParagraphs(paragraphs)

                val item = TranscriptionItem(
                    docId = "",
                    audioFileName = FileUtils.getFileNameFromUri(requireContext(), uri),
                    audioUrl = uri.toString(),
                    transcribedText = rawText,
                    aiSummary = "",
                    uploadDate = "",
                    timestamp = System.currentTimeMillis(),
                    segments = segments,
                    paragraphs = paragraphs
                )
                viewModel.setCurrentItem(item)

                tvSummary.text = "요약중입니다. 잠시만 기다려주세요..."
                tvKeywords.text = ""
                scrollSummary.post { scrollSummary.fullScroll(View.FOCUS_DOWN) }

                summarizeAfterTranscription(rawText, segments, paragraphs)
            }
        }

    }

    private fun summarizeAfterTranscription(
        rawText: String,
        segments: List<Segment>,
        paragraphs: List<Paragraph>
    ) {
        Log.d("SummaryFragment", "🚀 summarizeAfterTranscription 시작됨")
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val mode = prefs.getString("summary_mode", "simple") ?: "simple"
        val includeKeywords = prefs.getBoolean("include_keywords", true)

        val chunkSizeSeconds = 13 * 60  // 13분 단위
        val chunks = mutableListOf<List<Paragraph>>()
        var currentChunk = mutableListOf<Paragraph>()
        var chunkStart = 0f

        for (p in paragraphs) {
            if (p.startTime - chunkStart >= chunkSizeSeconds && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                chunkStart = p.startTime
            }
            currentChunk.add(p)
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk)
        }

        var completed = 0


        fun mergeSummariesWithKeywords(summaries: List<String>): String {
            val body = StringBuilder()
            val keywordMap = mutableMapOf<String, String>()

            for (summary in summaries) {
                val parts = summary.split("주요 키워드:", limit = 2)
                body.append(parts[0].trim()).append("\n\n")

                if (parts.size > 1) {
                    val keywordText = parts[1].trim()

                    // 🔍 핵심 추가: 키워드 파싱 전에 내용이 본문인지 키워드인지 구분
                    val regex = Regex("""(\S+?)\s*:\s*([^:,]+(?:\s*[^:,]+)*)""")
                    val matches = regex.findAll(keywordText)

                    if (matches.none()) {
                        // ⚠️ 키워드 형식이 아니면 그냥 본문으로 간주해서 append
                        body.append(keywordText).append("\n\n")
                        continue
                    }

                    for (match in matches) {
                        val keyword = match.groupValues[1].trim()
                        val definition = match.groupValues[2].trim()
                        keywordMap[keyword] = definition
                    }
                }
            }

            val keywordString = keywordMap.entries.joinToString(", ") { "${it.key}:${it.value}" }
            return body.toString().trim() +
                    (if (keywordMap.isNotEmpty()) "\n\n주요 키워드: $keywordString" else "")
        }


        val summaries = MutableList<String?>(chunks.size) { null }

        fun onChunkComplete(index: Int, summary: String) {
            summaries[index] = summary
            completed++
            if (completed == chunks.size) {
                val nonNullSummaries = summaries.filterNotNull()
                val mergedSummary = mergeSummariesWithKeywords(nonNullSummaries)

                requireActivity().runOnUiThread {
                    timerHandler.removeCallbacks(timerRunnable)
                    tvTimer.visibility = View.GONE
                    pbTimer.visibility = View.GONE

                    viewModel.updateAiSummary(mergedSummary)

                    ChatGPTTitleGenerator.generateTitle(mergedSummary) { title ->
                        val cleanTitle = title.replace("\"", "").trim()
                        viewModel.currentItem.value?.let { item ->
                            val fullItem = item.copy(aiSummary = mergedSummary, title = cleanTitle)
                            FirebaseUploader.uploadData(fullItem)
                        }
                    }

                    displaySummary(mergedSummary)
                    scrollSummary.post { scrollSummary.fullScroll(View.FOCUS_DOWN) }
                    Toast.makeText(requireContext(), "✅ 요약 완료!", Toast.LENGTH_SHORT).show()
                }
            }
        }






        chunks.forEachIndexed { index, chunk ->
            ChatGPTSummarizer.summarizeText(
                inputText = buildTranscriptTextFromParagraphs(chunk),
                transcriptSegments = emptyList(),
                paragraphs = chunk,
                summaryMode = mode,
                includeKeywords = includeKeywords
            ) { summary, error ->
                if (error .isNotEmpty()) {
                    Log.e("SummaryFragment", "❌ 요약 실패: $error")
                    requireActivity().runOnUiThread {
                        tvSummary.text = "OpenAI 오류: $error\n다시 시도해주세요."
                        timerHandler.removeCallbacks(timerRunnable)
                        tvTimer.visibility = View.GONE
                        pbTimer.visibility = View.GONE
                    }
                } else {
                    onChunkComplete(index, summary ?: "")
                }
            }

        }

    }



    private fun displaySummary(raw: String) {
        val summaryText = raw.ifBlank { "AI 요약 결과가 없습니다." }
        val parts = summaryText.split("주요 키워드:", limit = 2)
        val body = parts[0].trim()
        val keywords = parts.getOrNull(1)?.trim() ?: ""

        val spannable = SpannableStringBuilder(body)
        val regex = "\\[(\\d{2}:\\d{2})]".toRegex()

        regex.findAll(body).forEach { match ->
            val timeText = match.value          // 예: [00:35]
            val timeStr = match.groupValues[1]  // 예: 00:35
            val (min, sec) = timeStr.split(":").map { it.toInt() }
            val startTime = min * 60 + sec

            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val (min, sec) = timeStr.split(":").map { it.toInt() }
                    val startTime = (min * 60 + sec).toFloat()

                    val paragraphs = viewModel.currentItem.value?.paragraphs.orEmpty()

                    paragraphs.forEach {
                        Log.d("SummaryFragment", "🔍 단락: start=${it.startTime}, end=${it.endTime}")
                    }

                    val paragraph = paragraphs.find { kotlin.math.abs(it.startTime - startTime) < 1.0f }
                    val endTime = paragraph?.endTime ?: (startTime + 10f)

                    if (paragraph == null) {
                        Log.w("SummaryFragment", "❗ fallback으로 10초 사용됨. startTime=$startTime")
                    }

                    if (!viewModel.isPlayerPrepared) {
                        Toast.makeText(requireContext(), "오디오 준비 중입니다.", Toast.LENGTH_SHORT).show()
                        viewModel.requestPlayAfterPrepared(startTime, endTime)
                        return
                    }

                    viewModel.setPlaySegment(startTime, endTime)
                    viewModel.requestScrollTo(startTime)
                    (parentFragment as? HomeFragment)?.switchToTab(1)
                }
            }, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)



        }


        tvSummary.text = spannable
        tvSummary.movementMethod = LinkMovementMethod.getInstance()

        val keywordMap = parseKeywordDefinitions(raw)
        val keywordSpannable = SpannableStringBuilder()

        for ((keyword, definition) in keywordMap) {
            val start = keywordSpannable.length
            keywordSpannable.append(keyword)
            val end = keywordSpannable.length

            keywordSpannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    showDefinitionPopup(keyword, definition)
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            keywordSpannable.append(" • ")  // 간격
        }

        tvKeywords.text = keywordSpannable
        tvKeywords.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun parseKeywordDefinitions(summary: String): Map<String, String> {
        val map = mutableMapOf<String, String>()

        val keywordSections = summary.split("주요 키워드:").drop(1)  // 첫 번째는 요약 본문이므로 제외

        keywordSections.forEach { section ->
            val cleaned = section.replace("\n", " ").replace(Regex("\\s+"), " ").trim()

            // 대부분의 키워드:정의 쌍을 정규식으로 잡기
            val regex = Regex("""(\S+?)\s*:\s*(.*?)(?=\s+\S+\s*:)""")
            val matches = regex.findAll(cleaned)

            for (match in matches) {
                val keyword = match.groupValues[1].trim()
                val definition = match.groupValues[2].trim().trimEnd(',')
                if (keyword.isNotEmpty() && definition.isNotEmpty()) {
                    map[keyword] = definition
                }
            }

            // 마지막 키워드:정의 항목 추가 처리
            val lastMatch = matches.lastOrNull()
            val endOfLast = lastMatch?.range?.last?.plus(1) ?: 0
            val remaining = cleaned.substring(endOfLast).trim()
            val pair = remaining.split(":", limit = 2)
            if (pair.size == 2) {
                val keyword = pair[0].trim()
                val definition = pair[1].trim().trimEnd(',')
                if (keyword.isNotEmpty() && definition.isNotEmpty() && keyword !in map) {
                    map[keyword] = definition
                }
            }
        }

        return map
    }





    private fun showDefinitionPopup(term: String, definition: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(term)
            .setMessage(definition)
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun findEndTime(start: Float): Float {
        val paragraphs = viewModel.currentItem.value?.paragraphs.orEmpty()
        if (paragraphs.isEmpty()) return start + 10f

        // 🔍 start 시간과 가장 가까운 문단을 찾음
        val idx = paragraphs.indexOfFirst { start < it.startTime }
        return if (idx > 0) {
            paragraphs[idx].startTime  // 다음 문단 시작 시간
        } else if (idx == 0) {
            paragraphs[0].endTime  // 첫 문단이면 끝으로
        } else {
            paragraphs.last().endTime
        }
    }



    private fun setupMediaPlayer(uri: Uri) {
        try {
            viewModel.mediaPlayer = MediaPlayer().apply {
                setDataSource(requireContext(), uri)
                setOnPreparedListener { viewModel.isPlayerPrepared = true }
                prepareAsync()
                viewModel.isPlayerPrepared = false
            }
        } catch (e: Exception) {
            Log.e("SummaryFragment", "MediaPlayer 초기화 실패: ${e.message}")
        }
    }

    private fun getAudioDuration(uri: Uri): Float = try {
        MediaMetadataRetriever().run {
            setDataSource(requireContext(), uri)
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toFloatOrNull()?.div(1000f) ?: 0f
        }
    } catch (e: Exception) {
        Log.e("SummaryFragment", "Duration 오류: ${e.message}")
        0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.mediaPlayer?.release()
        viewModel.mediaPlayer = null
        viewModel.isPlayerPrepared = false
    }
}
