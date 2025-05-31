package com.example.summarizer.model.calendar

// Memo 클래스는 날짜별 메모 항목 하나를 표현하는 데이터 모델 클래스입니다.
// Firestore 또는 Realtime Database에 저장될 메모 단위를 나타냅니다.

data class Memo(
    val content: String = "",  // 메모의 본문 내용
    val timestamp: Long = System.currentTimeMillis() // 메모가 생성된 시각 (기본값: 현재 시간)
)
