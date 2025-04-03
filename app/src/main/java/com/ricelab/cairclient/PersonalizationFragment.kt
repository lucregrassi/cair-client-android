package com.ricelab.cairclient

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.ricelab.cairclient.databinding.FragmentPersonalizationBinding
import java.util.*

private const val TAG = "PersonalizationFragment"

class PersonalizationFragment : Fragment() {

    private var _binding: FragmentPersonalizationBinding? = null
    private val binding get() = _binding!!

    private val scheduledInterventions = mutableListOf<ScheduledIntervention>()
    private lateinit var personalizationManager: PersonalizationManager

    private val topicTriggerMap = mapOf(
        "Famiglia" to "Parliamo della mia famiglia",
        "Cibo" to "Parliamo di cibo"
    )

    private val actionTriggerMap = mapOf(
        "Richiama l'attenzione" to "Richiama l'attenzione"
    )

    private val predefinedQuestionsMapping = mapOf(
        "salutare alla persona e chiedere come sta" to "greeting the person and ask how they are",
        "chiedere alla persona se le si può dare del tu" to "asking the person if you can address them informally",
        "chiedere alla persona dove si trova" to "asking the person where they are",
        "chiedere alla persona la data e l'ora di oggi" to "asking the person today's date and time",
        "chiedere alla persona dove abita" to "asking the person where they live",
        "chiedere alla persona con chi abita" to "asking the person who they live with"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Proper OnBackPressedCallback object
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Pop this fragment from the back stack
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        personalizationManager = PersonalizationManager(requireContext())
        personalizationManager.loadFromPrefs()
        scheduledInterventions.clear()
        scheduledInterventions.addAll(personalizationManager.getAllScheduledInterventions())
        setupUI()
        updateScheduledInterventionsUI()
    }

    private fun setupUI() {
        val spinnerItems = listOf(
            "Seleziona un tipo",
            "Intervento ad un orario fisso",
            "Intervento periodico",
            "Intervento immediato"
        )
        binding.spinnerInterventionType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerItems)

        binding.spinnerInterventionType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateUiForInterventionType(spinnerItems[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        populateQuestions()
        populateTopics()
        populateActions()

        binding.btnConfirm.setOnClickListener { confirmIntervention() }
        binding.btnSend.setOnClickListener {
            personalizationManager.setScheduledInterventions(scheduledInterventions)
            personalizationManager.saveToPrefs()
            Log.d(TAG, "Saved ${scheduledInterventions.size} interventions")
        }
        binding.btnClear.setOnClickListener {
            scheduledInterventions.clear()
            personalizationManager.clearAll()
            personalizationManager.saveToPrefs()
            updateScheduledInterventionsUI()
            Log.d(TAG, "Cleared all interventions")
        }
    }

    private fun updateUiForInterventionType(type: String) {
        binding.timePickerContainer.visibility = if (type.contains("orario")) View.VISIBLE else View.GONE
        binding.periodicContainer.visibility = if (type.contains("periodico")) View.VISIBLE else View.GONE
    }

    private fun populateQuestions() {
        binding.containerQuestions.removeAllViews()
        for ((questionIt, _) in predefinedQuestionsMapping) {
            val checkBox = CheckBox(requireContext()).apply { text = questionIt }
            checkBox.setOnCheckedChangeListener { _, _ ->
                disableIfOtherTypeSelected("questions")
            }
            binding.containerQuestions.addView(checkBox)
        }
    }

    private fun populateTopics() {
        binding.containerTopics.removeAllViews()
        for ((topic, _) in topicTriggerMap) {
            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val checkBox = CheckBox(requireContext()).apply { text = topic }
            val exclusive = CheckBox(requireContext()).apply { text = "Esclusivo"; isEnabled = false }
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                exclusive.isEnabled = isChecked
                disableIfOtherTypeSelected("topics")
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
                disableIfOtherTypeSelected("actions")
            }
            binding.containerActions.addView(checkBox)
        }
    }

