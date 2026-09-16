package com.hamoon.uncleted.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.hamoon.uncleted.R
import com.hamoon.uncleted.canary.CovertCanarySender
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.OneTimeTokenManager
import com.hamoon.uncleted.crypto.PostQuantumEngine
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentRemoteSignalingBinding
import com.hamoon.uncleted.util.EmailSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteSignalingFragment : Fragment() {

    private var _binding: FragmentRemoteSignalingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoteSignalingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        val context = requireContext()

        // 1. OHTTP Canary Settings
        binding.switchOhttpCanary.isChecked = SecurityPreferences.isOhttpCanaryEnabled(context)
        binding.etOhttpRelayUrl.setText(SecurityPreferences.getOhttpRelayUrl(context))
        binding.etOhttpGatewayPubkey.setText(SecurityPreferences.getOhttpGatewayPublicKey(context) ?: "")
        setupMasqueradeDropdown(SecurityPreferences.getOhttpMasqueradeProfile(context))

        // 2. Ed25519 Cryptographic Wire Settings
        binding.etTrustedEd25519Pubkey.setText(CryptoPreferences.getTrustedPublicKey(context) ?: "")
        binding.etWireDriftWindowMs.setText(CryptoPreferences.getWireDriftWindowMs(context).toString())
        binding.tvLastWireSequence.text = "Highest Verified Sequence: ${CryptoPreferences.getLastRecordedSequence(context)}"

        // 3. OTC Tokens Status
        updateTokenCountDisplay()

        // 4. Permissive SMS & SMTP Settings
        val cleartextAllowed = CryptoPreferences.isCleartextSmsAllowed(context)
        binding.switchAllowCleartextSms.isChecked = cleartextAllowed
        binding.layoutSmsMasterPassword.isEnabled = cleartextAllowed
        binding.etEmergencyContact.setText(SecurityPreferences.getEmergencyContact(context) ?: "")
        binding.etSmsMasterPassword.setText(SecurityPreferences.getSmsMasterPassword(context) ?: "")
    }

    private fun setupMasqueradeDropdown(currentProfile: String) {
        val profiles = listOf("google_play_telemetry", "firebase_analytics")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, profiles)
        binding.autoOhttpMasqueradeProfile.setAdapter(adapter)

        val idx = profiles.indexOf(currentProfile).takeIf { it != -1 } ?: 0
        binding.autoOhttpMasqueradeProfile.setText(profiles[idx], false)
    }

    private fun updateTokenCountDisplay() {
        val remaining = OneTimeTokenManager.getRemainingTokenCount(requireContext())
        binding.tvOtcTokenStatus.text = "Active Single-Use Tokens in DE Store: $remaining"
    }

    private fun setupListeners() {
        val context = requireContext()

        // OHTTP Canary Toggle
        binding.switchOhttpCanary.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setOhttpCanaryEnabled(context, isChecked)
        }

        // OHTTP Test Probe
        binding.btnOhttpTestProbe.setOnClickListener {
            saveSettings()
            Toast.makeText(context, "Dispatching test OHTTP canary probe...", Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val (success, message) = CovertCanarySender.dispatchTestProbe(context)
                withContext(Dispatchers.Main) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(if (success) "Probe Successful" else "Probe Failed")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }

        // Generate OTC Tokens
        binding.btnGenerateOtcTokens.setOnClickListener {
            val tokens = OneTimeTokenManager.generateNewTokenBatch(context)
            updateTokenCountDisplay()
            showTokenWalletSheetDialog(tokens)
        }

        // Burn All OTC Tokens
        binding.btnBurnAllTokens.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Burn All Emergency Tokens?")
                .setMessage("All active single-use emergency recovery slips will be wiped from Device-Protected storage.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Burn All") { _, _ ->
                    OneTimeTokenManager.clearAllTokens(context)
                    updateTokenCountDisplay()
                    Toast.makeText(context, "All emergency tokens burned.", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Cleartext SMS Toggle
        binding.switchAllowCleartextSms.setOnCheckedChangeListener { _, isChecked ->
            CryptoPreferences.setCleartextSmsAllowed(context, isChecked)
            binding.layoutSmsMasterPassword.isEnabled = isChecked
        }

        // SMTP Credentials
        binding.btnSetEmailCredentials.setOnClickListener {
            showEmailCredentialsDialog()
        }

        // Test Email
        binding.btnSendTestEmail.setOnClickListener {
            sendTestEmail()
        }

        // Save All Settings
        binding.btnSaveRemoteSignaling.setOnClickListener {
            saveSettings()
            Toast.makeText(context, "Remote signaling parameters saved & armed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        val context = requireContext()

        // OHTTP
        val relayUrl = binding.etOhttpRelayUrl.text?.toString()?.trim().orEmpty()
        val gatewayKey = binding.etOhttpGatewayPubkey.text?.toString()?.trim()
        val profile = binding.autoOhttpMasqueradeProfile.text?.toString()?.trim().orEmpty()

        if (relayUrl.isNotEmpty()) SecurityPreferences.setOhttpRelayUrl(context, relayUrl)
        SecurityPreferences.setOhttpGatewayPublicKey(context, if (gatewayKey.isNullOrEmpty()) null else gatewayKey)
        if (profile.isNotEmpty()) SecurityPreferences.setOhttpMasqueradeProfile(context, profile)

        // Ed25519
        val ed25519Key = binding.etTrustedEd25519Pubkey.text?.toString()?.trim()
        val driftMs = binding.etWireDriftWindowMs.text?.toString()?.toLongOrNull() ?: 120000L
        CryptoPreferences.setTrustedPublicKey(context, if (ed25519Key.isNullOrEmpty()) null else ed25519Key)
        CryptoPreferences.setWireDriftWindowMs(context, driftMs)

        // SMS Fallback
        val contact = binding.etEmergencyContact.text?.toString()?.trim().orEmpty()
        val masterPassword = binding.etSmsMasterPassword.text?.toString()?.trim().orEmpty()
        SecurityPreferences.setEmergencyContact(context, contact)
        SecurityPreferences.setSmsMasterPassword(context, masterPassword)
    }

    private fun showTokenWalletSheetDialog(tokens: List<String>) {
        val context = requireContext()
        val sheetContent = StringBuilder()
            .append("UNCLE TED EMERGENCY WALLET SHEET\n")
            .append("Keep these single-use wipe tokens in your wallet/passport.\n")
            .append("Texting any of these lines to this phone will destroy all keys instantly:\n\n")

        tokens.forEach { token ->
            sheetContent.append("• ").append(token).append("\n")
        }

        val textView = TextView(context).apply {
            text = sheetContent.toString()
            setPadding(48, 24, 48, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Emergency One-Time Codes")
            .setView(textView)
            .setPositiveButton("Copy All") { _, _ ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UncleTed_OTC_Sheet", sheetContent.toString()))
                Toast.makeText(context, "Wallet sheet copied to clipboard.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showEmailCredentialsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_email_credentials, null)
        val etHost = dialogView.findViewById<TextInputEditText>(R.id.et_email_host_dialog)
        val etPort = dialogView.findViewById<TextInputEditText>(R.id.et_email_port_dialog)
        val etUsername = dialogView.findViewById<TextInputEditText>(R.id.et_email_username_dialog)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_email_password_dialog)
        val switchSslTls = dialogView.findViewById<SwitchMaterial>(R.id.switch_enable_ssl_tls_dialog)

        val currentConfig = EmailSender.getEmailConfig(requireContext())
        etHost.setText(currentConfig?.host)
        etPort.setText(currentConfig?.port?.toString())
        etUsername.setText(currentConfig?.username)
        etPassword.setText(currentConfig?.password)
        switchSslTls.isChecked = currentConfig?.enableSslTls ?: true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.set_email_credentials_title)
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val host = etHost.text.toString().trim()
                val port = etPort.text.toString().trim().toIntOrNull()
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()
                val enableSslTls = switchSslTls.isChecked

                if (host.isNotEmpty() && port != null && username.isNotEmpty() && password.isNotEmpty()) {
                    val config = EmailSender.EmailConfig(host, port, username, password, enableSslTls)
                    EmailSender.setEmailConfig(requireContext(), config)
                    Toast.makeText(requireContext(), "Email credentials saved.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.email_credentials_missing, Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTestEmail() {
        val recipient = SecurityPreferences.getEmergencyContact(requireContext())
        if (recipient.isNullOrEmpty() || !recipient.contains("@")) {
            Toast.makeText(requireContext(), "Set a valid email in 'Emergency Contact' first.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(requireContext(), "Sending test email...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val success = EmailSender.sendEmail(
                requireContext(),
                recipient,
                "Uncle Ted Test Email",
                "This is an authenticated test email from your Uncle Ted defense suite."
            )
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.email_sending_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.email_sending_failed, "Check logs for details."), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}