package com.ricelab.cairclient.ui.fragments

import com.ricelab.cairclient.R
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ricelab.cairclient.config.AppMode
import com.ricelab.cairclient.config.AppModeResolver
import com.ricelab.cairclient.databinding.FragmentInterventionBinding
import com.ricelab.cairclient.intervention.InterventionManager
import com.ricelab.cairclient.intervention.InterventionType
import com.ricelab.cairclient.intervention.ScheduledIntervention
import com.ricelab.cairclient.intervention.Topic
import java.util.*
import kotlin.math.roundToInt

private const val TAG = "InterventionFragment"

class InterventionFragment : Fragment() {

    private var _binding: FragmentInterventionBinding? = null
    private val binding get() = _binding!!

    private val scheduledInterventions = mutableListOf<ScheduledIntervention>()
    private lateinit var interventionManager: InterventionManager

    private val prefsName = "patient_fields"
    private var currentAppMode: AppMode = AppMode.DEFAULT

    private val apathiaTopicsTriggerMap = mapOf(
        "Cucina" to "Parliamo del cucinare",
        "Musica" to "Parliamo di musica",
        "Guardare la TV" to "Parliamo di cosa mi piace guardare in TV",
        "Giardinaggio" to "Parliamo di giardinaggio",
        "Disegno e schizzi" to "Parliamo del disegno e di fare schizzi",
        "Leggere un libro" to "Parliamo dei libri che mi piace leggere",
        "Attività all'aria aperta" to "Parliamo delle attività che mi piace fare all'aria aperta",
        "Giochi tradizionali" to "Parliamo dei giochi tradizionali che conosco",
        "Attualità" to "Parliamo di notizie e attualità",
        "Arte e cultura" to "Parliamo di arte e cultura",
        "Animali" to "Parliamo degli animali",
        "Cibo" to "Parliamo di cibo",
        "Sport" to "Parliamo di sport",
        "Viaggi" to "Parliamo dei viaggi",
        "Lavorare ai ferri" to "Parliamo del lavorare ai ferri",
        "Attività cognitive" to "Parliamo di attività di stimolazione cognitiva come cruciverba, sudoku e altri giochi per allenare la mente"
    )

    private val deliriumTopicsTriggerMap = mapOf(
        "Musica" to "Parliamo di musica",
        "Cinema" to "Parliamo di film e cinema",
        "Hobby" to "Parliamo delle cose che mi piace fare e dei miei hobby",
        "Cibo" to "Parliamo di cibo",
        "Famiglia" to "Parliamo della mia famiglia"
    )

    private val stazioneMarittimaTopicsTriggerMap = mapOf(
        "Questionario di valutazione del robot" to "Parliamo del riempimento del questionario di valutazione della mia esperienza con il robot"
    )

    private val acquarioTopicsTriggerMap = mapOf(
        "Barriere coralline" to "Parliamo delle scogliere coralline",
        "Protezione delle barriere coralline" to "Parliamo della protezione delle barriere coralline",
        "Polipi corallini" to "Parliamo dei polipi corallini",
        "Coralli molli" to "Parliamo dei coralli molli",
        "Forme dei pesci" to "Parliamo delle forme dei pesci"
    )

    private val actionTriggerMap = mapOf(
        "Richiama l'attenzione" to "Richiama l'attenzione"
    )

    private var topicTriggerMap: Map<String, String> = emptyMap()

    private val predefinedQuestionsMapping = mapOf(
        "salutare alla persona e chiedere come sta" to "greeting the person and ask them how they are feeling",
        "chiedere alla persona se le si può dare del tu" to "asking the person if you can address them informally",
        "chiedere alla persona dove si trova" to "asking the person where they are",
        "chiedere alla persona la data di oggi" to "asking the person which are today's date",
        "chiedere alla persona dove abita" to "asking the person where they live",
        "chiedere alla persona con chi abita" to "asking the person who they live with"
    )

    private fun isDeliriumMode(): Boolean = currentAppMode == AppMode.DELIRIUM

    private fun getTopicTriggerMapForMode(appMode: AppMode): Map<String, String> {
        return when (appMode) {
            AppMode.MARITIME_STATION -> stazioneMarittimaTopicsTriggerMap
            AppMode.DELIRIUM -> deliriumTopicsTriggerMap
            AppMode.APATHY,
            AppMode.DEFAULT,
            AppMode.PARAPLEGIA -> apathiaTopicsTriggerMap
        }
    }

