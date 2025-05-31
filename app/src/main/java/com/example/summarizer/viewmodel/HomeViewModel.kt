package com.example.summarizer.viewmodel

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.summarizer.model.common.TranscriptionItem

class HomeViewModel : ViewModel() {


    fun clearPlayRequest() {
        _playSegment.value = null
    }

    fun clearCurrentItem() {
        _currentItem.value = null
    }

    fun clearAllData() {
        _currentItem.value = null
        _playSegment.value = null
        _audioUri.value = null
        _transcribedText.value = null
        _predictedTranscriptionTime.value = null
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayerPrepared = false
    }



    //메모용 새로 추가
    private val _documentId = MutableLiveData<String?>()
    val documentId: LiveData<String?> get() = _documentId

    fun setDocumentId(id: String?) {
        _documentId.value = id
    }

    private val _transcribedText = MutableLiveData<CharSequence?>()
    val transcribedText: LiveData<CharSequence?> get() = _transcribedText

    private val _predictedTranscriptionTime = MutableLiveData<Float?>()
    val predictedTranscriptionTime: LiveData<Float?> get() = _predictedTranscriptionTime

    fun setTranscribedText(text: CharSequence) {
        _transcribedText.value = text
    }


    fun setPredictedTranscriptionTime(time: Float) {
        _predictedTranscriptionTime.value = time
    }
    fun setAiSummary(summary: String) {
        _currentItem.value = _currentItem.value?.copy(aiSummary = summary)
    }
    //여기까지


    // ① 오디오 길이 저장용 LiveData
    private val _audioDurationSeconds = MutableLiveData<Int>()
    val audioDurationSeconds: LiveData<Int> get() = _audioDurationSeconds

    fun setAudioDuration(seconds: Int) {
        _audioDurationSeconds.value = seconds
    }




    val keywordDefinitions = MutableLiveData<Map<String, String>>()  // HomeViewModel에 추가

    // ✅ 단일 TranscriptionItem 관리
    private val _currentItem = MutableLiveData<TranscriptionItem?>()
    val currentItem: LiveData<TranscriptionItem?> get() = _currentItem

    fun setCurrentItem(item: TranscriptionItem) {
        _currentItem.value = item.copy(segments = item.segments.toList())
    }

    fun updateAiSummary(summary: String) {
        _currentItem.value = _currentItem.value?.copy(aiSummary = summary)
    }

    fun loadFromHistory(item: TranscriptionItem) {
        Log.d("HomeViewModel", "📌 setCurrentItem 호출: ${item.audioFileName}, segments=${item.segments.size}")
        setCurrentItem(item)
    }

    fun setPlaySegment(start: Float, end: Float) {
        _playSegment.value = Pair(start, end)
    }

    private val _scrollToTime = MutableLiveData<Float?>()
    val scrollToTime: LiveData<Float?> get() = _scrollToTime

    fun requestScrollTo(time: Float) {
        _scrollToTime.value = time
        _scrollToTime.value = null // 다시 초기화해서 같은 시간도 작동 가능하게
    }



    // ✅ 오디오 재생 관련 정보는 별도로 유지
    private val _audioUri = MutableLiveData<Uri?>()
    val audioUri: LiveData<Uri?> get() = _audioUri

    private val _playSegment = MutableLiveData<Pair<Float, Float>?>()
    val playSegment: LiveData<Pair<Float, Float>?> get() = _playSegment


    private val _lastPlaybackPosition = MutableLiveData<Int>()
    val lastPlaybackPosition: LiveData<Int> get() = _lastPlaybackPosition

    var mediaPlayer: MediaPlayer? = null
    var isPlayerPrepared = false

    fun setAudioUri(uri: Uri?) {
        _audioUri.value = uri
    }

    fun setLastPlaybackPosition(position: Int) {
        _lastPlaybackPosition.value = position
    }

    fun requestPlay(start: Float, end: Float) {
        _playSegment.value = start to end
    }

    private var pendingPlayRequest: Pair<Float, Float>? = null

    fun requestPlayAfterPrepared(start: Float, end: Float) {
        if (isPlayerPrepared) {
            setPlaySegment(start, end)
        } else {
            pendingPlayRequest = Pair(start, end)
        }
    }

    fun onMediaPlayerPrepared() {
        isPlayerPrepared = true
        pendingPlayRequest?.let { (start, end) ->
            setPlaySegment(start, end)
            pendingPlayRequest = null
        }
    }



    fun reinitializeMediaPlayer(context: Context, uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnPreparedListener {
                    isPlayerPrepared = true
                    onMediaPlayerPrepared()

                    val durationSec = duration / 1000  // ms → 초
                    setAudioDuration(durationSec)
                    Log.d("HomeViewModel", "🎵 오디오 길이: ${durationSec}초")
                }
                prepareAsync()
                isPlayerPrepared = false
            }
        } catch (e: Exception) {
            Log.e("HomeViewModel", "❌ MediaPlayer 초기화 실패: ${e.message}")
        }
    }

}
