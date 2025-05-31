package com.example.summarizer.model.common

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.InputStreamReader

// 회귀 모델의 파라미터를 담는 데이터 클래스
data class ModelParams(val coefficient: Float, val intercept: Float)

/**
 * 단순 선형 회귀 모델을 기반으로 전사 시간 예측을 수행하는 객체
 * - 내부적으로 model_params.json 파일에서 파라미터를 불러와 초기화함
 * - 예: audioLengthSec = 120 → predict() = 160초 (예측값 반환)
 */
object RegressionModel {
    var coefficient: Float = 0f    // 회귀 계수 (기울기)
    var intercept: Float = 0f      // 절편

    /**
     * 앱 시작 시 JSON 파일을 통해 회귀 파라미터를 초기화하는 함수
     * 파일명: assets/model_params.json
     * 예: {"coefficient": 1.2, "intercept": 15.0}
     */
    fun init(context: Context) {
        try {
            context.assets.open("model_params.json").use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val params = Gson().fromJson(reader, ModelParams::class.java)
                coefficient = params.coefficient
                intercept = params.intercept
                Log.d("RegressionModel", "Model loaded: coefficient=$coefficient, intercept=$intercept")
            }
        } catch (e: Exception) {
            Log.e("RegressionModel", "Error loading model: ${e.message}")
        }
    }

    /**
     * 오디오 길이(초)를 받아 예측 전사 시간(초)를 반환
     * 공식: y = coefficient * x + intercept
     */
    fun predict(audioLengthSec: Float): Float {
        return coefficient * audioLengthSec + intercept
    }
}