    private fun getCurrentAppMode(): AppMode {
        return try {
            val masterKeyAlias = MasterKey.Builder(requireContext())
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                requireContext(),
                "secure_prefs",
                masterKeyAlias,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val serverPort = sharedPreferences.getInt("server_port", 12345)
            AppModeResolver.fromPort(serverPort)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel recupero della modalità corrente", e)
            AppMode.DEFAULT
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInterventionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        interventionManager = InterventionManager.getInstance(requireContext())

        currentAppMode = getCurrentAppMode()
        topicTriggerMap = getTopicTriggerMapForMode(currentAppMode)

        scheduledInterventions.clear()
        scheduledInterventions.addAll(interventionManager.getAllScheduledInterventions())

        setupVisibilityForMode()
        setupUI()
        updateScheduledInterventionsUI()
        loadPatientFields()
    }

    private fun setupVisibilityForMode() {
        val showPatientFields = isDeliriumMode()

        binding.patientFieldsContainer.visibility =
            if (showPatientFields) View.VISIBLE else View.GONE

        binding.questionsSectionContainer.visibility =
            if (showPatientFields) View.VISIBLE else View.GONE
    }

    private fun loadPatientFields() {
        if (!isDeliriumMode()) return

        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        binding.inputName.setText(prefs.getString("name", ""))
        binding.inputConversationPlace.setText(prefs.getString("conversation_place", ""))
        binding.inputLivingPlace.setText(prefs.getString("living_place", ""))
        binding.inputLivingCompanion.setText(prefs.getString("living_companion", ""))
        Log.d(TAG, "Loaded patient fields from SharedPreferences")
    }

    private fun savePatientFields() {
        if (!isDeliriumMode()) return

        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putString("name", binding.inputName.text.toString().trim())
            putString("conversation_place", binding.inputConversationPlace.text.toString().trim())
            putString("living_place", binding.inputLivingPlace.text.toString().trim())
            putString("living_companion", binding.inputLivingCompanion.text.toString().trim())
            apply()
        }
        Log.d(TAG, "Saved patient fields to SharedPreferences")
    }

    override fun onDestroyView() {
        savePatientFields()
        _binding = null
        super.onDestroyView()
    }

