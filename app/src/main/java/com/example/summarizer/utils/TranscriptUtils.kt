package com.example.summarizer.utils

import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import com.example.summarizer.model.common.Paragraph
import com.example.summarizer.model.common.Segment

/**
 * 문단을 나타내는 데이터 클래스
 * @param startTime 시작 시각 (초 단위)
 * @param endTime 종료 시각
 * @param text 문단 내용
 */

/**
 * 기본 문단 분리 함수
 * - 한 문장의 길이나 문장 부호(., ?, !) 기준으로 끊음
 */
fun groupSegmentsByMeaning(
    segments: List<Segment>,
    maxLength: Int = 200
): List<Paragraph> {
    val paragraphs = mutableListOf<Paragraph>()
    if (segments.isEmpty()) return paragraphs

    val builder = StringBuilder()
    var paragraphStartTime = segments[0].start

    for (segment in segments) {
        if (builder.isEmpty()) {
            paragraphStartTime = segment.start
        }

        builder.append(segment.text.trim()).append(" ")

        val textSoFar = builder.toString()
        val lastChar = segment.text.trim().lastOrNull()
        val atSentenceEnd = lastChar == '.' || lastChar == '?' || lastChar == '!'

        // 문장이 끝났거나 일정 길이를 초과하면 끊기
        if (atSentenceEnd || textSoFar.length >= maxLength) {
            paragraphs += Paragraph(
                text = builder.toString().trim(),
                startTime = paragraphStartTime,
                endTime = segment.end
            )
            builder.clear()
        }
    }

    // 마지막 남은 문장 추가
    if (builder.isNotEmpty()) {
        paragraphs += Paragraph(
            text = builder.toString().trim(),
            startTime = paragraphStartTime,
            endTime = segments.last().end
        )
    }

    return paragraphs
}

/**
 * 개선된 문단 분리 로직
 * - 최소/최대 글자 수 기준 + 문장 끝(.) 확인
 */
fun groupByMeaningImproved(
    segments: List<Segment>,
    minChars: Int = 500,
    maxChars: Int = 1000
): List<Paragraph> {
    val paragraphs = mutableListOf<Paragraph>()
    if (segments.isEmpty()) return paragraphs

    val builder = StringBuilder()
    var currentStart = segments.first().start

    // 현재까지 누적된 문장을 문단으로 저장하는 함수
    fun flush(endTime: Float) {
        if (builder.isNotEmpty()) {
            paragraphs += Paragraph(
                text = builder.toString().trim(),
                startTime = currentStart,
                endTime = endTime
            )
            builder.clear()
        }
    }

    for (seg in segments) {
        if (builder.isEmpty()) {
            currentStart = seg.start
        }

        builder.append(seg.text.trim()).append(" ")

        val textSoFar = builder.toString()
        val lastChar = seg.text.trim().lastOrNull()
        val atSentenceEnd = lastChar == '.' || lastChar == '?' || lastChar == '!'

        val longEnough = textSoFar.length >= minChars
        val tooLong = textSoFar.length >= maxChars

        if ((atSentenceEnd && longEnough) || tooLong) {
            flush(seg.end)
        }
    }

    // 마지막 누적 데이터 처리
    flush(segments.last().end)

    return paragraphs
}

/**
 * 문단 리스트를 "[시:분]\n텍스트" 형태로 구성된 순수 텍스트로 변환
 */
fun buildTranscriptTextFromParagraphs(paragraphs: List<Paragraph>): String {
    val builder = StringBuilder()
    for (p in paragraphs) {
        val m = (p.startTime / 60).toInt()
        val s = (p.startTime % 60).toInt()
        val timestamp = String.format("%02d:%02d", m, s)

        builder.append(timestamp)
            .append("\n")
            .append(p.text)
            .append("\n\n")
    }
    return builder.toString().trim()
}

/**
 * 문단 리스트를 SpannableString으로 변환하여
 * - 타임스탬프를 굵게/검정색으로 표시
 * - 타임스탬프 클릭 시 특정 동작(onClick) 실행
 */
fun formatParagraphsStyled(
    paragraphs: List<Paragraph>,
    onClick: (Float) -> Unit
): SpannableString {
    val ssb = SpannableStringBuilder()
    for (p in paragraphs) {
        val m = (p.startTime / 60).toInt()
        val s = (p.startTime % 60).toInt()
        val ts = String.format("[%02d:%02d]", m, s)

        val start = ssb.length
        ssb.append(ts).append("\n")
        val end = ssb.length

        // 타임스탬프 강조
        ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(ForegroundColorSpan(android.graphics.Color.BLACK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // 클릭 시 오디오 이동
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick(p.startTime)
            }
        }
        ssb.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        ssb.append(p.text.trim()).append("\n\n")
    }
    return SpannableString(ssb)
}
