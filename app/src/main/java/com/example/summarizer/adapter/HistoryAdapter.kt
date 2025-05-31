package com.example.summarizer.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.summarizer.model.common.TranscriptionItem
import com.example.summarizer.R

/**
 * 히스토리 목록을 표시하는 RecyclerView.Adapter
 * @param items Firestore에서 불러온 TranscriptionItem 리스트
 * @param onItemClick 아이템 클릭 시 호출되는 람다 (TranscriptionItem 전달)
 */
class HistoryAdapter(
    private val items: List<TranscriptionItem>,
    private val onItemClick: (TranscriptionItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    // ViewHolder를 생성하여 반환
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transcription, parent, false)
        return ViewHolder(view)
    }

    // ViewHolder에 데이터 바인딩
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onItemClick)
    }

    // 리스트 크기 반환
    override fun getItemCount(): Int = items.size

    // RecyclerView의 각 아이템을 위한 ViewHolder 클래스
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvUploadDate: TextView = itemView.findViewById(R.id.tvUploadDate)

        // TranscriptionItem의 데이터를 TextView에 바인딩
        fun bind(item: TranscriptionItem, onItemClick: (TranscriptionItem) -> Unit) {
            tvFileName.text = item.title.ifBlank { item.audioFileName } // 제목이 비어있으면 파일명 사용
            tvUploadDate.text = item.uploadDate

            // 아이템 클릭 시 람다 호출
            itemView.setOnClickListener {
                Log.d("HistoryAdapter", "Clicked Item - docId: ${item.docId}, title: ${item.title}")
                onItemClick(item)
            }
        }
    }
}