    private fun setupUI() {
        val spinnerItems = listOf(
            "Seleziona un tipo",
            "Intervento ad un orario fisso",
            "Intervento periodico",
            "Intervento immediato"
        )

        binding.spinnerInterventionType.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerItems).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        binding.spinnerInterventionType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateUiForInterventionType(spinnerItems[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        populateQuestions()
        populateTopics()
        populateActions()

        binding.btnAdd.setOnClickListener { confirmIntervention() }

        binding.btnConfirm.setOnClickListener {
            if (scheduledInterventions.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Nessun intervento programmato da confermare",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            interventionManager.setScheduledInterventions(scheduledInterventions)
            interventionManager.saveToPrefs()
            Toast.makeText(
                requireContext(),
                "Interventi programmati salvati correttamente",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Saved ${scheduledInterventions.size} interventions")
        }

        binding.btnClear.setOnClickListener {
            scheduledInterventions.clear()
            interventionManager.clearAll()
            interventionManager.saveToPrefs()
            updateScheduledInterventionsUI()
            Log.d(TAG, "Tutti gli interventi programmati sono stati eliminati")
            Toast.makeText(
                requireContext(),
                "Tutti gli interventi sono stati eliminati",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateUiForInterventionType(type: String) {
        binding.timePickerContainer.visibility =
            if (type.contains("orario")) View.VISIBLE else View.GONE

        binding.periodicContainer.visibility =
            if (type.contains("periodico")) View.VISIBLE else View.GONE
    }

    private fun populateQuestions() {
        binding.containerQuestions.removeAllViews()

        if (!isDeliriumMode()) return

        for ((questionIt, _) in predefinedQuestionsMapping) {
            val checkBox = CheckBox(requireContext()).apply { text = questionIt }
            checkBox.setOnCheckedChangeListener { _, _ ->
                disableIfOtherTypeSelected()
            }
            binding.containerQuestions.addView(checkBox)
        }
    }

    private fun populateTopics() {
        binding.containerTopics.removeAllViews()

        for ((topic, _) in topicTriggerMap) {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val checkBox = CheckBox(requireContext()).apply { text = topic }
            val exclusive = CheckBox(requireContext()).apply {
                text = "Esclusivo"
                isEnabled = false
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                exclusive.isEnabled = isChecked
                disableIfOtherTypeSelected()
            }

            layout.addView(checkBox)
            layout.addView(exclusive)
            binding.containerTopics.addView(layout)
        }
    }

    private fun populateActions() {
        binding.containerActions.removeAllViews()

        for ((action, _) in actionTriggerMap) {
            val checkBox = CheckBox(requireContext()).apply { text = action }
            checkBox.setOnCheckedChangeListener { _, _ ->
                disableIfOtherTypeSelected()
            }
            binding.containerActions.addView(checkBox)
        }
    }

    private fun disableIfOtherTypeSelected() {
        fun isAnyChecked(container: LinearLayout): Boolean {
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i)
                val checkBox =
                    if (view is LinearLayout) view.getChildAt(0) as CheckBox else view as CheckBox
                if (checkBox.isChecked) return true
            }
            return false
        }

        val isQuestionsChecked =
            if (isDeliriumMode()) isAnyChecked(binding.containerQuestions) else false
        val isTopicsChecked = isAnyChecked(binding.containerTopics)
        val isActionsChecked = isAnyChecked(binding.containerActions)

        val selected = mapOf(
            "questions" to isQuestionsChecked,
            "topics" to isTopicsChecked,
            "actions" to isActionsChecked
        )

        val activeTypes = selected.filter { it.value }.keys

        fun setEnabled(container: LinearLayout, enabled: Boolean) {
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i)
                if (view is LinearLayout) {
                    view.getChildAt(0).isEnabled = enabled
                    view.getChildAt(1).isEnabled =
                        enabled && (view.getChildAt(0) as CheckBox).isChecked
                } else {
                    view.isEnabled = enabled
                }
            }
        }

        if (activeTypes.isEmpty()) {
            if (isDeliriumMode()) {
                setEnabled(binding.containerQuestions, true)
            }
            setEnabled(binding.containerTopics, true)
            setEnabled(binding.containerActions, true)
        } else {
            if (isDeliriumMode()) {
                setEnabled(binding.containerQuestions, activeTypes.contains("questions"))
            }
            setEnabled(binding.containerTopics, activeTypes.contains("topics"))
            setEnabled(binding.containerActions, activeTypes.contains("actions"))
        }
    }

    private fun confirmIntervention() {
        val contextual = if (isDeliriumMode()) {
            val name = binding.inputName.text.toString().trim()
            val place = binding.inputConversationPlace.text.toString().trim()
            val living = binding.inputLivingPlace.text.toString().trim()
            val companion = binding.inputLivingCompanion.text.toString().trim()

            if (name.isBlank() || place.isBlank() || living.isBlank() || companion.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Tutti i campi del paziente sono obbligatori",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            mapOf(
                "name" to name,
                "conversation_place" to place,
                "living_place" to living,
                "living_companion" to companion
            )
        } else {
            emptyMap()
        }

        val selectedTypePosition = binding.spinnerInterventionType.selectedItemPosition
        if (selectedTypePosition == 0) {
            Toast.makeText(
                requireContext(),
                "Seleziona un tipo di intervento",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val typeEnum = when (selectedTypePosition) {
            1 -> InterventionType.FIXED
            2 -> InterventionType.PERIODIC
            3 -> InterventionType.IMMEDIATE
            else -> return
        }

        val selectedQuestions = mutableListOf<String>()
        if (isDeliriumMode()) {
            for (i in 0 until binding.containerQuestions.childCount) {
                val checkBox = binding.containerQuestions.getChildAt(i) as CheckBox
                if (checkBox.isChecked) {
                    selectedQuestions.add(
                        predefinedQuestionsMapping[checkBox.text.toString()]!!
                    )
                }
            }

            if (selectedQuestions.isNotEmpty()) {
                selectedQuestions.add("telling the user that the question session has ended")
            }
        }

        val selectedTopics = mutableListOf<Topic>()
        for (i in 0 until binding.containerTopics.childCount) {
            val row = binding.containerTopics.getChildAt(i) as LinearLayout
            val check = row.getChildAt(0) as CheckBox
            val excl = row.getChildAt(1) as CheckBox

            if (check.isChecked) {
                selectedTopics.add(
                    Topic(
                        topicTriggerMap[check.text.toString()]!!,
                        excl.isChecked
                    )
                )
            }
        }

        val selectedActions = mutableListOf<String>()
        for (i in 0 until binding.containerActions.childCount) {
            val checkBox = binding.containerActions.getChildAt(i) as CheckBox
            if (checkBox.isChecked) {
                selectedActions.add(actionTriggerMap[checkBox.text.toString()]!!)
            }
        }

        if (selectedQuestions.isEmpty() && selectedTopics.isEmpty() && selectedActions.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Seleziona almeno una domanda predefinita, un argomento di conversazione o un'azione per continuare",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val timestamp: Double = when (typeEnum) {
            InterventionType.FIXED -> {
                val hour = binding.timePicker.hour
                val minute = binding.timePicker.minute
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                calendar.timeInMillis / 1000.0
            }

            InterventionType.PERIODIC ->
                System.currentTimeMillis() / 1000.0 +
                        (binding.offsetInput.text.toString().toIntOrNull() ?: 0) * 60

            InterventionType.IMMEDIATE ->
                System.currentTimeMillis() / 1000.0
        }

        val period: Long = when (typeEnum) {
            InterventionType.PERIODIC ->
                (binding.periodInput.text.toString().toIntOrNull() ?: 1) * 60L

            InterventionType.FIXED -> 86400L
            InterventionType.IMMEDIATE -> 0L
        }

        val intervention = ScheduledIntervention(
            type = typeEnum.name.lowercase(),
            timestamp = timestamp,
            period = period,
            offset = if (typeEnum == InterventionType.PERIODIC) {
                (binding.offsetInput.text.toString().toIntOrNull() ?: 0) * 60L
            } else {
                0L
            },
            topics = selectedTopics,
            actions = selectedActions,
            interactionSequence = selectedQuestions,
            contextualData = if (selectedQuestions.isNotEmpty()) contextual else emptyMap()
        )

        scheduledInterventions.add(intervention)
        updateScheduledInterventionsUI()

        Log.d(TAG, "Added new intervention: $intervention")
        Toast.makeText(
            requireContext(),
            "Intervento aggiunto correttamente",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateScheduledInterventionsUI() {
        binding.containerScheduled.removeAllViews()

        scheduledInterventions.forEachIndexed { index, intervention ->
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val textView = TextView(requireContext())
            val formattedTime = Date((intervention.timestamp * 1000).toLong()).toString()
            val topics = intervention.topics?.joinToString {
                it.sentence + if (it.exclusive) " (esclusivo)" else ""
            }
            val actions = intervention.actions?.joinToString()
            val sequence = intervention.interactionSequence?.joinToString()
            val context = intervention.contextualData?.entries?.joinToString {
                "${it.key}: ${it.value}"
            }

            val details = """
                Intervento ${index + 1}:
                • Tipo: ${intervention.typeEnum?.name?.lowercase() ?: "unknown"}
                • Timestamp: $formattedTime
                • Periodo (min): ${(intervention.period.toDouble() / 60).roundToInt()}
                • Offset (min): ${(intervention.offset.toDouble() / 60).roundToInt()}
                • Topic: $topics
                • Azioni: $actions
                • Sequenza interazione: $sequence
                • Contesto: $context
            """.trimIndent()

            textView.text = details

            val deleteButton = Button(requireContext()).apply {
                text = "Elimina"
                setOnClickListener {
                    scheduledInterventions.removeAt(index)
                    interventionManager.setScheduledInterventions(scheduledInterventions)
                    interventionManager.saveToPrefs()
                    updateScheduledInterventionsUI()
                    Toast.makeText(
                        requireContext(),
                        "Intervento eliminato",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            container.addView(textView)
            container.addView(deleteButton)
            binding.containerScheduled.addView(container)
        }

        Log.d(TAG, "Scheduled interventions: ${scheduledInterventions.size}")
    }
}