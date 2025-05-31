package com.example.summarizer.model.quiz

/**
 * 퀴즈 오답 노트 항목을 나타내는 데이터 클래스
 * - OX 또는 객관식 퀴즈의 오답 정보를 저장
 *
 * @property question 사용자가 틀린 문제의 질문
 * @property correctAnswer 정답 (Boolean: OX, Int 또는 String: 객관식)
 * @property explanation 정답에 대한 해설
 * @property options 객관식 보기 (OX일 경우 null 가능)
 * @property type 퀴즈 유형 ("OX" 또는 "MCQ")
 */
data class WrongNoteItem(
    val question: String = "",
    val correctAnswer: Any? = null,
    val explanation: String = "",
    val options: List<String>? = null,
    val type: String = "OX"
)
