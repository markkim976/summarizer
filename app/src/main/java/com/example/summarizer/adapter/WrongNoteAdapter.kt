package com.example.summarizer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.summarizer.model.quiz.WrongNoteItem
import com.example.summarizer.R

/**
 * 오답노트 항목을 리스트로 표시하는 RecyclerView 어댑터
 * - WrongNoteItem 리스트를 받아 각 문제와 정답, 해설, 보기 등을 화면에 출력함
 */
class WrongNoteAdapter(var items: MutableList<WrongNoteItem>) :
    RecyclerView.Adapter<WrongNoteAdapter.ViewHolder>() {

    // 각 오답노트 항목의 뷰홀더 정의
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuestion: TextView = itemView.findViewById(R.id.tvWrongQuestion)
        val tvCorrect: TextView = itemView.findViewById(R.id.tvWrongCorrect)
        val tvExplanation: TextView = itemView.findViewById(R.id.tvWrongExplanation)
        val tvOptions: TextView? = itemView.findViewById(R.id.tvWrongOptions)
    }

    // 뷰홀더 생성 시 XML inflate
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wrong_note, parent, false)
        return ViewHolder(view)
    }

    // 각 아이템 데이터 바인딩
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvQuestion.text = "문제: ${item.question}"

        // 정답 포맷 처리: OX 또는 객관식 번호(A~D)
        val formattedAnswer = when (item.correctAnswer) {
            is Boolean -> if (item.correctAnswer) "O" else "X"
            is Number -> {
                val index = item.correctAnswer.toInt()
                listOf("A", "B", "C", "D").getOrNull(index) ?: index.toString()
            }
            else -> item.correctAnswer.toString()
        }
        holder.tvCorrect.text = "정답: $formattedAnswer"
        holder.tvExplanation.text = "해설: ${item.explanation}"

        // 보기 출력: 보기 항목이 있을 경우 포맷팅
        if (item.options != null && holder.tvOptions != null) {
            val optionLabels = listOf("A", "B", "C", "D")
            val optionText = item.options.withIndex().joinToString("\n") { (i, opt) ->
                "${optionLabels.getOrElse(i) { (i + 1).toString() }}. $opt"
            }
            holder.tvOptions.visibility = View.VISIBLE
            holder.tvOptions.text = "보기:\n$optionText"
        } else {
            holder.tvOptions?.visibility = View.GONE
        }
    }

    // 전체 아이템 수 반환
    override fun getItemCount(): Int = items.size

    // 특정 항목 삭제 시 호출
    fun removeItemAt(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    // 데이터 전체 갱신 시 호출
    fun updateData(newItems: List<WrongNoteItem>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }
}
