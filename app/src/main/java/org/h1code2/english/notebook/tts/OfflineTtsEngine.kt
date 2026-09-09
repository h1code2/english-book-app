package org.h1code2.english.notebook.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.Executors

/**
 * 内置离线语音引擎（sherpa-onnx + vits-piper-en_US-lessac-medium）。
 *
 * 模型随 APK 分发（assets/tts/），完全离线；系统 TTS 不可用时兜底。
 * 合成在单线程后台执行，避免阻塞主线程；音频经 AudioTrack 播放。
 */
object OfflineTtsEngine {

    private const val TAG = "OfflineTtsEngine"
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var initFailed = false

    val available: Boolean
        get() = !initFailed

    /** 惰性初始化；失败返回 null 并标记，避免反复尝试拖慢 UI */
    private fun obtain(context: Context): OfflineTts? {
        tts?.let { return it }
        if (initFailed) return null
        return synchronized(this) {
            tts ?: run {
                try {
                    // espeak-ng-data 在 native 层按文件系统读取，必须先拷到私有目录
                    // （模型 onnx / tokens 可直接走 AssetManager，无需拷贝）
                    val dataDirPath = copyEspeakDataDir(context)
                    val vits = OfflineTtsVitsModelConfig(
                        model = "tts/en_US-lessac-medium.onnx",
                        tokens = "tts/tokens.txt",
                        dataDir = dataDirPath
                    )
                    val config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(vits = vits, numThreads = 2)
                    )
                    OfflineTts(context.assets, config).also { tts = it }
                } catch (e: Exception) {
                    Log.e(TAG, "离线引擎初始化失败", e)
                    initFailed = true
                    null
                }
            }
        }
    }

    /**
     * 把 assets/tts/espeak-ng-data 递归拷到 filesDir/tts/espeak-ng-data，
     * 返回可供 native fopen 的绝对路径。已存在则跳过。
     */
    private fun copyEspeakDataDir(context: Context): String {
        val dstRoot = java.io.File(context.filesDir, "tts/espeak-ng-data")
        if (!dstRoot.exists()) {
            copyAssetDir(context, "tts/espeak-ng-data", dstRoot)
        }
        return dstRoot.absolutePath
    }

    private fun copyAssetDir(context: Context, assetPath: String, dst: java.io.File) {
        dst.mkdirs()
        for (name in context.assets.list(assetPath) ?: emptyArray()) {
            val childAsset = "$assetPath/$name"
            val childDst = java.io.File(dst, name)
            val children = context.assets.list(childAsset)
            if (children.isNullOrEmpty()) {
                context.assets.open(childAsset).use { input ->
                    childDst.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDir(context, childAsset, childDst)
            }
        }
    }

    /**
     * 合成并播放。异步执行；返回结果经 [onDone] 回调（主线程外）。
     * @return false 表示引擎不可用或合成失败
     */
    fun speak(context: Context, text: String, speed: Float = 1.0f, onDone: ((Boolean) -> Unit)? = null) {
        executor.execute {
            val engine = obtain(context.applicationContext)
            if (engine == null) {
                onDone?.invoke(false)
                return@execute
            }
            try {
                val audio = engine.generate(text, sid = 0, speed = speed)
                play(audio)
                Log.d(TAG, "synthesized '${text.take(30)}' samples=${audio.samples.size} rate=${audio.sampleRate}")
                onDone?.invoke(true)
            } catch (e: Exception) {
                Log.e(TAG, "合成失败", e)
                onDone?.invoke(false)
            }
        }
    }

    /** 停止当前播放（快速点击时避免叠音） */
    @Volatile
    private var track: AudioTrack? = null

    fun stop() {
        track?.let {
            try {
                it.pause()
                it.flush()
                it.release()
            } catch (_: Exception) {
            }
        }
        track = null
    }

    private fun play(audio: GeneratedAudio) {
        val pcm = toPcm16(audio.samples)
        val minBuf = android.media.AudioTrack.getMinBufferSize(
            audio.sampleRate,
            android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, pcm.size))
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .build()
        track = t
        t.write(pcm, 0, pcm.size)
        t.play()
    }

    private fun toPcm16(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, f ->
            val v = (f.coerceIn(-1f, 1f) * 32767).toInt().toShort()
            bytes[i * 2] = (v.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
