package com.example.summarizer.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 오디오 파일 관련 유틸리티 함수 모음
 */
object FileUtils {

    /**
     * 주어진 URI를 실제 임시 파일로 변환하여 반환
     *
     * @param context 현재 컨텍스트
     * @param uri 선택된 오디오 파일의 URI
     * @return 임시 저장된 File 객체 (temp_audio.mp3 이름으로 캐시에 저장됨)
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val file = File(context.cacheDir, "temp_audio.mp3") // 캐시 디렉토리 내에 생성
        val outputStream = FileOutputStream(file)

        inputStream?.copyTo(outputStream) // InputStream → OutputStream 복사
        inputStream?.close()
        outputStream.close()

        return file
    }

    /**
     * URI로부터 실제 파일명을 추출
     *
     * @param context 현재 컨텍스트
     * @param uri 파일의 URI
     * @return URI에서 파악된 파일명 (없을 경우 "unknown.mp3")
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result = "unknown.mp3"
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) {
                result = it.getString(nameIndex)
            }
        }
        return result
    }
}
