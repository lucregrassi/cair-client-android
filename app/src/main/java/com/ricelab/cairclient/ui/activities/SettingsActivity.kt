package com.ricelab.cairclient.ui.activities

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ricelab.cairclient.R
import com.ricelab.cairclient.storage.FileStorageManager
import com.ricelab.cairclient.ui.adapters.AmbientPointsAdapter
import com.ricelab.cairclient.ui.model.MoveStepUi
import java.io.IOException
import kotlin.math.roundToLong
import com.ricelab.cairclient.config.AppModeResolver
import com.ricelab.cairclient.ui.model.AppModeOption

private const val TAG = "SettingsActivity"

class SettingsActivity : AppCompatActivity() {

    // ui elements
    private lateinit var serverIpSpinner: Spinner
    private lateinit var serverPortSpinner: Spinner
    private lateinit var appModeOptions: List<AppModeOption>
    private lateinit var experimentIdEditText: EditText
    private lateinit var deviceIdEditText: EditText
    private lateinit var personNameEditText: EditText
    private lateinit var personGenderSpinner: Spinner
    private lateinit var personAgeEditText: EditText
    private lateinit var openAIApiKeyEditText: EditText
    private lateinit var azureSpeechKeyEditText: EditText
    private lateinit var fillerSentenceSwitch: SwitchCompat
    private lateinit var autoDetectLanguageSwitch: SwitchCompat
    private lateinit var formalLanguageSwitch: SwitchCompat
    private lateinit var useLedsSwitch: SwitchCompat
    private lateinit var robotPasswordEditText: EditText
    private lateinit var robotPasswordGroup: View
    private lateinit var voiceSpeedSeekBar: SeekBar
    private lateinit var voiceSpeedLabel: TextView
    private lateinit var voicePitchSeekBar: SeekBar
    private lateinit var voicePitchLabel: TextView
    private lateinit var userFontSizeSeekBar: SeekBar
    private lateinit var userFontSizeLabel: TextView
    private lateinit var robotFontSizeSeekBar: SeekBar
    private lateinit var robotFontSizeLabel: TextView
    private lateinit var silenceDurationSeekBar: SeekBar
    private lateinit var silenceDurationLabel: TextView
    private lateinit var micAutoOffSwitch: SwitchCompat
    private lateinit var micAutoOffSeekBar: SeekBar
    private lateinit var micAutoOffLabel: TextView
    private lateinit var micAutoOffGroup: View
    private lateinit var proceedButton: Button

    private val serverIpList = mutableListOf<String>()

    // movement UI
    private lateinit var ambientMoveSwitch: SwitchCompat
    private lateinit var ambientMoveGroup: View
    private lateinit var addAmbientPointBtn: Button
    private lateinit var ambientPointsRecycler: RecyclerView
    private lateinit var ambientAdapter: AmbientPointsAdapter
    private var ambientPoints: MutableList<MoveStepUi> = mutableListOf()

    private lateinit var autoScreenLockSwitch: SwitchCompat

    private fun refreshRobotPasswordVisibility() {
        robotPasswordGroup.visibility = if (useLedsSwitch.isChecked) View.VISIBLE else View.GONE
    }

    private fun refreshMicAutoOffVisibility() {
        val on = micAutoOffSwitch.isChecked
        micAutoOffGroup.visibility = if (on) View.VISIBLE else View.GONE
        micAutoOffSeekBar.isEnabled = on
    }

    private fun refreshAmbientMoveVisibility() {
        val on = ambientMoveSwitch.isChecked
        ambientMoveGroup.visibility = if (on) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // --- findViewById ---
        serverIpSpinner = findViewById(R.id.serverIpSpinner)
        serverPortSpinner = findViewById(R.id.serverPortSpinner)
        experimentIdEditText = findViewById(R.id.experimentIdEditText)
        deviceIdEditText = findViewById(R.id.deviceIdEditText)
        personNameEditText = findViewById(R.id.personNameEditText)

        personGenderSpinner = findViewById(R.id.personGenderSpinner)
        val genderOptions = listOf("Femmina", "Maschio", "Non binario")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        personGenderSpinner.adapter = genderAdapter

        appModeOptions = AppModeResolver.selectableModes().map { mode ->
            AppModeOption(
                mode = mode,
                label = "${AppModeResolver.displayName(mode)} (${AppModeResolver.toPort(mode)})"
            )
        }

        val appModeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            appModeOptions
        )
        appModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serverPortSpinner.adapter = appModeAdapter