    private fun disableIfOtherTypeSelected(selectedType: String) {
        fun isAnyChecked(container: LinearLayout): Boolean {
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i)
                val checkBox = if (view is LinearLayout) view.getChildAt(0) as CheckBox else view as CheckBox
                if (checkBox.isChecked) return true
            }
            return false
        }

        val selected = mapOf(
            "topics" to isAnyChecked(binding.containerTopics),
            "questions" to isAnyChecked(binding.containerQuestions),
            "actions" to isAnyChecked(binding.containerActions)
        )

        val shouldDisable = selected.filterKeys { it != selectedType }.any { it.value }

        fun setEnabled(container: LinearLayout, enabled: Boolean) {
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i)
                if (view is LinearLayout) {
                    view.getChildAt(0).isEnabled = enabled
                    view.getChildAt(1).isEnabled = enabled && (view.getChildAt(0) as CheckBox).isChecked
                } else {
                    view.isEnabled = enabled
                }
            }
        }

        setEnabled(binding.containerQuestions, selectedType == "questions" || !shouldDisable)
        setEnabled(binding.containerTopics, selectedType == "topics" || !shouldDisable)
        setEnabled(binding.containerActions, selectedType == "actions" || !shouldDisable)
    }

    private fun confirmIntervention() {
        val name = binding.inputName.text.toString().trim()
        val place = binding.inputConversationPlace.text.toString().trim()
        val living = binding.inputLivingPlace.text.toString().trim()
        val companion = binding.inputLivingCompanion.text.toString().trim()

        if (name.isBlank() || place.isBlank() || living.isBlank() || companion.isBlank()) {
            Toast.makeText(requireContext(), "Tutti i campi del paziente sono obbligatori", Toast.LENGTH_SHORT).show()
            return
        }

        val contextual = mapOf(
            "name" to name,
            "conversation_place" to place,
            "living_place" to living,
            "living_companion" to companion
        )

        val typeText = binding.spinnerInterventionType.selectedItem.toString()
        val typeEnum = when {
            typeText.contains("fisso", true) -> InterventionType.FIXED
            typeText.contains("periodico", true) -> InterventionType.PERIODIC
            typeText.contains("immediato", true) -> InterventionType.IMMEDIATE
            else -> return
        }

        val selectedQuestions = mutableListOf<String>()
        for (i in 0 until binding.containerQuestions.childCount) {
            val checkBox = binding.containerQuestions.getChildAt(i) as CheckBox
            if (checkBox.isChecked) selectedQuestions.add(predefinedQuestionsMapping[checkBox.text.toString()]!!)
        }

        if (selectedQuestions.isNotEmpty()) {
            selectedQuestions.add(0, "start the programmed intervention")
            selectedQuestions.add("telling the user that the programmed intervention has ended")
        }

        val selectedTopics = mutableListOf<Topic>()
        for (i in 0 until binding.containerTopics.childCount) {
            val row = binding.containerTopics.getChildAt(i) as LinearLayout
            val check = row.getChildAt(0) as CheckBox
            val excl = row.getChildAt(1) as CheckBox
            if (check.isChecked) selectedTopics.add(Topic(topicTriggerMap[check.text.toString()]!!, excl.isChecked))
        }

        val selectedActions = mutableListOf<String>()
        for (i in 0 until binding.containerActions.childCount) {
            val checkBox = binding.containerActions.getChildAt(i) as CheckBox
            if (checkBox.isChecked) selectedActions.add(actionTriggerMap[checkBox.text.toString()]!!)
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
            InterventionType.PERIODIC -> System.currentTimeMillis() / 1000.0 + (binding.offsetInput.text.toString().toIntOrNull() ?: 0) * 60
            InterventionType.IMMEDIATE -> System.currentTimeMillis() / 1000.0
        }

        val period: Long = when (typeEnum) {
            InterventionType.PERIODIC -> (binding.periodInput.text.toString().toIntOrNull() ?: 1) * 60L
            InterventionType.FIXED -> 86400L
            else -> 0L
        }

        val intervention = ScheduledIntervention(
            type = typeEnum.name.lowercase(),
            timestamp = timestamp,
            period = period,
            offset = if (typeEnum == InterventionType.PERIODIC) (binding.offsetInput.text.toString().toIntOrNull() ?: 0) * 60L else 0,
            topics = selectedTopics,
            actions = selectedActions,
            interactionSequence = selectedQuestions,
            contextualData = if (selectedQuestions.isNotEmpty()) contextual else emptyMap()
        )

        scheduledInterventions.add(intervention)
        updateScheduledInterventionsUI()
        Log.d(TAG, "Added new intervention: $intervention")
    }

    private fun updateScheduledInterventionsUI() {
        binding.containerScheduled.removeAllViews()
        scheduledInterventions.forEachIndexed { index, intervention ->
            val text = TextView(requireContext())
            text.text = "Intervento ${index + 1}: ${intervention.typeEnum?.name?.lowercase() ?: "unknown"}"
            binding.containerScheduled.addView(text)
        }
        Log.d(TAG, "Scheduled interventions: ${scheduledInterventions.size}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
