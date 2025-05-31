package com.example.summarizer.model.summary

import android.net.Uri
import android.util.Log
import com.example.summarizer.model.common.TranscriptionItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

/**
 * 음성 파일 및 요약 결과 데이터를 Firebase에 업로드하는 객체
 * - Firebase Storage: 음성 파일
 * - Firestore: 전체 데이터 (스크립트, 요약 등)
 * - Realtime Database: 요약 제목 + 문서 ID 저장 (달력 메모 연동용)
 */
object FirebaseUploader {
    private const val TAG = "FirebaseUploader"

    /**
     * 전체 데이터 업로드 함수
     * @param item TranscriptionItem: 전체 변환 결과 객체
     */
    fun uploadData(item: TranscriptionItem) {
        val storageReference = FirebaseStorage.getInstance().reference
        val fileRef = storageReference.child("audioFiles/${item.audioFileName}")
        val audioUri = Uri.parse(item.audioUrl)

        // 음성 파일 Firebase Storage에 업로드
        fileRef.putFile(audioUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                fileRef.downloadUrl // 다운로드 URL 획득
            }
            .addOnSuccessListener { downloadUrl ->
                // 현재 로그인된 사용자 정보 가져오기
                val currentUser = FirebaseAuth.getInstance().currentUser
                val userId = currentUser?.uid ?: "unknown"
                val userName = currentUser?.displayName ?: ""
                val userEmail = currentUser?.email ?: ""

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val uploadDate = sdf.format(Date())

                // Firestore에 저장할 데이터 구성
                val data = hashMapOf(
                    "audioUrl" to downloadUrl.toString(),
                    "audioFileName" to item.audioFileName,
                    "transcribedText" to item.transcribedText,
                    "aiSummary" to item.aiSummary,
                    "title" to item.title, // 요약 제목
                    "timestamp" to item.timestamp,
                    "uploadDate" to uploadDate,
                    "userId" to userId,
                    "userName" to userName,
                    "userEmail" to userEmail,
                    "segments" to item.segments.map {
                        mapOf("start" to it.start, "end" to it.end, "text" to it.text)
                    },
                    "paragraphs" to item.paragraphs.map {
                        mapOf("startTime" to it.startTime, "endTime" to it.endTime, "text" to it.text)
                    }
                )

                Log.d(TAG, "업로드 전 item 내용: $item")

                // Firestore에 업로드
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("transcriptions")
                    .add(data)
                    .addOnSuccessListener { docRef ->
                        val documentId = docRef.id
                        Log.d(TAG, "Firestore 저장 성공: $documentId")

                        // ✅ Realtime Database에도 요약 제목 저장 (문서 ID와 함께)
                        val db = FirebaseDatabase.getInstance("https://lecturesummarizer-ae95d-default-rtdb.asia-southeast1.firebasedatabase.app").reference
                        val memoRef = db.child("memos").child(userId).child(uploadDate)

                        val memoText = "[요약제목] ${item.title}::$documentId"

                        // 동일 문서 ID가 이미 저장되어 있는지 확인
                        memoRef.get().addOnSuccessListener { snapshot ->
                            val exists = snapshot.children.any {
                                it.getValue(String::class.java)?.contains(documentId) == true
                            }

                            if (!exists) {
                                memoRef.push().setValue(memoText)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Realtime에 요약제목 저장 성공")
                                    }
                                    .addOnFailureListener {
                                        Log.e(TAG, "Realtime 저장 실패: ${it.message}")
                                    }
                            } else {
                                Log.d(TAG, "이미 저장된 제목. 중복 생략됨.")
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Firestore 저장 실패: ${it.message}")
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Storage 업로드 실패: ${it.message}")
            }
    }
}
