package com.example.summarizer.view.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.summarizer.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth
    private val RC_SIGN_IN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Firebase 인증 초기화
        firebaseAuth = FirebaseAuth.getInstance()

        // 구글 로그인 옵션 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // 구글 로그인 클라이언트 설정
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 이미지 버튼으로 변경된 구글 로그인 버튼
        val signInButton = findViewById<ImageButton>(R.id.btn_google_sign_in)
        signInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d("Login", "✅ Google 로그인 성공: ${account.email}")
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            Log.e("Login", "❌ 로그인 실패 - 상태코드: ${e.statusCode}, 설명: ${GoogleSignInStatusCodes.getStatusCodeString(e.statusCode)}")
            Toast.makeText(this, "로그인 실패: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    Log.d("FirebaseAuth", "✅ Firebase 인증 성공: ${user?.email}")
                    Toast.makeText(this, "${user?.displayName}님 환영합니다!", Toast.LENGTH_SHORT).show()
                    saveUserToFirestore(account)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.e("FirebaseAuth", "❌ Firebase 인증 실패: ${task.exception?.message}")
                    Toast.makeText(this, "Firebase 인증 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserToFirestore(account: GoogleSignInAccount) {
        val db = FirebaseFirestore.getInstance()
        val usersRef = db.collection("users")
        val userId = account.id ?: return
        val userDoc = usersRef.document(userId)

        val userData = mapOf(
            "name" to (account.displayName ?: ""),
            "email" to (account.email ?: ""),
            "photoUrl" to (account.photoUrl?.toString() ?: ""),
            "createdAt" to System.currentTimeMillis()
        )

        userDoc.get().addOnSuccessListener { document ->
            if (!document.exists()) {
                userDoc.set(userData)
                    .addOnSuccessListener { Log.d("Firebase", "✅ 유저 정보 저장 성공") }
                    .addOnFailureListener { Log.e("Firebase", "❌ 저장 실패: ${it.message}") }
            } else {
                Log.d("Firebase", "ℹ️ 이미 저장된 유저")
            }
        }
    }
}
