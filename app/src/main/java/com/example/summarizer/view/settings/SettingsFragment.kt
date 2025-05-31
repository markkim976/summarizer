package com.example.summarizer.view.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.example.summarizer.R
import com.example.summarizer.view.main.LoginActivity
import com.example.summarizer.viewmodel.HomeViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var viewModel: HomeViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        viewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        // 현재 로그인된 유저의 이메일 표시
        val email = FirebaseAuth.getInstance().currentUser?.email ?: "알 수 없음"
        findPreference<Preference>("account_email")?.summary = email

        // 로그아웃 처리
        findPreference<Preference>("logout")?.setOnPreferenceClickListener {
            val auth = FirebaseAuth.getInstance()
            auth.signOut()

            // Google 계정 로그아웃도 함께
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

            googleSignInClient.signOut().addOnCompleteListener {
                Toast.makeText(requireContext(), "로그아웃 되었습니다", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }

            true
        }

        // 전체 데이터 삭제 처리 (Realtime DB + Firestore)
        findPreference<Preference>("delete_all_data")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("데이터 삭제 확인")
                .setMessage("정말 모든 데이터를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("삭제") { _, _ ->
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val uid = user.uid
                        val baseUrl =
                            "https://lecturesummarizer-ae95d-default-rtdb.asia-southeast1.firebasedatabase.app"
                        val database = FirebaseDatabase.getInstance(baseUrl)
                        val paths = listOf("memos")

                        var successCount = 0
                        var failCount = 0

                        for (path in paths) {
                            database.getReference(path).child(uid).removeValue()
                                .addOnSuccessListener {
                                    successCount++
                                    if (successCount + failCount == paths.size) {
                                        deleteFirestoreTranscriptions(uid)
                                    }
                                }
                                .addOnFailureListener {
                                    failCount++
                                    if (successCount + failCount == paths.size) {
                                        Toast.makeText(
                                            requireContext(),
                                            "일부 데이터 삭제에 실패했습니다.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                        }
                    } else {
                        Toast.makeText(requireContext(), "로그인 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
            true
        }

        // 캐시 삭제
        findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
            val deletedBytes = clearAppCache()
            val readableSize = when {
                deletedBytes >= 1024 * 1024 -> String.format("%.2fMB", deletedBytes / (1024.0 * 1024))
                deletedBytes >= 1024 -> String.format("%.1fKB", deletedBytes / 1024.0)
                else -> "${deletedBytes}B"
            }

            Toast.makeText(
                requireContext(),
                "총 ${readableSize}의 캐시가 초기화되었습니다.",
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        // 피드백 이메일
        findPreference<Preference>("send_feedback")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:markkim976@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "summarizer 앱 피드백")
            }
            startActivity(intent)
            true
        }

        // 요약 방식 설정
        val summaryModePref = findPreference<ListPreference>("summary_mode")
        summaryModePref?.summary = summaryModePref?.entry
        summaryModePref?.setOnPreferenceChangeListener { preference, newValue ->
            val index = summaryModePref.findIndexOfValue(newValue.toString())
            preference.summary = summaryModePref.entries[index]
            true
        }

        // 키워드 포함 여부 설정
        val keywordSwitch = findPreference<SwitchPreferenceCompat>("include_keywords")
    }

    private fun clearAppCache(): Long {
        var totalBytesDeleted = 0L

        val cacheDir = requireContext().cacheDir
        cacheDir?.listFiles()?.forEach { file ->
            totalBytesDeleted += file.length()
            file.delete()
        }

        val sttTempFile = File(requireContext().filesDir, "stt_temp.wav")
        if (sttTempFile.exists()) {
            totalBytesDeleted += sttTempFile.length()
            sttTempFile.delete()
        }

        return totalBytesDeleted
    }

    private fun deleteFirestoreTranscriptions(uid: String) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("transcriptions")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "모든 데이터가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "삭제 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Firestore 조회 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
