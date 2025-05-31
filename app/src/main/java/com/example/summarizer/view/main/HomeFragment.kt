package com.example.summarizer.view.main

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.summarizer.R
import com.example.summarizer.adapter.HistoryAdapter
import com.example.summarizer.adapter.ViewPagerAdapter
import com.example.summarizer.model.common.Paragraph
import com.example.summarizer.model.common.Segment
import com.example.summarizer.model.common.TranscriptionItem
import com.example.summarizer.view.script.ScriptFragment
import com.example.summarizer.view.summary.SummaryFragment
import com.example.summarizer.viewmodel.HomeViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    private lateinit var recyclerViewHistory: RecyclerView
    private val historyItems = mutableListOf<TranscriptionItem>()
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var viewModel: HomeViewModel

    // 외부에서 호출하여 탭 전환
    fun switchToTab(index: Int) {
        view?.findViewById<ViewPager2>(R.id.view_pager)?.currentItem = index
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ViewModel 연결 (Activity 범위 공유)
        viewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        // 뷰 바인딩
        drawerLayout = view.findViewById(R.id.drawer_layout)
        toolbar = view.findViewById(R.id.toolbar)
        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager = view.findViewById(R.id.view_pager)
        recyclerViewHistory = view.findViewById(R.id.recyclerViewHistory)

        // 드로어 열릴 때마다 히스토리 새로고침
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerOpened(drawerView: View) {
                Log.d("HomeFragment", "📂 드로어 열림 → 히스토리 강제 새로고침")
                loadHistory()
            }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // RecyclerView 초기화
        recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = HistoryAdapter(historyItems) { item ->
            // 아이템 클릭 시 ViewModel에 설정 + 오디오 재생
            viewModel.setCurrentItem(item)
            val uri = Uri.parse(item.audioUrl)
            viewModel.setAudioUri(uri)
            viewModel.reinitializeMediaPlayer(requireContext(), uri)
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        recyclerViewHistory.adapter = historyAdapter

        // 툴바 햄버거 메뉴 클릭 시 드로어 열기
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // 상단 탭 구성 (AI 요약, 스크립트)
        val adapter = ViewPagerAdapter(this).apply {
            addFragment(SummaryFragment(), "AI 요약")
            addFragment(ScriptFragment(), "스크립트")
        }
        viewPager.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = adapter.getPageTitle(pos)
        }.attach()

        // 앱 시작 시 히스토리 한번 로드
        loadHistory()
    }

    /**
     * Firestore에서 현재 유저의 요약 이력(TranscriptionItem)들을 로드하여 RecyclerView에 표시
     */
    private fun loadHistory() {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("transcriptions")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp") // 최신순 정렬
            .get()
            .addOnSuccessListener { snap ->
                val newItems = mutableListOf<TranscriptionItem>()

                for (doc in snap) {
                    // Segment 리스트 파싱
                    val segmentsRaw = doc.get("segments") as? List<Map<String, Any>> ?: emptyList()
                    val segmentList = segmentsRaw.mapNotNull { seg ->
                        val start = (seg["start"] as? Number)?.toFloat()
                        val end = (seg["end"] as? Number)?.toFloat()
                        val text = seg["text"] as? String
                        if (start != null && end != null && text != null) {
                            Segment(start, end, text)
                        } else null
                    }

                    // Paragraph 리스트 파싱
                    val paragraphsRaw = doc.get("paragraphs") as? List<Map<String, Any>> ?: emptyList()
                    val paragraphList = paragraphsRaw.mapNotNull { para ->
                        val start = (para["startTime"] as? Number)?.toFloat()
                        val end = (para["endTime"] as? Number)?.toFloat()
                        val text = para["text"] as? String
                        if (start != null && end != null && text != null) {
                            Paragraph(start, end, text)
                        } else null
                    }

                    val title = doc.getString("title") ?: "(제목 없음)"

                    // TranscriptionItem 객체로 저장
                    newItems += TranscriptionItem(
                        docId = doc.id,
                        audioFileName = doc.getString("audioFileName") ?: "",
                        audioUrl = doc.getString("audioUrl") ?: "",
                        transcribedText = doc.getString("transcribedText") ?: "",
                        aiSummary = doc.getString("aiSummary") ?: "",
                        uploadDate = doc.getString("uploadDate") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        segments = segmentList,
                        paragraphs = paragraphList,
                        title = title
                    )
                }

                // 어댑터에 데이터 반영
                historyItems.clear()
                historyItems.addAll(newItems)
                recyclerViewHistory.post {
                    historyAdapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "히스토리 불러오기 실패: ${e.message}")
            }
    }
}
