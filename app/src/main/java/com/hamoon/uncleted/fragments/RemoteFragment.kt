package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.hamoon.uncleted.R
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.OneTimeTokenManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentRemoteBinding
import com.hamoon.uncleted.util.EmailSender
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteFragment : Fragment() {

    private var _binding: FragmentRemoteBinding? = null
    private val binding get() = _binding!!
    private var isRooted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            isRooted = RootChecker.isDeviceRooted()
            binding.layoutRemoteInstallCode.isVisible = isRooted
            loadSettings()
            setupListeners()
        }
    }

    private fun setupListeners() {
        binding.btnSaveRemote.setOnClickListener {
            saveSettings()
            Toast.makeText(requireContext(), "Remote settings saved!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSetEmailCredentials.setOnClickListener { showEmailCredentialsDialog() }
        binding.btnSendTestEmail.setOnClickListener { sendTestEmail() }

        binding.btnGenerateTokens.setOnClickListener {
            generateAndShowEmergencyTokens()
        }

        binding.switchAllowCleartextSms.setOnCheckedChangeListener { _, isChecked ->
            CryptoPreferences.setCleartextSmsAllowed(requireContext(), isChecked)
            binding.layoutSmsMasterPassword.isEnabled = isChecked
        }
    }

    private fun loadSettings() {
        val context = requireContext()
        binding.etEmergencyContact.setText(SecurityPreferences.getEmergencyContact(context))
        binding.etSmsMasterPassword.setText(SecurityPreferences.getSmsMasterPassword(context))

        // Cryptographic Remote Settings
        binding.etTrustedEd25519Pubkey.setText(CryptoPreferences.getTrustedPublicKey(context))
        val cleartextAllowed = CryptoPreferences.isCleartextSmsAllowed(context)
        binding.switchAllowCleartextSms.isChecked = cleartextAllowed
        binding.layoutSmsMasterPassword.isEnabled = cleartextAllowed

        val remainingTokens = OneTimeTokenManager.getRemainingTokenCount(context)
        binding.tvTokenStatus.text = "Active Single-Use Tokens: $remainingTokens"

        if (isRooted) {
            binding.etRemoteInstallCode.setText(SecurityPreferences.getRemoteInstallCode(context))
        }
    }

    private fun saveSettings() {
        val context = requireContext()
        SecurityPreferences.setEmergencyContact(context, binding.etEmergencyContact.text.toString().trim())
        SecurityPreferences.setSmsMasterPassword(context, binding.etSmsMasterPassword.text.toString().trim())

        // Save Ed25519 Public Key
        val pubKey = binding.etTrustedEd25519Pubkey.text?.toString()?.trim()
        CryptoPreferences.setTrustedPublicKey(context, if (pubKey.isNullOrEmpty()) null else pubKey)
        CryptoPreferences.setCleartextSmsAllowed(context, binding.switchAllowCleartextSms.isChecked)

        if (isRooted) {
            SecurityPreferences.setRemoteInstallCode(context, binding.etRemoteInstallCode.text.toString().trim())
        }
    }

    private fun generateAndShowEmergencyTokens() {
        val context = requireContext()
        val tokens = OneTimeTokenManager.generateNewTokenBatch(context)
        val remaining = OneTimeTokenManager.getRemainingTokenCount(context)
        binding.tvTokenStatus.text = "Active Single-Use Tokens: $remaining"

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
            .setPositiveButton("I Copied/Saved Them", null)
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
        lifecycleScope.launch {
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
                    val errorMessage = getString(R.string.email_sending_failed, "Check logs for details.")
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}