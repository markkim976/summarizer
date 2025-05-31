package com.example.summarizer.view.script

import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.summarizer.R
import com.example.summarizer.utils.formatParagraphsStyled
import com.example.summarizer.utils.groupByMeaningImproved
import com.example.summarizer.viewmodel.HomeViewModel
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ScriptFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var tvTranscription: TextView
    private lateinit var scrollTranscription: ScrollView
    private lateinit var btnStop: FloatingActionButton
    private val handler = Handler(Looper.getMainLooper())
    private var isActive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_script, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        tvTranscription = view.findViewById(R.id.tv_result)
        scrollTranscription = view.findViewById(R.id.scroll_transcription)
        btnStop = view.findViewById(R.id.btn_stop)

        viewModel.currentItem.observe(viewLifecycleOwner) { item ->

            if (item == null) {
                tvTranscription.text = "전사 데이터가 없습니다."
                return@observe
            }

            val segments = item?.segments ?: return@observe
            if (segments.isEmpty()) return@observe

            val paragraphs = groupByMeaningImproved(segments)
            val paragraphTimings = paragraphs.map { it.startTime }

            val styled = formatParagraphsStyled(paragraphs) { startSec ->
                val idx = paragraphTimings.indexOf(startSec)
                val endSec = if (idx + 1 < paragraphTimings.size)
                    paragraphTimings[idx + 1]
                else
                    viewModel.mediaPlayer?.duration?.div(1000f) ?: startSec + 10f
                playAndHighlight(startSec, endSec)
            }

            tvTranscription.text = null
            tvTranscription.text = styled
            tvTranscription.movementMethod = LinkMovementMethod.getInstance()
            scrollTranscription.post { scrollTranscription.fullScroll(View.FOCUS_UP) }
        }

        viewModel.playSegment.observe(viewLifecycleOwner) { pair ->
            Log.d("ScriptFragment", "🟡 observe playSegment: $pair")
            if (!isActive) {
                Log.d("ScriptFragment", "🚫 Fragment 비활성 상태 — 무시")
                return@observe
            }
            pair?.let { (start, end) ->
                Log.d("ScriptFragment", "🔥 playSegment 수신: $start ~ $end")
                playAndHighlight(start, end)
                scrollToParagraph(start)
            }
        }


        viewModel.scrollToTime.observe(viewLifecycleOwner) { time ->
            time?.let { scrollToParagraphAt(it) }
        }

        viewModel.audioUri.observe(viewLifecycleOwner) { uri ->
            uri?.let { setupMediaPlayer(it) }
        }

        btnStop.setOnClickListener {
            viewModel.mediaPlayer?.takeIf { it.isPlaying }?.apply {
                pause()
                btnStop.visibility = View.GONE
                clearHighlight()
            }
        }
    }

    private fun setupMediaPlayer(uri: Uri) {
        try {
            viewModel.mediaPlayer?.release()
            viewModel.mediaPlayer = MediaPlayer().apply {
                setDataSource(requireContext(), uri)
                setOnPreparedListener {
                    viewModel.onMediaPlayerPrepared() // ✅ 준비 완료 알림
                    Log.d("ScriptFragment", "MediaPlayer prepared: $uri")
                }
                prepareAsync()
                viewModel.isPlayerPrepared = false
            }
        } catch (e: Exception) {
            Log.e("ScriptFragment", "MediaPlayer error: ${e.message}")
        }
    }

    private fun scrollToParagraphAt(timeSec: Float) {
        val fullText = tvTranscription.text.toString()
        val timestamp = String.format("[%02d:%02d]", (timeSec / 60).toInt(), (timeSec % 60).toInt()) // 🔥 반드시 대괄호 포함
        val index = fullText.indexOf(timestamp)
        Log.d("ScriptFragment", "스크롤 시도: $timestamp -> 위치 $index")
        if (index >= 0) {
            tvTranscription.post {
                tvTranscription.layout?.let { layout ->
                    val line = layout.getLineForOffset(index)
                    scrollTranscription.scrollTo(0, layout.getLineTop(line))
                }
            }
        } else {
            Log.w("ScriptFragment", "❌ 스크롤 실패: [$timestamp] 안 찾힘")
        }
    }

    private fun scrollToParagraph(startSec: Float) {
        val text = tvTranscription.text
        if (text !is Spannable) return

        val span = text as Spannable
        val minutes = (startSec / 60).toInt()
        val seconds = (startSec % 60).toInt()
        val timestamp = String.format("%02d:%02d", minutes, seconds)

        val fullText = span.toString()
        val tsStart = fullText.indexOf("[$timestamp]")
        Log.d("ScriptFragment", "🔍 하이라이트 위치 찾기: [$timestamp] -> $tsStart")
        if (tsStart < 0) return

        tvTranscription.postDelayed({
            tvTranscription.layout?.let { layout ->
                val line = layout.getLineForOffset(tsStart)
                scrollTranscription.scrollTo(0, layout.getLineTop(line))
            }
        }, 100)
    }

    private fun playAndHighlight(startSec: Float, endSec: Float) {
        viewModel.mediaPlayer?.let { player ->
            if (viewModel.isPlayerPrepared) {
                val startMs = (startSec * 1000).toInt()
                val durationSec = (endSec - startSec).takeIf { it > 0.5f } ?: 3f
                val durationMs = (durationSec * 1000).toLong()

                Log.d("ScriptFragment", "▶️ 재생 시작: $startSec ~ $endSec (duration: $durationSec sec)")

                // 재생 시작 전에 기존 pause 콜백 제거
                handler.removeCallbacksAndMessages(null)

                // MediaPlayer 상태 초기화
                player.seekTo(startMs)
                player.start()
                btnStop.visibility = View.VISIBLE

                // 새 재생 예약
                handler.postDelayed({
                    try {
                        if (player.isPlaying) {  // ⚠️ 여기서 예외 발생할 수 있음
                            player.pause()
                            btnStop.visibility = View.GONE
                        }
                    } catch (e: IllegalStateException) {
                        Log.w("ScriptFragment", "MediaPlayer 상태 오류: ${e.message}")
                    }
                    clearHighlight()
                }, durationMs)

            } else {
                Toast.makeText(requireContext(), "오디오 준비 중입니다.", Toast.LENGTH_SHORT).show()
            }
        }
        highlightParagraph(startSec)
    }

    private fun clearHighlight() {
        val text = tvTranscription.text
        if (text !is Spannable) return

        text.getSpans(0, text.length, BackgroundColorSpan::class.java)
            .forEach { text.removeSpan(it) }
    }

    private fun highlightParagraph(startSec: Float) {
        val text = tvTranscription.text
        if (text !is Spannable) return
        val span = text as Spannable

        span.getSpans(0, span.length, BackgroundColorSpan::class.java)
            .forEach { span.removeSpan(it) }

        val minutes = (startSec / 60).toInt()
        val seconds = (startSec % 60).toInt()
        val timestamp = String.format("%02d:%02d", minutes, seconds)

        val fullText = span.toString()
        val tsStart = fullText.indexOf("[$timestamp]").takeIf { it >= 0 } ?: return
        val tsEnd = tsStart + 8 // "[00:00]" 형태이므로 항상 8자

        val highlightStart = if (fullText.getOrNull(tsEnd) == '\n') tsEnd + 1 else tsEnd
        val nextBlank = fullText.indexOf("\n\n", highlightStart)
        val highlightEnd = if (nextBlank >= 0) nextBlank else fullText.length


        span.setSpan(
            BackgroundColorSpan(Color.LTGRAY),
            highlightStart,
            highlightEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvTranscription.post {
            tvTranscription.layout?.let { layout ->
                val line = layout.getLineForOffset(highlightStart)
                scrollTranscription.scrollTo(0, layout.getLineTop(line))
            }
        }
    }
    override fun onResume() {
        super.onResume()
        isActive = true
        val uri = viewModel.audioUri.value
        if (uri != null && (viewModel.mediaPlayer == null || !viewModel.isPlayerPrepared)) {
            viewModel.reinitializeMediaPlayer(requireContext(), uri)
        }
    }

    override fun onStop() {
        super.onStop()
        isActive = false
        clearHighlight()
        viewModel.mediaPlayer?.pause()
        btnStop.visibility = View.GONE
        viewModel.isPlayerPrepared = false
        viewModel.clearPlayRequest()
        handler.removeCallbacksAndMessages(null)
    }




    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        viewModel.mediaPlayer?.release()
//        viewModel.mediaPlayer = null
//        viewModel.isPlayerPrepared = false
//    }
}
