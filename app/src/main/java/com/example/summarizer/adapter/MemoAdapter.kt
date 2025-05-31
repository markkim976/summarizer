package com.example.summarizer.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.summarizer.R

/**
 * 날짜별 메모를 표시하는 RecyclerView Adapter
 * - summaryList: "[요약제목]"이 포함된 요약 메모 목록
 * - normalList: 일반 텍스트 메모 목록
 * - onItemDeleted: 아이템 삭제 콜백 (위치, 내용, 요약 여부 전달)
 * - onItemClicked: 아이템 클릭 콜백 (위치, 내용, 요약 여부 전달)
 */
class MemoAdapter(
    private var summaryList: MutableList<String>,
    private var normalList: MutableList<String>,
    private val onItemDeleted: (position: Int, item: String, isSummary: Boolean) -> Unit,
    private val onItemClicked: (position: Int, item: String, isSummary: Boolean) -> Unit
) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    // 뷰홀더 클래스: 메모 항목 1개를 표현
    inner class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMemo: TextView = view.findViewById(R.id.textMemoContent)

        init {
            // 항목 클릭 시 콜백 호출
            view.setOnClickListener {
                val pos = adapterPosition
                val (item, isSummary) = getItemAt(pos) ?: return@setOnClickListener
                Log.d("MemoAdapter", "메모 클릭됨: $item")
                onItemClicked(pos, item, isSummary)
            }
        }
    }

    // 아이템 레이아웃 inflate 후 ViewHolder 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view)
    }

    // 각 항목의 데이터 바인딩
    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val (rawItem, isSummary) = getItemAt(position) ?: return

        // "[요약제목]제목::docId" 형식이면 제목만 추출
        val displayText = if (isSummary) {
            rawItem.removePrefix("[요약제목]").split("::").firstOrNull()?.trim() ?: rawItem
        } else {
            rawItem
        }

        holder.textMemo.text = displayText
        holder.textMemo.setTextColor(if (isSummary) Color.parseColor("#1565C0") else Color.BLACK)
        holder.textMemo.setTypeface(null, if (isSummary) Typeface.BOLD else Typeface.NORMAL)
    }

    // 전체 아이템 개수 반환
    override fun getItemCount(): Int = summaryList.size + normalList.size

    // 외부에서 리스트 갱신 시 사용
    fun updateList(summaries: List<String>, normals: List<String>) {
        summaryList = summaries.toMutableList()
        normalList = normals.toMutableList()
        notifyDataSetChanged()
    }

    // 특정 위치 아이템 삭제 요청
    fun deleteItem(position: Int) {
        val (item, isSummary) = getItemAt(position) ?: return
        onItemDeleted(position, item, isSummary)
    }

    // 포지션 기반으로 요약인지 일반인지 판단하여 항목 반환
    fun getItemAt(position: Int): Pair<String, Boolean>? {
        return when {
            position < summaryList.size -> summaryList[position] to true
            position - summaryList.size < normalList.size -> normalList[position - summaryList.size] to false
            else -> null
        }
    }

    // 전체 메모 리스트 반환
    fun getAllMemos(): List<String> = summaryList + normalList
}
