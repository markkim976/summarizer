package com.example.summarizer.view.quiz

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.summarizer.R
import com.example.summarizer.adapter.WrongNoteAdapter
import com.example.summarizer.model.quiz.ChatGPTQuizGenerator
import com.example.summarizer.model.quiz.WrongNoteItem

data class QuizItem(
    val audioFileName: String,
    val aiSummary: String,
    val documentId: String
)

data class QuizQuestion(
    val question: String,
    val answer: Boolean,
    val explanation: String = ""
)

data class MCQQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val explanation: String = ""
)


class QuizFragment : Fragment() {

    private lateinit var layoutSetup: LinearLayout
    private lateinit var layoutQuiz: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var layoutWrongNoteOverlay: FrameLayout
    private lateinit var recyclerWrongNotes: RecyclerView
    private lateinit var btnCloseWrongNote: Button

    private lateinit var spinnerHistoryFiles: Spinner
    private lateinit var radioGroupType: RadioGroup
    private lateinit var btnGenerateQuiz: Button
    private lateinit var btnShowWrongNote: Button
    private lateinit var tvEmptyWrongNote: TextView


    private lateinit var tvQuizProgress: TextView
    private lateinit var tvQuizQuestion: TextView
    private lateinit var layoutOptions: LinearLayout
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnSubmit: Button

    private lateinit var tvQuizScore: TextView
    private lateinit var btnShowReview: Button
    private lateinit var btnBackToMain: Button

    private lateinit var layoutReviewOverlay: FrameLayout
    private lateinit var btnCloseReview: Button
    private lateinit var layoutReviewList: LinearLayout

    private var selectedDocId: String? = null

    private lateinit var quizQuestions: List<QuizQuestion>
    private var userAnswers: MutableList<Boolean?> = mutableListOf()
    private var currentQuestionIndex = 0

    private lateinit var mcqQuestions: List<MCQQuestion>
    private var mcqUserAnswers: MutableList<Int?> = mutableListOf()
    private var mcqCurrentIndex = 0

