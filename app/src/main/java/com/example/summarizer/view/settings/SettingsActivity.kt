package com.example.summarizer.view.settings

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.summarizer.R

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 레이아웃 먼저 설정해야 findViewById 가능
        setContentView(R.layout.activity_settings)

        // << 돌아가기 버튼 처리
        val backText = findViewById<TextView>(R.id.backText)
        backText.setOnClickListener {
            finish()
        }

        // 프래그먼트 삽입
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }
}