        personAgeEditText = findViewById(R.id.personAgeEditText)
        openAIApiKeyEditText = findViewById(R.id.openAIApiKeyEditText)
        azureSpeechKeyEditText = findViewById(R.id.azureSpeechKeyEditText)
        fillerSentenceSwitch = findViewById(R.id.fillerSentenceSwitch)
        autoDetectLanguageSwitch = findViewById(R.id.autoDetectLanguageSwitch)
        formalLanguageSwitch = findViewById(R.id.formalLanguageSwitch)
        useLedsSwitch = findViewById(R.id.useLedsSwitch)

        robotPasswordGroup = findViewById(R.id.robotPasswordGroup)
        robotPasswordEditText = findViewById(R.id.robotPasswordEditText)

        voiceSpeedSeekBar = findViewById(R.id.voiceSpeedSeekBar)
        voiceSpeedLabel = findViewById(R.id.voiceSpeedLabel)
        voicePitchSeekBar = findViewById(R.id.voicePitchSeekBar)
        voicePitchLabel = findViewById(R.id.voicePitchLabel)

        userFontSizeSeekBar = findViewById(R.id.userFontSizeSeekBar)
        userFontSizeLabel = findViewById(R.id.userFontSizeLabel)
        robotFontSizeSeekBar = findViewById(R.id.robotFontSizeSeekBar)
        robotFontSizeLabel = findViewById(R.id.robotFontSizeLabel)
        autoScreenLockSwitch = findViewById(R.id.autoScreenLockSwitch)

        silenceDurationSeekBar = findViewById(R.id.silenceDurationSeekBar)
        silenceDurationLabel = findViewById(R.id.silenceDurationLabel)

        micAutoOffGroup = findViewById(R.id.micAutoOffGroup)
        micAutoOffSwitch = findViewById(R.id.micAutoOffSwitch)
        micAutoOffSeekBar = findViewById(R.id.micAutoOffSeekBar)
        micAutoOffLabel = findViewById(R.id.micAutoOffLabel)

        ambientMoveSwitch = findViewById(R.id.ambientMoveSwitch)
        ambientMoveGroup = findViewById(R.id.ambientMoveGroup)
        addAmbientPointBtn = findViewById(R.id.addAmbientPointBtn)
        ambientPointsRecycler = findViewById(R.id.ambientPointsRecycler)

        proceedButton = findViewById(R.id.proceedButton)

        // RecyclerView + adapter (NOTA: adapter nel package com.ricelab.cairclient)
        ambientAdapter = AmbientPointsAdapter(
            ambientPoints,
            onEdit = { pos -> showAddEditDialog(pos) },
            onDelete = { pos ->
                ambientPoints.removeAt(pos)
                ambientAdapter.notifyItemRemoved(pos)
                if (pos < ambientPoints.size) {
                    ambientAdapter.notifyItemRangeChanged(pos, ambientPoints.size - pos)
                }
            }
        )
        ambientPointsRecycler.layoutManager = LinearLayoutManager(this)
        ambientPointsRecycler.adapter = ambientAdapter

        addAmbientPointBtn.setOnClickListener { showAddEditDialog(-1) }

        // Seekbar listeners
        voiceSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                voiceSpeedLabel.text = "Velocità voce: ${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        voicePitchSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                voicePitchLabel.text = "Tonalità voce: ${p.coerceAtLeast(50)}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        userFontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                userFontSizeLabel.text = "Dimensione testo utente: ${progress}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        robotFontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                robotFontSizeLabel.text = "Dimensione testo robot: ${progress}sp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        silenceDurationSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                silenceDurationLabel.text = "Durata silenzio (s): $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        micAutoOffSwitch.setOnCheckedChangeListener { _, _ -> refreshMicAutoOffVisibility() }
        ambientMoveSwitch.setOnCheckedChangeListener { _, _ -> refreshAmbientMoveVisibility() }

