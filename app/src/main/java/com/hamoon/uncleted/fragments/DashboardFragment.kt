package com.hamoon.uncleted.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.hamoon.uncleted.R
import com.hamoon.uncleted.databinding.FragmentDashboardBinding
import com.hamoon.uncleted.databinding.ItemChecklistBinding
import com.hamoon.uncleted.services.PanicActionService
import com.hamoon.uncleted.util.SecurityScoreCalculator
import com.hamoon.uncleted.util.ThreatDetectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var threatsFoundView: View? = null
    private var noThreatsView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.progressIndicator.isVisible = true
        binding.dashboardContentScrollview.isVisible = false
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            populateDashboard()
        }
    }

    private suspend fun populateDashboard() {
        val context = context ?: return

        val (threatAssessment, securityLevel, checklistItems) = withContext(Dispatchers.IO) {
            val threats = ThreatDetectionEngine.performThreatAnalysis(context)
            val secLevel = SecurityScoreCalculator.calculateSecurityLevel(context)
            val items = SecurityScoreCalculator.getChecklistItems(context)
            Triple(threats, secLevel, items)
        }

        withContext(Dispatchers.Main) {
            if (_binding == null) return@withContext
            binding.progressIndicator.isVisible = false
            binding.dashboardContentScrollview.isVisible = true

            updateSecurityStatus(securityLevel, threatAssessment)
            if (securityLevel.level == 6) {
                updateProgressBar(6, ContextCompat.getColor(context, securityLevel.colorRes))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_alert_triangle_24)
            } else {
                updateProgressBar(securityLevel.level, ContextCompat.getColor(context, securityLevel.colorRes))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_shield_check_24)
            }
            updateSecurityChecklist(context, checklistItems)
            updateThreatAssessmentCard(context, threatAssessment)
        }
    }

    private fun updateSecurityStatus(securityLevel: SecurityScoreCalculator.SecurityLevel, threatAssessment: ThreatDetectionEngine.ThreatAssessment) {
        val context = requireContext()

        val finalTitle = when (threatAssessment.threatLevel) {
            ThreatDetectionEngine.ThreatLevel.CRITICAL -> "CRITICAL THREAT DETECTED"
            ThreatDetectionEngine.ThreatLevel.HIGH -> "HIGH THREAT DETECTED"
            ThreatDetectionEngine.ThreatLevel.MEDIUM -> "THREATS DETECTED"
            else -> getString(securityLevel.titleRes)
        }

        val finalDescription = when (threatAssessment.threatLevel) {
            ThreatDetectionEngine.ThreatLevel.CRITICAL -> "Immediate action required. Review critical threats below."
            ThreatDetectionEngine.ThreatLevel.HIGH -> "High-priority threats detected. Review security status below."
            ThreatDetectionEngine.ThreatLevel.MEDIUM -> "Moderate threats detected. See details below."
            else -> getString(securityLevel.descriptionRes)
        }

        val finalColor = when (threatAssessment.threatLevel) {
            ThreatDetectionEngine.ThreatLevel.CRITICAL, ThreatDetectionEngine.ThreatLevel.HIGH -> ContextCompat.getColor(context, R.color.status_red)
            ThreatDetectionEngine.ThreatLevel.MEDIUM -> ContextCompat.getColor(context, R.color.status_yellow)
            else -> ContextCompat.getColor(context, securityLevel.colorRes)
        }

        binding.tvStatusTitle.text = finalTitle
        binding.tvStatusSubtitle.text = finalDescription
        binding.ivStatusIcon.setColorFilter(finalColor)
        binding.tvStatusTitle.setTextColor(finalColor)
    }

    private fun updateSecurityChecklist(context: Context, checklistItems: List<SecurityScoreCalculator.ChecklistItem>) {
        binding.checklistContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        checklistItems.forEach { item ->
            val checklistItemBinding = ItemChecklistBinding.inflate(inflater, binding.checklistContainer, false)
            checklistItemBinding.ivFeatureIcon.setImageResource(item.iconRes)
            checklistItemBinding.tvFeatureTitle.text = getString(item.titleRes)
            checklistItemBinding.tvFeatureDescription.text = getString(item.descriptionRes)
            if (item.isMet()) {
                if (item.titleRes == R.string.check_root_title) {
                    checklistItemBinding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle_24)
                    checklistItemBinding.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.level_root_god_mode))
                    checklistItemBinding.tvStatusText.text = "Detected"
                    checklistItemBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.level_root_god_mode))
                    checklistItemBinding.ivFeatureIcon.setColorFilter(ContextCompat.getColor(context, R.color.level_root_god_mode))
                } else {
                    checklistItemBinding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle_24)
                    checklistItemBinding.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_green))
                    checklistItemBinding.tvStatusText.text = "Active"
                    checklistItemBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_green))
                }
            } else {
                if (item.titleRes == R.string.check_root_title) {
                    checklistItemBinding.ivStatusIcon.setImageResource(R.drawable.ic_cancel_24)
                    checklistItemBinding.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.md_theme_light_outline))
                    checklistItemBinding.tvStatusText.text = "Not Found"
                    checklistItemBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_outline))
                } else {
                    checklistItemBinding.ivStatusIcon.setImageResource(R.drawable.ic_cancel_24)
                    checklistItemBinding.ivStatusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_red))
                    checklistItemBinding.tvStatusText.text = "Inactive"
                    checklistItemBinding.tvStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_red))
                }
            }
            binding.checklistContainer.addView(checklistItemBinding.root)
        }
    }

    private fun updateThreatAssessmentCard(context: Context, threatAssessment: ThreatDetectionEngine.ThreatAssessment) {
        if (threatAssessment.threats.isNotEmpty()) {
            noThreatsView?.isVisible = false

            if (threatsFoundView == null) {
                threatsFoundView = binding.stubThreatsFound.inflate()
            }
            threatsFoundView?.isVisible = true

            binding.cardThreatAssessment.strokeColor = when (threatAssessment.threatLevel) {
                ThreatDetectionEngine.ThreatLevel.CRITICAL, ThreatDetectionEngine.ThreatLevel.HIGH -> ContextCompat.getColor(context, R.color.status_red)
                else -> ContextCompat.getColor(context, R.color.status_yellow)
            }
            binding.cardThreatAssessment.strokeWidth = 2

            threatsFoundView?.let { view ->
                val tvThreatLevel: TextView = view.findViewById(R.id.tv_threat_level)
                val tvThreatSummary: TextView = view.findViewById(R.id.tv_threat_summary)
                val tvThreatDetails: TextView = view.findViewById(R.id.tv_threat_details)
                val tvThreatRecommendations: TextView = view.findViewById(R.id.tv_threat_recommendations)

                tvThreatLevel.text = "Threat Level: ${threatAssessment.threatLevel.name}"
                tvThreatLevel.setTextColor(when (threatAssessment.threatLevel) {
                    ThreatDetectionEngine.ThreatLevel.CRITICAL, ThreatDetectionEngine.ThreatLevel.HIGH -> ContextCompat.getColor(context, R.color.status_red)
                    else -> ContextCompat.getColor(context, R.color.status_yellow)
                })

                tvThreatSummary.text = getString(
                    R.string.dashboard_threat_summary,
                    threatAssessment.threats.size,
                    (threatAssessment.confidence * 100).toInt()
                )
                val threatsStringBuilder = StringBuilder()
                threatAssessment.threats.forEach { threat ->
                    val severityEmoji = when (threat.severity) {
                        PanicActionService.Severity.HIGH, PanicActionService.Severity.CRITICAL -> "🔴"
                        PanicActionService.Severity.MEDIUM -> "🟡"
                        else -> "⚪"
                    }
                    threatsStringBuilder.append("$severityEmoji ${threat.description}\n")
                }
                tvThreatDetails.text = threatsStringBuilder.toString().trim()

                if (threatAssessment.recommendations.isNotEmpty()) {
                    tvThreatRecommendations.isVisible = true
                    tvThreatRecommendations.text = "\nRecommendations:\n• ${threatAssessment.recommendations.take(3).joinToString("\n• ")}"
                } else {
                    tvThreatRecommendations.isVisible = false
                }
            }
        } else {
            threatsFoundView?.isVisible = false

            if (noThreatsView == null) {
                noThreatsView = binding.stubNoThreats.inflate()
            }
            noThreatsView?.isVisible = true

            val outlineColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, Color.GRAY)
            binding.cardThreatAssessment.strokeColor = outlineColor
            binding.cardThreatAssessment.strokeWidth = 1
        }

        binding.cardThreatAssessment.visibility = View.VISIBLE
    }

    private fun updateProgressBar(level: Int, color: Int) {
        val progressViews = listOf(
            binding.progressBar1, binding.progressBar2, binding.progressBar3,
            binding.progressBar4, binding.progressBar5
        )
        val outlineColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, Color.GRAY)
        if (level == 6) {
            progressViews.forEach { it.setBackgroundColor(color) }
            return
        }
        progressViews.forEachIndexed { index, view ->
            if (index < level) {
                view.setBackgroundColor(color)
            } else {
                view.setBackgroundColor(outlineColor)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        threatsFoundView = null
        noThreatsView = null
        _binding = null
    }
}