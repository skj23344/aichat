package com.coder.aichat.data.update

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 更新下载与安装。
 */
object UpdateManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 弹出更新提示对话框 */
    fun showUpdateDialog(context: Context, scope: CoroutineScope, info: UpdateInfo) {
        AlertDialog.Builder(context)
            .setTitle("发现新版本 v${info.version}")
            .setMessage(info.note.ifBlank { "点击更新以获取最新功能与修复。" })
            .setPositiveButton("立即更新") { _, _ -> downloadAndInstall(context, scope, info.apkUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    /** 后台下载 + 进度条 + 完成后跳安装 */
    private fun downloadAndInstall(context: Context, scope: CoroutineScope, url: String) {
        val dialog = ProgressDialog(context).apply {
            setTitle("正在下载更新")
            setMessage("准备下载…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setMax(100)
            setCancelable(false)
            show()
        }
        scope.launch {
            val file = downloadApk(context, url) { pct ->
                mainHandler.post {
                    if (dialog.isShowing) {
                        dialog.progress = pct
                        dialog.setMessage("下载中 $pct%")
                    }
                }
            }
            mainHandler.post { dialog.dismiss() }
            if (file != null) {
                installApk(context, file)
            } else {
                Toast.makeText(context, "下载失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            val total = body.contentLength()

            val dir = context.getExternalFilesDir(null) ?: context.cacheDir
            val file = File(dir, "AiChat-update.apk")
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                    }
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /** 通过 FileProvider 调起系统安装 */
    fun installApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开安装界面：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
