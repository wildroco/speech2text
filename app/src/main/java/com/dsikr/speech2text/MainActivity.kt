package com.dsikr.speech2text

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*


private const val TAG = "AudioRecordTest"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val STATE_READY = "ready"
private const val STATE_RECORDING = "recording"
private const val STATE_RECOGNIZING = "recognizing"
private const val MESSAGE_READY = "녹음 버튼을 눌러 음성 인식을 시작하세요."
private const val MESSAGE_RECORDING = "녹음중..."
private const val MESSAGE_RECOGNIZING = "음성 인식중..."

class MainActivity : AppCompatActivity() {
    //녹음 기능을 사용하기 위한 권한 획득을 위한 변수
    private var permissionToRecordAccepted = false
    private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)


    //ETRI 인공지능 오픈 API 통신을 위한 변수
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://aiopen.etri.re.kr:8000")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val etriService = retrofit.create(EtriService::class.java)
    private val etriApiKey = "a674ff10-4c3e-4aa4-843a-1a61a7e8293b"
    private val languageCode = "korean"
    private val samplingFrequency = 16000
    private val speechMaxLength = samplingFrequency * 45;


    //UI 요소
    private var textViewState: TextView? = null
    private var textViewResult: TextView? = null
    private var actionButton: FloatingActionButton? = null
    private val mainHandler = Handler(Looper.getMainLooper())


    //앱의 동작 상태 관리
    private var state = STATE_READY
    private var isRecording = false
    private var forceStopRecording = false
    private var speechLength = 0
    private var speechData = ByteArray(speechMaxLength * 2)


    //Android 기본 제공 메서드. 녹음 기능 권한 획득의 결과를 처리하는 콜백
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
        if (!permissionToRecordAccepted) finish()
    }


    // Android 기본 제공 메서드. Activity가 생성 될 때 해야 할 일
    override fun onCreate(savedInstanceState: Bundle?) {
        // UI 구성하기
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        textViewState = findViewById(R.id.textview_state)
        textViewState?.text = MESSAGE_READY
        textViewResult = findViewById(R.id.textview_result)
        textViewResult?.text = ""
        actionButton = findViewById(R.id.fab)

        // 녹음 권한 요청
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        // 녹음 버튼 행동 설정
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
            when (state) {
                STATE_READY -> onStartRecording()
                STATE_RECORDING -> onStopRecording()
                STATE_RECOGNIZING -> {
                    Log.d(TAG, "음성 인식 중 버튼 눌림. 이 행동은 막아야 함")
                    Toast.makeText(baseContext, "음성 인식 진행중 입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Android 기본 제공 메서드. 우측 상단의 옵션 메뉴 만들기
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // Android 기본 제공 메서드. 옵션 메뉴의 행동 정의
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                // 공유 메뉴 행동 정의

                if (textViewResult?.text.isNullOrEmpty()) {
                    Toast.makeText(baseContext, "음성 인식 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, textViewResult?.text ?: "")
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 음성 녹음이 시작될 때 해야 하는 일
    private fun onStartRecording() {
        if (isRecording) {
            forceStopRecording = true
        } else {
            state = STATE_RECORDING
            actionButton?.setImageResource(R.drawable.ic_baseline_stop_24)
            textViewResult?.text = ""

            Thread {
                // 레코딩 시작
                Log.d(TAG, "record start")
                recordSpeech()

                // 음성 인식 시작
                Log.d(TAG, "regognize start")
                state = STATE_RECOGNIZING
                mainHandler.post {
                    textViewState?.text = MESSAGE_RECOGNIZING
                }
                var recognizedText = recognizeVoice()
                if (recognizedText.isNullOrEmpty()) {
                    recognizedText = "음성 인식 실패"
                }
                mainHandler.post {
                    textViewResult?.text = recognizedText
                    onReady()
                }
            }.start()
        }
    }

    // 음성 녹음이 중지 될 때 해야 하는 일
    private fun onStopRecording() {
        forceStopRecording = true
    }

    // 음성 인식이 완료되고 준비 상태가 될 때 해야 하는 일
    private fun onReady() {
        state = STATE_READY
        actionButton?.setImageResource(R.drawable.ic_baseline_mic_24)
        textViewState?.text = MESSAGE_READY
    }

    // 음성 녹음하기
    private fun recordSpeech() {
        Log.d(TAG, "recordSpeech)")
        state = STATE_RECORDING
        mainHandler.post {
            textViewState?.text = MESSAGE_RECORDING
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            samplingFrequency,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            samplingFrequency,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        speechLength = 0
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            throw RuntimeException("Error. Failed to initialized audio device. Check the permission.")
        } else {
            val inBuffer = ShortArray(bufferSize)
            forceStopRecording = false
            isRecording = true
            audio.startRecording()
            while (!forceStopRecording) {
                val ret = audio.read(inBuffer, 0, bufferSize)
                for (i in 0 until ret) {
                    if (speechLength >= speechMaxLength) {
                        forceStopRecording = true
                        break
                    }
                    speechData[speechLength * 2] = (inBuffer[i].toInt().and(0x00FF)).toByte()
                    speechData[speechLength * 2 + 1] =
                        (inBuffer[i].toInt().and(0xFF00).shr(8)).toByte()
                    speechLength += 1

                }
            }
            audio.stop()
            audio.release()
            isRecording = false
        }
    }

    // 음성 인식하기.
    private fun recognizeVoice(): String? {
        Log.d(TAG, "recognizeVoice")
        val audioString = Base64.encodeToString(speechData, 0, speechLength * 2, Base64.NO_WRAP)
        val requestBody = VoiceRecognitionRequest(
            request_id = UUID.randomUUID().toString(),
            access_key = etriApiKey,
            argument = VoiceRecognitionRequestArgument(
                language_code = languageCode,
                audio = audioString
            )
        )
        val request = etriService.voiceRecognition(requestBody)
        val response = request.execute()
        Log.d(TAG, "response code: ${response.code()}")
        Log.d(TAG, "response message: ${response.message()}")
        Log.d(TAG, "response body: ${response.body()}")
        Log.d(TAG, "response error body: ${response.errorBody()?.string()}")
        if (response.isSuccessful) {
            val recognizedText = response.body()?.return_object?.recognized ?: ""
            return recognizedText
        } else {
            return null
        }
    }
}