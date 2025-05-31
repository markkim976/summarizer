package com.example.summarizer.view.calendar

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.prolificinteractive.materialcalendarview.*
import com.prolificinteractive.materialcalendarview.spans.DotSpan
import java.util.Calendar

import com.example.summarizer.R


class CalendarFragment : Fragment() {

    private lateinit var calendarView: MaterialCalendarView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var selectedDate: CalendarDay? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)
        calendarView = view.findViewById(R.id.calendarView)

        // Firebase 초기화
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance(
            "https://lecturesummarizer-ae95d-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).reference

        // 로그인 유저 확인 후 요약 데코레이터 적용
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d("CalendarFragment", "🟢 유저 로그인됨: UID=${currentUser.uid}, Email=${currentUser.email}")
            decorateSummaryDates(currentUser.uid)
        } else {
            Log.w("CalendarFragment", "🔴 유저 정보 없음. 로그인 필요!")
        }

        // 요일 한글 포맷, 캘린더 모드 설정
        setKoreanFormat()
        initCalendarClick()
        decorateToday()
        decorateSundays()

        return view
    }

    /**
     * 오늘 날짜를 배경 Drawable로 강조 표시
     */
    private fun decorateToday() {
        val today = CalendarDay.today()
        val drawable: Drawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_calendar_today)!!

        calendarView.addDecorator(object : DayViewDecorator {
            override fun shouldDecorate(day: CalendarDay): Boolean = day == today
            override fun decorate(view: DayViewFacade) {
                view.setBackgroundDrawable(drawable)
            }
        })
    }

    /**
     * 일요일 날짜를 빨간색으로 표시
     */
    private fun decorateSundays() {
        calendarView.addDecorator(object : DayViewDecorator {
            override fun shouldDecorate(day: CalendarDay): Boolean {
                val calendar = day.calendar
                return calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            }

            override fun decorate(view: DayViewFacade) {
                view.addSpan(ForegroundColorSpan(Color.RED))
            }
        })
    }

    /**
     * 요일 및 타이틀을 한국어로 포맷 설정
     */
    private fun setKoreanFormat() {
        calendarView.setWeekDayFormatter { dayOfWeek ->
            when (dayOfWeek) {
                Calendar.SUNDAY -> "일"
                Calendar.MONDAY -> "월"
                Calendar.TUESDAY -> "화"
                Calendar.WEDNESDAY -> "수"
                Calendar.THURSDAY -> "목"
                Calendar.FRIDAY -> "금"
                Calendar.SATURDAY -> "토"
                else -> ""
            }
        }

        calendarView.setTitleFormatter { day -> "${day.year}년 ${day.month + 1}월" }

        calendarView.state().edit()
            .setFirstDayOfWeek(Calendar.SUNDAY)
            .setCalendarDisplayMode(CalendarMode.MONTHS)
            .commit()
    }

    /**
     * 날짜 클릭 시 해당 날짜의 메모 목록을 불러오고 다이얼로그로 표시
     */
    private fun initCalendarClick() {
        calendarView.setOnDateChangedListener { _, date, _ ->
            selectedDate = date
            val userId = auth.currentUser?.uid ?: run {
                Log.w("CalendarFragment", "❌ 유저 인증 정보 없음!")
                return@setOnDateChangedListener
            }

            val dateKey = "${date.year}-${String.format("%02d", date.month + 1)}-${String.format("%02d", date.day)}"
            Log.d("CalendarFragment", "📅 날짜 클릭됨: $dateKey, 유저 ID: $userId")

            val memoRef = database.child("memos").child(userId).child(dateKey)

            memoRef.get().addOnSuccessListener { snapshot ->
                val memoList = mutableListOf<String>()
                for (child in snapshot.children) {
                    val rawText = child.getValue(String::class.java)
                    rawText?.let {
                        val clean = it.trim().replace("\"", "")
                        memoList.add(clean)
                    }
                }

                Log.d("CalendarFragment", "📌 메모 개수: ${memoList.size}")

                // 메모 다이얼로그 띄우기
                MemoDialogFragment.newInstance(dateKey, memoList)
                    .show(childFragmentManager, "memo")
            }.addOnFailureListener {
                Log.e("CalendarFragment", "❌ 메모 불러오기 실패: ${it.message}")
            }
        }
    }

    /**
     * Firebase Realtime DB에서 [요약제목]이 있는 날짜만 추출해 점으로 표시
     */
    private fun decorateSummaryDates(userId: String) {
        val memoRef = database.child("memos").child(userId)

        memoRef.get().addOnSuccessListener { snapshot ->
            val datesWithSummary = mutableSetOf<CalendarDay>()

            for (dateSnapshot in snapshot.children) {
                for (memoSnapshot in dateSnapshot.children) {
                    val memoText = memoSnapshot.getValue(String::class.java)?.trim()
                    if (memoText != null && memoText.startsWith("[요약제목]")) {
                        val parts = dateSnapshot.key?.split("-")
                        if (parts != null && parts.size == 3) {
                            val year = parts[0].toInt()
                            val month = parts[1].toInt() - 1
                            val day = parts[2].toInt()
                            datesWithSummary.add(CalendarDay.from(year, month, day))
                        }
                    }
                }
            }

            calendarView.addDecorator(object : DayViewDecorator {
                override fun shouldDecorate(day: CalendarDay): Boolean {
                    return datesWithSummary.contains(day)
                }

                override fun decorate(view: DayViewFacade) {
                    view.addSpan(DotSpan(8f, Color.BLUE)) // 파란 점 표시
                }
            })
        }.addOnFailureListener {
            Log.e("CalendarFragment", "❌ 요약 날짜 데코레이터 실패: ${it.message}")
        }
    }
}
