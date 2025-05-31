package com.example.summarizer.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2에 Fragment를 동적으로 연결하기 위한 어댑터
 * - 내부에 Fragment와 Title 리스트를 함께 관리
 * - 사용 예: TabLayout + ViewPager2 연결 시 ScriptFragment, SummaryFragment 등을 보여줄 때 사용
 */
class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // 실제로 표시될 Fragment 목록
    private val fragments = mutableListOf<Fragment>()

    // 각 Fragment에 대응하는 탭 제목
    private val titles = mutableListOf<String>()

    // 외부에서 Fragment와 제목을 추가하는 메서드
    fun addFragment(fragment: Fragment, title: String) {
        titles.add(title)
        fragments.add(fragment)
    }

    // ViewPager2에서 필요한 Fragment 개수 반환
    override fun getItemCount(): Int = fragments.size

    // position에 해당하는 Fragment 반환
    override fun createFragment(position: Int): Fragment = fragments[position]

    // 탭 제목 반환 (TabLayoutMediator에서 수동으로 호출될 수 있음)
    fun getPageTitle(position: Int): String = titles[position]
}
