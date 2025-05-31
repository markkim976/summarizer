package com.example.summarizer.model.common

// Firestore에 저장되는 전사 + 요약 데이터를 표현하는 데이터 클래스
data class TranscriptionItem(
    val docId: String = "",             // Firestore 문서 ID (기본값: 빈 문자열)
    val audioFileName: String = "",     // 사용자가 업로드한 오디오 파일 이름
    val audioUrl: String = "",          // 오디오 파일의 Firebase Storage URL
    val transcribedText: String = "",   // Whisper로부터 받아온 전체 전사 텍스트
    val aiSummary: String = "",         // ChatGPT로 생성한 요약문
    val title: String = "",             // 사용자가 지정한 요약 제목
    val uploadDate: String = "",        // 업로드된 날짜 (예: 2025-05-29)
    val timestamp: Long = 0L,           // 업로드 시각 (밀리초)
    val segments: List<Segment> = emptyList(),     // 전사된 세그먼트 목록 (자막 구간)
    val paragraphs: List<Paragraph> = emptyList()  // 문단 단위로 나눈 내용 (요약/재생용)
)

// Whisper 전사 결과에서의 한 구간(세그먼트)을 나타냄
data class Segment(
    val start: Float = 0f,  // 시작 시간 (초)
    val end: Float = 0f,    // 종료 시간 (초)
    val text: String = ""   // 해당 시간 구간의 전사 텍스트
)

// AI 요약 결과에서 문단 단위 텍스트를 나타냄 (선택적으로 사용할 수 있음)
data class Paragraph(
    val startTime: Float = 0f,  // 문단 시작 시간 (초)
    val endTime: Float = 0f,    // 문단 끝 시간 (초)
    val text: String = ""   // 문단 내용
)