    private lateinit var wrongNoteAdapter: WrongNoteAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_quiz, container, false)
        setupViews(view)
        setupListeners()
        loadHistory() // 여기서 호출
        return view

    }


    private fun setupViews(view: View) {
        layoutSetup = view.findViewById(R.id.layoutSetup)
        layoutQuiz = view.findViewById(R.id.layoutQuiz)
        layoutResult = view.findViewById(R.id.layoutResult)

        spinnerHistoryFiles = view.findViewById(R.id.spinnerHistoryFiles)
        radioGroupType = view.findViewById(R.id.radioGroupType)
        btnGenerateQuiz = view.findViewById(R.id.btnGenerateQuiz)
        btnShowWrongNote = view.findViewById(R.id.btnShowWrongNote)

        tvQuizProgress = view.findViewById(R.id.tvQuizProgress)
        tvQuizQuestion = view.findViewById(R.id.tvQuizQuestion)
        layoutOptions = view.findViewById(R.id.layoutOptions)
        btnPrev = view.findViewById(R.id.btnPrev)
        btnNext = view.findViewById(R.id.btnNext)
        btnSubmit = view.findViewById(R.id.btnSubmit)

        tvQuizScore = view.findViewById(R.id.tvQuizScore)
        btnShowReview = view.findViewById(R.id.btnShowReview)
        btnBackToMain = view.findViewById(R.id.btnBackToMain)

        layoutReviewOverlay = view.findViewById(R.id.layoutReviewOverlay)
        btnCloseReview = view.findViewById(R.id.btnCloseReview)
        layoutReviewList = view.findViewById(R.id.layoutReviewList)

        layoutWrongNoteOverlay = view.findViewById(R.id.layoutWrongNoteOverlay)
        recyclerWrongNotes = view.findViewById(R.id.recyclerWrongNotes)
        btnCloseWrongNote = view.findViewById(R.id.btnCloseWrongNote)

        wrongNoteAdapter = WrongNoteAdapter(emptyList<WrongNoteItem>().toMutableList())
        recyclerWrongNotes.layoutManager = LinearLayoutManager(requireContext())
        recyclerWrongNotes.adapter = wrongNoteAdapter

        tvEmptyWrongNote = view.findViewById(R.id.tvEmptyWrongNote)

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedItem = wrongNoteAdapter.items[position]
                wrongNoteAdapter.removeItemAt(position)
                removeWrongNoteFromFirebase(removedItem)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val paint = Paint().apply { color = Color.RED }
                val background = RectF(
                    itemView.right + dX, itemView.top.toFloat(),
                    itemView.right.toFloat(), itemView.bottom.toFloat()
                )
                c.drawRect(background, paint)

                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 48f
                    textAlign = Paint.Align.RIGHT
                    isAntiAlias = true
                }
                c.drawText("삭제", itemView.right - 50f, itemView.top + itemView.height / 2f + 16f, textPaint)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerWrongNotes)


    }



    private fun generateOXQuestions(summary: String): List<QuizQuestion> {
        val sentences = summary.split(". ", "?", "!", "\n").map { it.trim() }.filter { it.length > 10 }

        return sentences.shuffled().take(5).map { sentence ->
            // 실제론 ChatGPT 등으로 문제 생성하는 게 좋지만, 지금은 문장을 그대로 사용
            QuizQuestion(sentence, true) // 정답을 전부 "O"로 고정
        }
    }

    private fun showCurrentQuestion() {
        val question = quizQuestions[currentQuestionIndex]
        tvQuizProgress.text = "${currentQuestionIndex + 1} / ${quizQuestions.size}"
        tvQuizQuestion.text = question.question

        layoutOptions.removeAllViews()
        val yesButton = RadioButton(requireContext()).apply {
            text = "O (맞다)"
            isChecked = userAnswers[currentQuestionIndex] == true
            setOnClickListener { userAnswers[currentQuestionIndex] = true }
        }

        val noButton = RadioButton(requireContext()).apply {
            text = "X (아니다)"
            isChecked = userAnswers[currentQuestionIndex] == false
            setOnClickListener { userAnswers[currentQuestionIndex] = false }
        }

        val group = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
            addView(yesButton)
            addView(noButton)
        }

        layoutOptions.addView(group)

        btnPrev.visibility = if (currentQuestionIndex > 0) View.VISIBLE else View.GONE
        btnNext.visibility = if (currentQuestionIndex < quizQuestions.lastIndex) View.VISIBLE else View.GONE
        btnSubmit.visibility = if (currentQuestionIndex == quizQuestions.lastIndex) View.VISIBLE else View.GONE
    }

    private fun showMCQQuestion() {
        val question = mcqQuestions[mcqCurrentIndex]
        tvQuizProgress.text = "${mcqCurrentIndex + 1} / ${mcqQuestions.size}"
        tvQuizQuestion.text = question.question

        layoutOptions.removeAllViews()

        val radioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.VERTICAL
        }

        question.options.forEachIndexed { index, optionText ->
            val radioButton = RadioButton(requireContext()).apply {
                text = optionText
                isChecked = mcqUserAnswers[mcqCurrentIndex] == index
                setOnClickListener {
                    mcqUserAnswers[mcqCurrentIndex] = index
                }
            }
            radioGroup.addView(radioButton)
        }

        layoutOptions.addView(radioGroup)

        btnPrev.visibility = if (mcqCurrentIndex > 0) View.VISIBLE else View.GONE
        btnNext.visibility = if (mcqCurrentIndex < mcqQuestions.lastIndex) View.VISIBLE else View.GONE
        btnSubmit.visibility = if (mcqCurrentIndex == mcqQuestions.lastIndex) View.VISIBLE else View.GONE
    }

    private fun showQuizReview() {
        layoutReviewList.removeAllViews()
        layoutReviewOverlay.visibility = View.VISIBLE

        val isOX = radioGroupType.checkedRadioButtonId == R.id.radioOX
        val total = if (isOX) quizQuestions.size else mcqQuestions.size

        for (i in 0 until total) {
            val questionText = if (isOX) quizQuestions[i].question else mcqQuestions[i].question
            val userAnswer = if (isOX) userAnswers[i] else mcqUserAnswers[i]
            val correctAnswer = if (isOX) quizQuestions[i].answer else mcqQuestions[i].correctIndex
            val explanation = if (isOX) quizQuestions[i].explanation else mcqQuestions[i].explanation

            val reviewView = layoutInflater.inflate(R.layout.item_quiz_review, layoutReviewList, false)

            val tvQuestion = reviewView.findViewById<TextView>(R.id.tvReviewQuestion)
            val tvUserAnswer = reviewView.findViewById<TextView>(R.id.tvReviewUserAnswer)
            val tvCorrectAnswer = reviewView.findViewById<TextView>(R.id.tvReviewCorrectAnswer)
            val tvResult = reviewView.findViewById<TextView>(R.id.tvReviewResult)

            tvQuestion.text = "문제 ${i + 1}. $questionText"
            tvUserAnswer.text = "내 답: ${formatAnswer(userAnswer)}"
            tvCorrectAnswer.text = "정답: ${formatAnswer(correctAnswer)}"
            tvResult.text = if (userAnswer == correctAnswer) "✅ 정답" else "❌ 오답"

            // 해설 추가
            val tvExplanation = reviewView.findViewById<TextView>(R.id.tvWrongExplanation)
            tvExplanation.text = "해설: $explanation"

            // ✅ 객관식 문제일 경우 보기 목록 추가
            val tvOptions = reviewView.findViewById<TextView>(R.id.tvWrongOptions)
            if (!isOX) {
                val optionsText = mcqQuestions[i].options.withIndex().joinToString("\n") { (index, option) ->
                    val label = listOf("A", "B", "C", "D").getOrNull(index) ?: "${index + 1}"
                    "$label. $option"
                }
                tvOptions.text = "보기:\n$optionsText"
                tvOptions.visibility = View.VISIBLE
            } else {
                tvOptions.visibility = View.GONE // OX문제면 숨김
            }

            layoutReviewList.addView(reviewView)
        }
    }




    private fun formatAnswer(answer: Any?): String {
        return when (answer) {
            is Boolean -> if (answer) "O" else "X"
            is Int -> listOf("A", "B", "C", "D").getOrNull(answer) ?: "?"
            else -> answer?.toString() ?: "?"
        }
    }










    private fun setupListeners() {
        btnShowWrongNote.setOnClickListener {
            if (selectedDocId.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "파일을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showWrongNoteOverlay()
        }

        btnCloseWrongNote.setOnClickListener {
            layoutWrongNoteOverlay.visibility = View.GONE
        }

        btnShowReview.setOnClickListener {
            showQuizReview()
            layoutReviewOverlay.visibility = View.VISIBLE
        }
        btnCloseReview.setOnClickListener {
            layoutReviewOverlay.visibility = View.GONE
        }


        btnBackToMain.setOnClickListener {
            // 메인으로 이동 처리 (예: requireActivity().onBackPressedDispatcher.onBackPressed())
        }

        btnGenerateQuiz.setOnClickListener {
            val docId = selectedDocId
            if (docId.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "퀴즈를 만들 파일을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val db = FirebaseFirestore.getInstance()
            db.collection("transcriptions").document(docId)
                .get()
                .addOnSuccessListener { document ->
                    val summaryText = document.getString("aiSummary")?.trim() ?: ""
                    if (summaryText.isEmpty()) {
                        Toast.makeText(requireContext(), "선택된 문서에 요약 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val selectedRadioId = radioGroupType.checkedRadioButtonId
                    val wrongOXList = document.get("wrongOXQuestions") as? List<Map<String, Any>> ?: emptyList()
                    val wrongMCQList = document.get("wrongMCQQuestions") as? List<Map<String, Any>> ?: emptyList()

                    if (selectedRadioId == R.id.radioOX) {
                        val reused = wrongOXList.take(5).mapNotNull {
                            val q = it["question"] as? String
                            val a = it["correctAnswer"]
                            val e = it["explanation"] as? String ?: ""
                            if (q != null && a is Boolean) QuizQuestion(q, a, e) else null
                        }

                        val needed = 5 - reused.size
                        if (needed == 0) {
                            quizQuestions = reused
                            userAnswers = MutableList(5) { null }
                            currentQuestionIndex = 0
                            showCurrentQuestion()
                            layoutSetup.visibility = View.GONE
                            layoutQuiz.visibility = View.VISIBLE
                            return@addOnSuccessListener
                        }

                        ChatGPTQuizGenerator.generateQuizFromSummary(
                            summary = summaryText,
                            callback = { generated ->
                                val combined = reused + generated.take(needed)
                                quizQuestions = combined
                                userAnswers = MutableList(5) { null }
                                currentQuestionIndex = 0
                                showCurrentQuestion()
                                layoutSetup.visibility = View.GONE
                                layoutQuiz.visibility = View.VISIBLE
                            },
                            onError = { msg ->
                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                    } else if (selectedRadioId == R.id.radioMultipleChoice) {
                        val reused = wrongMCQList.take(5).mapNotNull {
                            val q = it["question"] as? String
                            val correct = (it["correctAnswer"] as? Long)?.toInt()
                            val options = (it["options"] as? List<*>)?.filterIsInstance<String>()
                            val explanation = it["explanation"] as? String ?: ""
                            if (q != null && correct != null && options != null && options.size == 4)
                                MCQQuestion(q, options, correct, explanation)
                            else null
                        }

                        val needed = 5 - reused.size
                        if (needed == 0) {
                            mcqQuestions = reused
                            mcqUserAnswers = MutableList(5) { null }
                            mcqCurrentIndex = 0
                            showMCQQuestion()
                            layoutSetup.visibility = View.GONE
                            layoutQuiz.visibility = View.VISIBLE
                            return@addOnSuccessListener
                        }

                        ChatGPTQuizGenerator.generateMCQ(
                            summary = summaryText,
                            callback = { generated ->
                                val combined = reused + generated.take(needed)
                                mcqQuestions = combined
                                mcqUserAnswers = MutableList(5) { null }
                                mcqCurrentIndex = 0
                                showMCQQuestion()
                                layoutSetup.visibility = View.GONE
                                layoutQuiz.visibility = View.VISIBLE
                            },
                            onError = { msg ->
                                requireActivity().runOnUiThread {
                                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Toast.makeText(requireContext(), "퀴즈 유형을 선택해주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "요약 데이터를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
        }





        btnPrev.setOnClickListener {
            if (radioGroupType.checkedRadioButtonId == R.id.radioOX) {
                if (currentQuestionIndex > 0) {
                    currentQuestionIndex--
                    showCurrentQuestion()
                }
            } else {
                if (mcqCurrentIndex > 0) {
                    mcqCurrentIndex--
                    showMCQQuestion()
                }
            }
        }

        btnNext.setOnClickListener {
            if (radioGroupType.checkedRadioButtonId == R.id.radioOX) {
                if (currentQuestionIndex < quizQuestions.lastIndex) {
                    currentQuestionIndex++
                    showCurrentQuestion()
                }
            } else {
                if (mcqCurrentIndex < mcqQuestions.lastIndex) {
                    mcqCurrentIndex++
                    showMCQQuestion()
                }
            }
        }

        btnSubmit.setOnClickListener {
            if (radioGroupType.checkedRadioButtonId == R.id.radioOX) {
                val correctCount = quizQuestions.indices.count { i ->
                    userAnswers[i] == quizQuestions[i].answer
                }
                tvQuizScore.text = "당신의 점수는 $correctCount / ${quizQuestions.size} 입니다!"
            } else {
                val correctCount = mcqQuestions.indices.count { i ->
                    mcqUserAnswers[i] == mcqQuestions[i].correctIndex
                }
                tvQuizScore.text = "당신의 점수는 $correctCount / ${mcqQuestions.size} 입니다!"
            }

            // ✅ 오답 저장 호출 (OX/MCQ 둘 다 자동 처리)
            updateWrongQuestionsToFirebase()

            layoutQuiz.visibility = View.GONE
            layoutResult.visibility = View.VISIBLE
        }



        btnBackToMain.setOnClickListener {
            // 메인화면(퀴즈 선택화면)으로 돌아가기
            layoutResult.visibility = View.GONE
            layoutSetup.visibility = View.VISIBLE
        }



    }

    private fun showWrongNoteOverlay() {
        val docId = selectedDocId ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("transcriptions").document(docId)
            .get()
            .addOnSuccessListener { doc ->
                val wrongNotes = doc.get("wrongNoteQuestions") as? List<Map<String, Any>> ?: emptyList()
                val items = wrongNotes.mapNotNull {
                    val question = it["question"] as? String ?: return@mapNotNull null
                    val correctAnswer = it["correctAnswer"]
                    val explanation = it["explanation"] as? String ?: ""
                    val type = it["type"] as? String ?: "OX"
                    val options = (it["options"] as? List<*>)?.filterIsInstance<String>()
                    WrongNoteItem(question, correctAnswer, explanation, options, type)
                }

                if (items.isEmpty()) {
                    recyclerWrongNotes.visibility = View.GONE
                    tvEmptyWrongNote.visibility = View.VISIBLE
                } else {
                    wrongNoteAdapter.updateData(items)
                    recyclerWrongNotes.visibility = View.VISIBLE
                    tvEmptyWrongNote.visibility = View.GONE
                }

                layoutWrongNoteOverlay.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "오답노트 로드 실패", Toast.LENGTH_SHORT).show()
            }
    }


    private fun removeWrongNoteFromFirebase(item: WrongNoteItem) {
        val docId = selectedDocId ?: return
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("transcriptions").document(docId)

        docRef.get().addOnSuccessListener { snapshot ->
            val existing = snapshot.get("wrongNoteQuestions") as? List<Map<String, Any>> ?: return@addOnSuccessListener
            val updated = existing.filterNot {
                it["question"] == item.question && it["type"] == item.type
            }

            docRef.update("wrongNoteQuestions", updated)
                .addOnSuccessListener { Log.d("Firebase", "오답 삭제됨") }
                .addOnFailureListener { Log.e("Firebase", "삭제 실패", it) }
        }
    }


    // ✅ updateWrongQuestionsToFirebase 함수에 오답노트 누적 저장 추가
    private fun updateWrongQuestionsToFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docId = selectedDocId ?: return
        val db = FirebaseFirestore.getInstance()

        val isOX = radioGroupType.checkedRadioButtonId == R.id.radioOX
        val fieldName = if (isOX) "wrongOXQuestions" else "wrongMCQQuestions"

        val newWrongList = mutableListOf<Map<String, Any>>()
        val newWrongNoteList = mutableListOf<Map<String, Any>>()

        for (i in 0 until 5) {
            val correct = if (isOX) quizQuestions[i].answer else mcqQuestions[i].correctIndex
            val user = if (isOX) userAnswers[i] else mcqUserAnswers[i]

            if (user != correct) {
                val question = if (isOX) quizQuestions[i].question else mcqQuestions[i].question
                val explanation = if (isOX) quizQuestions[i].explanation else mcqQuestions[i].explanation

                val wrongItem = if (isOX) {
                    mapOf(
                        "question" to question,
                        "type" to "OX",
                        "correctAnswer" to correct,
                        "explanation" to explanation
                    )
                } else {
                    mapOf(
                        "question" to question,
                        "type" to "MCQ",
                        "correctAnswer" to correct,
                        "options" to mcqQuestions[i].options,
                        "explanation" to explanation
                    )
                }

                newWrongList.add(wrongItem)
                newWrongNoteList.add(wrongItem)
            }
        }

        val docRef = db.collection("transcriptions").document(docId)

        docRef.get().addOnSuccessListener { snapshot ->
            val existingList = snapshot.get(fieldName) as? List<Map<String, Any>> ?: emptyList()

            val filteredOld = existingList.filter { old ->
                val question = old["question"] as? String ?: return@filter true
                val isStillWrong = newWrongList.any { it["question"] == question }
                isStillWrong
            }

            val mergedWrongList = (filteredOld + newWrongList).distinctBy { it["question"] }
            val existingWrongNote = snapshot.get("wrongNoteQuestions") as? List<Map<String, Any>> ?: emptyList()
            val mergedWrongNote = (existingWrongNote + newWrongNoteList).distinctBy { it["question"] }

            val updates = mapOf(
                fieldName to mergedWrongList,
                "wrongNoteQuestions" to mergedWrongNote
            )

            docRef.update(updates)
                .addOnSuccessListener { Log.d("Firestore", "✅ 오답 및 오답노트 저장 완료") }
                .addOnFailureListener { Log.e("Firestore", "❌ 오답 저장 실패", it) }
        }.addOnFailureListener {
            Log.e("Firestore", "❌ 문서 가져오기 실패", it)
        }
    }



    private fun loadHistory() {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("transcriptions")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { snapshot ->
                val fileList = mutableListOf<Pair<String, String>>()
                fileList.add("파일을 선택해주세요" to "") // 기본 안내

                for (doc in snapshot) {
                    val title = doc.getString("title") ?: doc.getString("audioFileName") ?: continue
                    val docId = doc.id
                    fileList.add(title to docId)
                }

                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.spinner_history_item,
                    fileList.map { it.first } // 표시용 title
                )
                adapter.setDropDownViewResource(R.layout.spinner_history_item)
                spinnerHistoryFiles.adapter = adapter

                spinnerHistoryFiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                        selectedDocId = if (fileList[position].second.isNotEmpty()) {
                            fileList[position].second
                        } else {
                            null
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "히스토리 불러오기 실패", Toast.LENGTH_SHORT).show()
            }
    }








}
