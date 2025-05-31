package com.example.summarizer.view.main

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.summarizer.R
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 액션바 숨기기
        supportActionBar?.hide()

        // 스플래쉬 레이아웃 연결 (선택 사항: 로고나 배경 있을 경우)
        setContentView(R.layout.splash_activity)

        // 2초 대기 후 로그인 상태에 따라 화면 전환
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // 자동 로그인 → MainActivity로 이동
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // 로그인 필요 → LoginActivity로 이동
                startActivity(Intent(this, LoginActivity::class.java))
            }

            // 전환 애니메이션
            overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            finish()
        }, 2000)
    }
}
