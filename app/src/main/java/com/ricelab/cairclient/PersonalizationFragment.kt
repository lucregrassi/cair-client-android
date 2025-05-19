package com.ricelab.cairclient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.ricelab.cairclient.databinding.FragmentPersonalizationBinding
import com.ricelab.cairclient.libraries.DialogueNuances

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

    private fun loadNuances() {
        val nuances = DialogueNuances.loadFromPrefs(requireContext())

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
        binding.inputPositiveSpeechAct.setText(nuances.values["positive_speech_act"]?.joinToString(", ").orEmpty())
        binding.inputContextualSpeechAct.setText(nuances.values["contextual_speech_act"]?.joinToString(", ").orEmpty())
    }

    private fun saveNuances() {
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
            "tone" to binding.inputTone.text.toString().split(",").map { it.trim() },
            "positive_speech_act" to binding.inputPositiveSpeechAct.text.toString().split(",").map { it.trim() },
            "contextual_speech_act" to binding.inputContextualSpeechAct.text.toString().split(",").map { it.trim() }
        )

        val currentNuances = DialogueNuances.loadFromPrefs(requireContext())
        currentNuances.values = updatedValues
        currentNuances.saveToPrefs(requireContext())

        Toast.makeText(requireContext(), "Nuances salvate correttamente", Toast.LENGTH_SHORT).show()

        (activity as? MainActivity)?.let { main ->
            main.conversationState.dialogueState.dialogueNuances =
                DialogueNuances.loadFromPrefs(requireContext())
        }
    }

    private fun resetToDefault() {
        DialogueNuances.resetToDefaults(requireContext())
        loadNuances()
        Toast.makeText(requireContext(), "Valori predefiniti ripristinati", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}