package com.example.summarizer.view.calendar

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.summarizer.R
import com.example.summarizer.adapter.MemoAdapter
import com.example.summarizer.model.common.Paragraph
import com.example.summarizer.model.common.Segment
import com.example.summarizer.model.common.TranscriptionItem
import com.example.summarizer.viewmodel.HomeViewModel

class MemoDialogFragment : DialogFragment() {

    private lateinit var textDateTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var editMemoInput: EditText
    private lateinit var btnSaveMemo: Button

    private lateinit var userId: String
    private lateinit var dateKey: String
    private lateinit var adapter: MemoAdapter

    private var summaryList: MutableList<String> = mutableListOf()
    private var normalList: MutableList<String> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_memo_dialog, container, false)

        dateKey = arguments?.getString("dateKey") ?: ""
        val allList = arguments?.getStringArrayList("memoList")?.toMutableList() ?: arrayListOf()

        summaryList = allList.filter { it.startsWith("[요약제목]") }.toMutableList()
        normalList = allList.filterNot { it.startsWith("[요약제목]") }.toMutableList()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            dismiss()
            return view
        }
        userId = currentUser.uid

        textDateTitle = view.findViewById(R.id.textDateTitle)
        recyclerView = view.findViewById(R.id.recyclerViewMemos)
        editMemoInput = view.findViewById(R.id.editMemoInput)
        btnSaveMemo = view.findViewById(R.id.btnSaveMemo)

        textDateTitle.text = convertDateFormat(dateKey)

        adapter = MemoAdapter(
            summaryList,
            normalList,
            onItemDeleted = { pos, _, isSummary -> deleteMemo(pos) },
            onItemClicked = { pos, item, isSummary ->
                if (isSummary) {
                    val parts = item.removePrefix("[요약제목]").split("::")
                    if (parts.size == 2) fetchSummaryByDocId(parts[1].trim())
                    else Toast.makeText(requireContext(), "❌ 요약 형식 오류", Toast.LENGTH_SHORT).show()
                } else {
                    showEditMemoDialog(pos, item)
                }
            }
        )

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItemAt(position)

                if (item?.second == true) {
                    adapter.notifyItemChanged(position)
                    Toast.makeText(requireContext(), "요약제목은 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("삭제 확인")
                        .setMessage("정말 삭제하시겠습니까?")
                        .setPositiveButton("삭제") { _, _ ->
                            deleteMemo(position)
                        }
                        .setNegativeButton("취소") { _, _ ->
                            adapter.notifyItemChanged(position)
                        }
                        .setCancelable(false)
                        .show()
                }
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                val itemView = viewHolder.itemView
                val paint = Paint().apply { color = Color.RED }
                val background = RectF(
                    itemView.right + dX, itemView.top.toFloat(),
                    itemView.right.toFloat(), itemView.bottom.toFloat()
                )
                c.drawRect(background, paint)

                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 40f
                    textAlign = Paint.Align.RIGHT
                    isFakeBoldText = true
                }
                c.drawText("삭제", itemView.right - 50f, itemView.top + itemView.height / 2f + 15f, textPaint)
            }
        }).attachToRecyclerView(recyclerView)

        btnSaveMemo.setOnClickListener { saveNewMemo() }

        return view
    }

    private fun deleteMemo(position: Int) {
        val db = FirebaseDatabase.getInstance("https://lecturesummarizer-ae95d-default-rtdb.asia-southeast1.firebasedatabase.app").reference
        val memoRef = db.child("memos").child(userId).child(dateKey)

        val allMemos = adapter.getAllMemos()
        val targetValue = allMemos.getOrNull(position)

        if (targetValue != null && targetValue.startsWith("[요약제목]")) {
            Toast.makeText(requireContext(), "⚠️ 요약제목 메모는 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show()
            adapter.notifyItemChanged(position)
            return
        }

        memoRef.get().addOnSuccessListener { snapshot ->
            val targetKey = snapshot.children.firstOrNull { it.getValue(String::class.java) == targetValue }?.key

            if (targetKey != null) {
                memoRef.child(targetKey).removeValue().addOnSuccessListener {
                    normalList.remove(targetValue)
                    adapter.updateList(summaryList, normalList)
                    Toast.makeText(requireContext(), "✅ 메모 삭제 완료", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "❌ 삭제 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    adapter.notifyItemChanged(position)
                }
            } else {
                Toast.makeText(requireContext(), "❌ 메모 키를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                adapter.notifyItemChanged(position)
            }
        }
    }

    private fun saveNewMemo() {
        val newMemo = editMemoInput.text.toString().trim()
        if (newMemo.isNotEmpty()) {
            val db = FirebaseDatabase.getInstance("https://lecturesummarizer-ae95d-default-rtdb.asia-southeast1.firebasedatabase.app")
                .reference

            db.child("memos").child(userId).child(dateKey).push()
                .setValue(newMemo)
                .addOnSuccessListener {
                    normalList.add(newMemo)
                    adapter.updateList(summaryList, normalList)
                    editMemoInput.text.clear()
                    Toast.makeText(requireContext(), "✅ 메모 저장 완료", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "❌ 저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "메모를 입력해주세요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchSummaryByDocId(docId: String) {
        FirebaseFirestore.getInstance()
            .collection("transcriptions")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val item = TranscriptionItem(
                        docId = docId,
                        audioFileName = doc.getString("audioFileName") ?: "",
                        audioUrl = doc.getString("audioUrl") ?: "",
                        transcribedText = doc.getString("transcribedText") ?: "",
                        aiSummary = doc.getString("aiSummary") ?: "",
                        uploadDate = doc.getString("uploadDate") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        segments = (doc["segments"] as? List<Map<String, Any>>)?.mapNotNull {
                            val start = (it["start"] as? Number)?.toFloat()
                            val end = (it["end"] as? Number)?.toFloat()
                            val text = it["text"] as? String
                            if (start != null && end != null && text != null) Segment(start, end, text) else null
                        } ?: emptyList(),
                        paragraphs = (doc["paragraphs"] as? List<Map<String, Any>>)?.mapNotNull {
                            val start = (it["startTime"] as? Number)?.toFloat()
                            val end = (it["endTime"] as? Number)?.toFloat()
                            val text = it["text"] as? String
                            if (start != null && end != null && text != null) Paragraph(start, end, text) else null
                        } ?: emptyList()
                    )

                    val viewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]
                    viewModel.setCurrentItem(item)
                    val uri = Uri.parse(item.audioUrl)
                    viewModel.setAudioUri(uri)
                    viewModel.reinitializeMediaPlayer(requireContext(), uri)

                    findNavController().navigate(R.id.homeFragment)
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "❌ 요약 데이터를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "❌ 불러오기 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditMemoDialog(position: Int, oldText: String) {
        val editText = EditText(requireContext()).apply {
            setText(oldText)
            setSelection(text.length)
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("메모 수정")
            .setView(editText)
            .setPositiveButton("수정") { _, _ ->
                val updated = editText.text.toString().trim()
                if (updated.isNotEmpty()) {
                    val db = FirebaseDatabase.getInstance("https://lecturesummarizer-ae95d-default-rtdb.asia-southeast1.firebasedatabase.app").reference
                    val memoRef = db.child("memos").child(userId).child(dateKey)

                    val originalText = normalList.getOrNull(position - summaryList.size)

                    memoRef.get().addOnSuccessListener { snap ->
                        val key = snap.children.firstOrNull {
                            it.getValue(String::class.java) == originalText
                        }?.key

                        key?.let {
                            memoRef.child(it).setValue(updated)
                                .addOnSuccessListener {
                                    normalList[position - summaryList.size] = updated
                                    adapter.updateList(summaryList, normalList)
                                    Toast.makeText(requireContext(), "✅ 수정 완료", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(requireContext(), "❌ 수정 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        } ?: run {
                            Toast.makeText(requireContext(), "❌ 수정할 키를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    companion object {
        fun newInstance(dateKey: String, memoList: List<String>): MemoDialogFragment {
            return MemoDialogFragment().apply {
                arguments = Bundle().apply {
                    putString("dateKey", dateKey)
                    putStringArrayList("memoList", ArrayList(memoList))
                }
            }
        }
    }

    private fun convertDateFormat(dateKey: String): String {
        val parts = dateKey.split("-")
        return if (parts.size == 3) "${parts[0]}년 ${parts[1]}월 ${parts[2]}일" else dateKey
    }
}