        val fromMenu = intent.getBooleanExtra("fromMenu", false)
        val deleteAllButton: Button = findViewById(R.id.deleteAllButton)
        deleteAllButton.setOnClickListener { showDeleteConfirmationDialog() }

        // Load server IPs from certificates
        loadServerIpsFromCertificates()

        // Read saved values (and if not from menu and minimal config exists -> start MainActivity)
        if (loadSavedValues(fromMenu)) return

        useLedsSwitch.setOnCheckedChangeListener { _, isChecked ->
            refreshRobotPasswordVisibility()
            if (isChecked && robotPasswordEditText.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Per usare i LED inserisci la password del robot.", Toast.LENGTH_SHORT).show()
                robotPasswordEditText.requestFocus()
            }
        }

        proceedButton.setOnClickListener {
            val sharedPreferences = securePrefs()
            val oldServerPort = sharedPreferences.getInt("server_port", -1)

            val ambientEnabled = ambientMoveSwitch.isChecked
            val validAmbientPoints = ambientPoints.filter { it.isValid() }
            if (ambientEnabled && validAmbientPoints.isEmpty()) {
                Toast.makeText(
                    this,
                    "Hai abilitato il movimento ma non hai inserito punti validi. Aggiungi almeno un punto con tempo.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (serverIpSpinner.adapter == null || serverIpSpinner.adapter.count == 0) {
                Toast.makeText(this, "Nessun server disponibile. Aggiungi certificati o riprova.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val serverIp = (serverIpSpinner.selectedItem as? String) ?: run {
                Toast.makeText(this, "Seleziona un server.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedOption = serverPortSpinner.selectedItem as? AppModeOption ?: run {
                Toast.makeText(this, "Seleziona una modalità.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMode = selectedOption.mode
            val serverPort = AppModeResolver.toPort(selectedMode)

            val experimentId = experimentIdEditText.text.toString().trim()
            val deviceId = deviceIdEditText.text.toString().trim()
            val personName = personNameEditText.text.toString().trim()

            val selectedGenderLabel = personGenderSpinner.selectedItem.toString()
            val personGender = when (selectedGenderLabel) {
                "Femmina" -> "f"
                "Maschio" -> "m"
                "Non binario" -> "nb"
                else -> ""
            }

            val personAge = personAgeEditText.text.toString().trim()
            val openAIApiKey = openAIApiKeyEditText.text.toString().trim()
            val azureSpeechKey = azureSpeechKeyEditText.text.toString().trim()
            val useFillerSentence = fillerSentenceSwitch.isChecked
            val autoDetectLanguage = autoDetectLanguageSwitch.isChecked
            val useFormalLanguage = formalLanguageSwitch.isChecked
            val useLeds = useLedsSwitch.isChecked
            val robotPassword = robotPasswordEditText.text.toString().trim()
            val micAutoOffEnabled = micAutoOffSwitch.isChecked
            val micAutoOffMinutes = micAutoOffSeekBar.progress.coerceAtLeast(1)
            val voicePitch = voicePitchSeekBar.progress.coerceAtLeast(50)
            val autoScreenLockEnabled = autoScreenLockSwitch.isChecked

            if (openAIApiKey.isEmpty()) {
                Toast.makeText(this, "Per favore, inserisci la chiave OpenAI", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ageInt = personAge.toIntOrNull()
            if (personAge.isNotEmpty() && (ageInt == null || ageInt <= 0)) {
                Toast.makeText(this, "Età non valida.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (useLeds && robotPassword.isEmpty()) {
                Toast.makeText(this, "Inserisci la password del robot per usare i LED.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val oldMode = if (oldServerPort != -1) AppModeResolver.fromPort(oldServerPort) else null
            val modeChanged = oldMode != null && oldMode != selectedMode

            if (modeChanged) {
                clearConversationStateOnly()
                Log.i(TAG, "App mode changed: $oldMode -> $selectedMode, conversation state reset")
            }

            val gson = Gson()
            val ambientJson = gson.toJson(ambientPoints)

            saveValues(
                serverIp = serverIp,
                serverPort = serverPort,
                experimentId = experimentId,
                deviceId = deviceId,
                personName = personName,
                personGender = personGender,
                personAge = personAge,
                openAIApiKey = openAIApiKey,
                azureSpeechKey = azureSpeechKey,
                useFillerSentence = useFillerSentence,
                autoDetectLanguage = autoDetectLanguage,
                useFormalLanguage = useFormalLanguage,
                useLeds = useLeds,
                robotPassword = robotPassword,
                voiceSpeed = voiceSpeedSeekBar.progress,
                voicePitch = voicePitch,
                userFontSize = userFontSizeSeekBar.progress,
                robotFontSize = robotFontSizeSeekBar.progress,
                silenceDuration = silenceDurationSeekBar.progress,
                autoScreenLockEnabled = autoScreenLockEnabled,
                micAutoOffEnabled = micAutoOffEnabled,
                micAutoOffMinutes = micAutoOffMinutes,
                ambientMoveEnabled = ambientMoveSwitch.isChecked,
                ambientMoveStepsJson = ambientJson
            )

            if (!fromMenu) {
                startActivity(Intent(this, MainActivity::class.java))
            }
            finish()
        }
    }

    private fun showAddEditDialog(position: Int = -1) {
        val isEdit = position >= 0
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_move_step, null)

        val xEt: EditText = dialogView.findViewById(R.id.xEdit)
        val yEt: EditText = dialogView.findViewById(R.id.yEdit)
        val thetaEt: EditText = dialogView.findViewById(R.id.thetaEdit)
        val dwellEt: EditText = dialogView.findViewById(R.id.dwellEdit)
        val mustSwitch: SwitchCompat = dialogView.findViewById(R.id.mustReachSwitch)

        // NEW: timeout UI (visible only when mustReach ON)
        val timeoutGroup: View? = dialogView.findViewById(R.id.mustReachTimeoutGroup)
        val timeoutEt: EditText? = dialogView.findViewById(R.id.mustReachTimeoutEdit)

        fun refreshTimeoutVisibility() {
            val on = mustSwitch.isChecked
            if (timeoutGroup != null) {
                timeoutGroup.visibility = if (on) View.VISIBLE else View.GONE
            }
            if (!on) {
                timeoutEt?.setText("")
            }
        }

        if (isEdit) {
            val s = ambientPoints[position]
            xEt.setText(s.x.toString())
            yEt.setText(s.y.toString())
            thetaEt.setText(s.thetaDeg.toString())
            val minutes = s.dwellMs / 60_000.0
            // show using comma as decimal separator (your existing UX)
            dwellEt.setText(minutes.toString())
            mustSwitch.isChecked = s.mustReach
            // populate timeout if present
            timeoutEt?.setText(s.mustReachTimeoutSec?.toString() ?: "")
            refreshTimeoutVisibility()
        } else {
            mustSwitch.isChecked = false
            refreshTimeoutVisibility()
        }

        mustSwitch.setOnCheckedChangeListener { _, _ -> refreshTimeoutVisibility() }

        val b = AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Modifica punto" else "Aggiungi punto")
            .setView(dialogView)
            .setPositiveButton("OK", null)
            .setNegativeButton("Annulla", null)
            .create()

        b.setOnShowListener {
            val ok = b.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.setOnClickListener {
                val x = xEt.text.toString().toDoubleOrNull() ?: 0.0
                val y = yEt.text.toString().toDoubleOrNull() ?: 0.0
                val theta = thetaEt.text.toString().toDoubleOrNull() ?: 0.0
                val raw = dwellEt.text.toString().trim().replace(',', '.')
                val dwellMinutes = raw.toDoubleOrNull() ?: 0.0
                val dwell = (dwellMinutes * 60_000.0).roundToLong()
                val mustReach = mustSwitch.isChecked

                // parse timeout only if mustReach
                val timeoutSecLong: Long? = if (mustReach) {
                    val rawTimeout = timeoutEt?.text?.toString()?.trim()?.replace(',', '.') ?: ""
                    val timeoutDouble = rawTimeout.toDoubleOrNull()
                    if (timeoutDouble == null) {
                        Toast.makeText(this, "Inserisci un timeout valido (secondi) per i punti obbligatori.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val secs = (timeoutDouble * 1.0).roundToLong()
                    if (secs <= 0L) {
                        Toast.makeText(this, "Il timeout deve essere > 0 secondi.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    secs
                } else {
                    null
                }

                if (dwell <= 0L) {
                    Toast.makeText(this, "Il tempo (minuti) deve essere > 0", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (x == 0.0 && y == 0.0 && theta == 0.0) {
                    Toast.makeText(this, "Presa posizione inutile (0,0,0) — inserisci almeno un valore diverso da zero.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newStep = MoveStepUi(
                    x = x,
                    y = y,
                    thetaDeg = theta,
                    dwellMs = dwell,
                    mustReach = mustReach,
                    mustReachTimeoutSec = timeoutSecLong
                )

                if (isEdit) {
                    ambientPoints[position] = newStep
                    ambientAdapter.notifyItemChanged(position)
                } else {
                    ambientPoints.add(newStep)
                    ambientAdapter.notifyItemInserted(ambientPoints.size - 1)
                }
                b.dismiss()
            }
        }
        b.show()
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Conferma cancellazione")
            .setMessage("Sei sicuro di voler cancellare tutti i dati?")
            .setPositiveButton("Sì") { dialog, _ -> dialog.dismiss(); deleteAllData() }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_home -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_WELCOME, true)
                )
                true
            }
            R.id.action_personalization -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.OPEN_PERSONALIZATION)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_LISTENING, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_WELCOME, true)
                )
                true
            }
            R.id.action_intervention -> {
                safelyNavigateTo(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_FRAGMENT, MainActivity.OPEN_INTERVENTION)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_LISTENING, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_SUPPRESS_WELCOME, true)
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun safelyNavigateTo(intent: Intent) {
        closeOptionsMenu()
        window?.decorView?.post {
            startActivity(intent)
            finish()
        }
    }

    private fun loadSavedValues(fromMenu: Boolean): Boolean {
        val sharedPreferences = securePrefs()

        val savedServerIp = sharedPreferences.getString("server_ip", null)
        val savedServerPort = sharedPreferences.getInt("server_port", -1)
        val savedExperimentId = sharedPreferences.getString("experiment_id", "")
        val savedDeviceId = sharedPreferences.getString("device_id", "")
        val savedPersonName = sharedPreferences.getString("person_name", "")
        val savedPersonGender = sharedPreferences.getString("person_gender", "")
        val savedPersonAge = sharedPreferences.getString("person_age", "")
        val savedOpenAIApiKey = sharedPreferences.getString("openai_api_key", null)
        val savedAzureSpeechKey = sharedPreferences.getString("azure_speech_key", null)
        val useFillerSentence = sharedPreferences.getBoolean("use_filler_sentence", true)
        val autoDetectLanguage = sharedPreferences.getBoolean("auto_detect_language", false)
        val useFormalLanguage = sharedPreferences.getBoolean("use_formal_language", true)
        val ambientMoveEnabled = sharedPreferences.getBoolean("ambient_move_enabled", false)

        // ambient steps JSON read
        val ambientJson = sharedPreferences.getString("ambient_move_steps_json", null)
        if (!ambientJson.isNullOrBlank()) {
            try {
                val t = object : TypeToken<List<MoveStepUi>>() {}.type
                val loaded = Gson().fromJson<List<MoveStepUi>>(ambientJson, t) ?: emptyList()
                ambientPoints.clear()
                ambientPoints.addAll(loaded)
                ambientAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e(TAG, "ambient json parse error", e)
            }
        }

        ambientMoveSwitch.isChecked = ambientMoveEnabled
        refreshAmbientMoveVisibility()

        // auto-start main if minimal config exists
        if (!fromMenu && !savedServerIp.isNullOrEmpty() && !savedOpenAIApiKey.isNullOrEmpty() && savedServerPort != -1) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return true
        }

        savedServerIp?.let { setSelectedServerIp(it) }
        if (savedServerPort != -1) {
            val savedMode = AppModeResolver.fromPort(savedServerPort)
            val index = appModeOptions.indexOfFirst { it.mode == savedMode }
            if (index != -1) {
                serverPortSpinner.setSelection(index)
            }
        }

        savedExperimentId?.let { experimentIdEditText.setText(it) }
        savedDeviceId?.let { deviceIdEditText.setText(it) }
        savedPersonName?.let { personNameEditText.setText(it) }

        savedPersonGender?.let {
            val index = when (it) {
                "f" -> 0
                "m" -> 1
                "nb" -> 2
                else -> -1
            }
            if (index != -1) personGenderSpinner.setSelection(index)
        }
        savedPersonAge?.let { personAgeEditText.setText(it) }
        savedOpenAIApiKey?.let { openAIApiKeyEditText.setText(it) }
        savedAzureSpeechKey?.let { azureSpeechKeyEditText.setText(it) }

        fillerSentenceSwitch.isChecked = useFillerSentence
        autoDetectLanguageSwitch.isChecked = autoDetectLanguage
        formalLanguageSwitch.isChecked = useFormalLanguage

        val useLeds = sharedPreferences.getBoolean("use_leds", false)
        val savedRobotPassword = sharedPreferences.getString("robot_password", "") ?: ""
        useLedsSwitch.isChecked = useLeds
        robotPasswordEditText.setText(savedRobotPassword)
        refreshRobotPasswordVisibility()

        val savedVoiceSpeed = sharedPreferences.getInt("voice_speed", 100)
        voiceSpeedSeekBar.progress = savedVoiceSpeed
        voiceSpeedLabel.text = "Velocità voce: ${savedVoiceSpeed}%"

        val savedVoicePitch = sharedPreferences.getInt("voice_pitch", 100)
        voicePitchSeekBar.progress = savedVoicePitch
        voicePitchLabel.text = "Tonalità voce: ${savedVoicePitch}%"

        val savedSilenceDuration = sharedPreferences.getInt("silence_duration", 2)
        silenceDurationSeekBar.progress = savedSilenceDuration
        silenceDurationLabel.text = "Durata silenzio (s): $savedSilenceDuration"

        val savedUserFontSize = sharedPreferences.getInt("user_font_size", 24)
        userFontSizeSeekBar.progress = savedUserFontSize
        userFontSizeLabel.text = "Dimensione testo utente: ${savedUserFontSize}sp"

        val savedRobotFontSize = sharedPreferences.getInt("robot_font_size", 24)
        robotFontSizeSeekBar.progress = savedRobotFontSize
        robotFontSizeLabel.text = "Dimensione testo robot: ${savedRobotFontSize}sp"

        val savedAutoScreenLockEnabled =
            sharedPreferences.getBoolean("auto_screen_lock_enabled", false)
        autoScreenLockSwitch.isChecked = savedAutoScreenLockEnabled

        val savedMicAutoOffEnabled = sharedPreferences.getBoolean("mic_auto_off_enabled", true)
        val savedMicAutoOffMinutes = sharedPreferences.getInt("mic_auto_off_minutes", 1)
        micAutoOffSwitch.isChecked = savedMicAutoOffEnabled
        micAutoOffSeekBar.progress = savedMicAutoOffMinutes
        micAutoOffLabel.text = "Timer auto-spegnimento microfono (min): $savedMicAutoOffMinutes"
        refreshMicAutoOffVisibility()

        return false
    }

    private fun setSelectedServerIp(ip: String) {
        val adapter = serverIpSpinner.adapter
        if (adapter != null) {
            val index = serverIpList.indexOf(ip)
            if (index != -1) serverIpSpinner.setSelection(index)
        }
    }

    private fun loadServerIpsFromCertificates() {
        try {
            val assetManager: AssetManager = assets
            val certificateFiles = assetManager.list("certificates") ?: emptyArray()
            for (filename in certificateFiles) {
                if (filename.startsWith("server_") && filename.endsWith(".crt")) {
                    val ip = filename.removePrefix("server_")
                        .removeSuffix(".crt")
                        .replace("_", ".")
                    serverIpList.add(ip)
                }
            }
            if (serverIpList.isEmpty()) {
                Toast.makeText(this, "No server certificates found.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "No server certificates found in assets/certificates/")
            } else {
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverIpList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                serverIpSpinner.adapter = adapter
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error loading server IPs: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error loading server IPs", e)
        }
    }

    private fun saveValues(
        serverIp: String,
        serverPort: Int,
        experimentId: String,
        deviceId: String,
        personName: String,
        personGender: String,
        personAge: String,
        openAIApiKey: String,
        azureSpeechKey: String,
        useFillerSentence: Boolean,
        autoDetectLanguage: Boolean,
        useFormalLanguage: Boolean,
        useLeds: Boolean,
        robotPassword: String,
        voiceSpeed: Int,
        voicePitch: Int,
        userFontSize: Int,
        robotFontSize: Int,
        silenceDuration: Int,
        autoScreenLockEnabled: Boolean,
        micAutoOffEnabled: Boolean,
        micAutoOffMinutes: Int,
        ambientMoveEnabled: Boolean,
        ambientMoveStepsJson: String
    ) {
        val sharedPreferences = securePrefs()
        val pwdToStore = if (useLeds) robotPassword else ""
        sharedPreferences.edit {
            putString("server_ip", serverIp)
            putInt("server_port", serverPort)
            putString("experiment_id", experimentId)
            putString("device_id", deviceId)
            putString("person_name", personName)
            putString("person_gender", personGender)
            putString("person_age", personAge)
            putString("openai_api_key", openAIApiKey)
            putString("azure_speech_key", azureSpeechKey)
            putBoolean("use_filler_sentence", useFillerSentence)
            putBoolean("auto_detect_language", autoDetectLanguage)
            putBoolean("use_formal_language", useFormalLanguage)
            putBoolean("use_leds", useLeds)
            putString("robot_password", pwdToStore)
            putInt("voice_speed", voiceSpeed)
            putInt("voice_pitch", voicePitch)
            putInt("user_font_size", userFontSize)
            putInt("robot_font_size", robotFontSize)
            putInt("silence_duration", silenceDuration)
            putBoolean("auto_screen_lock_enabled", autoScreenLockEnabled)
            putBoolean("mic_auto_off_enabled", micAutoOffEnabled)
            putInt("mic_auto_off_minutes", micAutoOffMinutes)
            putBoolean("ambient_move_enabled", ambientMoveEnabled)
            putString("ambient_move_steps_json", ambientMoveStepsJson)
        }
    }

    private fun securePrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                this,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secure prefs", e)
            getSharedPreferences("fallback_prefs", MODE_PRIVATE)
        }
    }

    private fun clearConversationStateOnly() {
        Log.i(TAG, "clearConversationStateOnly() with filesDir=$filesDir")
        val fileStorageManager = FileStorageManager(filesDir)

        fileStorageManager.dialogueStateFile?.delete()
        fileStorageManager.speakersInfoFile?.delete()
        fileStorageManager.dialogueStatisticsFile?.delete()
    }

    private fun deleteAllData() {
        Log.i(TAG, "deleteAllData() with filesDir=$filesDir")
        val fileStorageManager = FileStorageManager(filesDir)

        fileStorageManager.dialogueStateFile?.delete()
        fileStorageManager.speakersInfoFile?.delete()
        fileStorageManager.dialogueStatisticsFile?.delete()

        val sharedPreferences = securePrefs()
        sharedPreferences.edit { clear() }

        Toast.makeText(this, "Tutti i dati sono stati cancellati.", Toast.LENGTH_LONG).show()
    }
}