package com.ricelab.cairclient.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.ricelab.cairclient.databinding.FragmentPersonalizationBinding
import com.ricelab.cairclient.conversation.DialogueNuances
import com.ricelab.cairclient.ui.activities.MainActivity
import com.ricelab.cairclient.config.AppMode

class PersonalizationFragment : Fragment() {

    private var _binding: FragmentPersonalizationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonalizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().supportFragmentManager.popBackStack()
            }
        })

        loadNuances()

        binding.btnSaveNuances.setOnClickListener { saveNuances() }
        binding.btnResetDefaults.setOnClickListener { resetToDefault() }
    }

    private fun getCurrentAppMode(): AppMode {
        return (activity as? MainActivity)?.getCurrentAppMode() ?: AppMode.DEFAULT
    }

    private fun loadNuances() {
        val activity = activity as? MainActivity
        val appMode = getCurrentAppMode()

        val nuances = if (appMode == AppMode.MARITIME_STATION && activity != null) {
            activity.getCurrentEffectiveDialogueNuances()
        } else {
            DialogueNuances.loadForMode(requireContext(), appMode)
        }

        // diversity
        binding.inputNationality.setText(nuances.values["diversity"]?.getOrNull(0).orEmpty())
        binding.inputMentalCondition.setText(nuances.values["diversity"]?.getOrNull(1).orEmpty())
        binding.inputPhysicalCondition.setText(nuances.values["diversity"]?.getOrNull(2).orEmpty())

        // time
        binding.inputTimeOfDay.setText(nuances.values["time"]?.getOrNull(0).orEmpty())
        binding.inputSeason.setText(nuances.values["time"]?.getOrNull(1).orEmpty())
        binding.inputEvents.setText(nuances.values["time"]?.getOrNull(2).orEmpty())

        // place
        binding.inputEnvironment.setText(nuances.values["place"]?.getOrNull(0).orEmpty())
        binding.inputCity.setText(nuances.values["place"]?.getOrNull(1).orEmpty())
        binding.inputNation.setText(nuances.values["place"]?.getOrNull(2).orEmpty())

        // tone
        binding.inputTone.setText(nuances.values["tone"]?.joinToString(", ").orEmpty())

        // speech acts
        binding.inputPositiveSpeechAct.setText(
            nuances.values["positive_speech_act"]?.joinToString(", ").orEmpty()
        )
        binding.inputContextualSpeechAct.setText(
            nuances.values["contextual_speech_act"]?.joinToString(", ").orEmpty()
        )

        val toneLocked = appMode == AppMode.MARITIME_STATION
        binding.inputTone.isEnabled = !toneLocked
        binding.inputTone.isFocusable = !toneLocked
        binding.inputTone.isFocusableInTouchMode = !toneLocked
        binding.inputTone.isClickable = !toneLocked
    }

    private fun saveNuances() {
        val appMode = getCurrentAppMode()
        val currentNuances = DialogueNuances.loadForMode(requireContext(), appMode)

        val toneValues = if (appMode == AppMode.MARITIME_STATION) {
            currentNuances.values["tone"] ?: List(9) { "neutral" }
        } else {
            binding.inputTone.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        val updatedValues = mapOf(
            "diversity" to listOf(
                binding.inputNationality.text.toString().trim(),
                binding.inputMentalCondition.text.toString().trim(),
                binding.inputPhysicalCondition.text.toString().trim()
            ),
            "time" to listOf(
                binding.inputTimeOfDay.text.toString().trim(),
                binding.inputSeason.text.toString().trim(),
                binding.inputEvents.text.toString().trim()
            ),
            "place" to listOf(
                binding.inputEnvironment.text.toString().trim(),
                binding.inputCity.text.toString().trim(),
                binding.inputNation.text.toString().trim()
            ),
            "tone" to toneValues,
            "positive_speech_act" to binding.inputPositiveSpeechAct.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            "contextual_speech_act" to binding.inputContextualSpeechAct.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        )

        currentNuances.values = updatedValues
        DialogueNuances.saveForMode(requireContext(), appMode, currentNuances)

        Toast.makeText(
            requireContext(),
            "Nuances salvate correttamente per la modalità $appMode",
            Toast.LENGTH_SHORT
        ).show()

        (activity as? MainActivity)?.refreshEffectiveDialogueNuances()
        loadNuances()
    }

    private fun resetToDefault() {
        val appMode = getCurrentAppMode()

        DialogueNuances.resetForMode(requireContext(), appMode)
        loadNuances()

        Toast.makeText(
            requireContext(),
            "Valori predefiniti ripristinati per la modalità $appMode",
            Toast.LENGTH_SHORT
        ).show()

        (activity as? MainActivity)?.refreshEffectiveDialogueNuances()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}