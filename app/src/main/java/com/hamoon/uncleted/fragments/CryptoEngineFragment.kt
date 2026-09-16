package com.hamoon.uncleted.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.R
import com.hamoon.uncleted.crypto.AntiRollbackManager
import com.hamoon.uncleted.crypto.CryptoPreferences
import com.hamoon.uncleted.crypto.PostQuantumEngine
import com.hamoon.uncleted.crypto.StrongBoxSecurityManager
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentCryptoEngineBinding
import com.hamoon.uncleted.util.MemoryHardeningEngine
import com.hamoon.uncleted.util.NativeSecurityBridge
import com.hamoon.uncleted.vault.PlausibleDeniabilityVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CryptoEngineFragment : Fragment() {

    private var _binding: FragmentCryptoEngineBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCryptoEngineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshHardwareStatus()
    }

    private fun loadSettings() {
        val context = requireContext()

        // 1. Hardware Status
        refreshHardwareStatus()

        // 2. Post-Quantum Cryptography Hybrid Setup
        binding.switchPqcEnabled.isChecked = CryptoPreferences.isPqcHybridEnabled(context)
        val localPub = CryptoPreferences.getLocalPqcPublicKey(context)
        if (localPub != null) {
            binding.etLocalPqcPubkey.setText(localPub.encodeToBase64())
        } else {
            binding.etLocalPqcPubkey.setText("")
        }
        binding.etTrustedRemotePqcPubkey.setText(CryptoPreferences.getTrustedPqcPublicKey(context) ?: "")

        // 3. Plausible Deniability Vault Setup
        binding.etVaultCarrierName.setText(SecurityPreferences.getVaultCarrierFileName(context))
        binding.etVaultSecretLabel.setText(SecurityPreferences.getVaultSecretLabel(context))

        // 4. Memory Hardening & ZRAM Setup (Matching XML camelCase ID)
        binding.switchZramScrubbing.isChecked = SecurityPreferences.isZramScrubbingEnabled(context)
        binding.switchZramRekeying.isChecked = SecurityPreferences.isZramReKeyingEnabled(context)
    }

    private fun refreshHardwareStatus() {
        val context = requireContext()
        val hasStrongBox = StrongBoxSecurityManager.isStrongBoxSupported(context)
        val monotonicCounter = CryptoPreferences.getHardwareMonotonicCounter(context)
        val isSuicide = CryptoPreferences.isSuicideExecuted(context)

        binding.tvKeystoreHardwareStatus.text = if (hasStrongBox) {
            "HSM Provider: Titan M2 / StrongBox KeyMint (Discrete Silicon)"
        } else {
            "HSM Provider: Primary SoC TEE Fallback"
        }

        binding.tvKeystoreMonotonicCounter.text = "Hardware Monotonic Sequence: $monotonicCounter"

        if (isSuicide) {
            binding.tvKeystoreSuicideStatus.text = "Suicide Key Register: REVOKED (PERMANENT SUICIDE)"
            binding.tvKeystoreSuicideStatus.setTextColor(ContextCompat.getColor(context, R.color.status_red))
        } else {
            binding.tvKeystoreSuicideStatus.text = "Suicide Key Register: Armed & Sealed"
            binding.tvKeystoreSuicideStatus.setTextColor(ContextCompat.getColor(context, R.color.status_green))
        }
    }

    private fun setupListeners() {
        val context = requireContext()

        // PQC Toggle
        binding.switchPqcEnabled.setOnCheckedChangeListener { _, isChecked ->
            CryptoPreferences.setPqcHybridEnabled(context, isChecked)
        }

        // Generate Local Hybrid Keypair
        binding.btnGeneratePqcKeypair.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val keyPair = PostQuantumEngine.generateHybridKeyPair()
                CryptoPreferences.saveLocalPqcKeyPair(context, keyPair)
                AntiRollbackManager.registerSecurityEventAdvance(context)

                withContext(Dispatchers.Main) {
                    binding.etLocalPqcPubkey.setText(keyPair.public.encodeToBase64())
                    refreshHardwareStatus()
                    Toast.makeText(context, "New ML-KEM-768/X25519 keypair sealed into DE storage.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Copy Public Key to Clipboard
        binding.btnCopyPqcPubkey.setOnClickListener {
            val pubKeyStr = binding.etLocalPqcPubkey.text?.toString()?.trim()
            if (!pubKeyStr.isNullOrEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("UncleTed_PQC_Hybrid_PublicKey", pubKeyStr))
                Toast.makeText(context, "PQC Public Key copied to clipboard.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No local public key generated yet.", Toast.LENGTH_SHORT).show()
            }
        }

        // Save Remote Peer Hybrid Public Key
        binding.btnSavePqcRemote.setOnClickListener {
            val remoteKeyStr = binding.etTrustedRemotePqcPubkey.text?.toString()?.trim()
            if (remoteKeyStr.isNullOrEmpty()) {
                CryptoPreferences.setTrustedPqcPublicKey(context, null)
                Toast.makeText(context, "Remote peer public key cleared.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val decoded = PostQuantumEngine.HybridPublicKey.decodeFromBase64(remoteKeyStr)
            if (decoded != null) {
                CryptoPreferences.setTrustedPqcPublicKey(context, remoteKeyStr)
                Toast.makeText(context, "Remote hybrid public key verified & armed.", Toast.LENGTH_SHORT).show()
            } else {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Invalid Hybrid Public Key")
                    .setMessage("The provided Base64 string could not be parsed into a valid ML-KEM-768 + X25519 hybrid envelope. Please check the key format.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        // Vault: Store Blob in DNG
        binding.btnStoreVaultBlob.setOnClickListener {
            val carrierName = binding.etVaultCarrierName.text?.toString()?.trim().orEmpty()
            val label = binding.etVaultSecretLabel.text?.toString()?.trim().orEmpty()
            val content = binding.etVaultSecretContent.text?.toString().orEmpty()

            if (carrierName.isEmpty() || label.isEmpty() || content.isEmpty()) {
                Toast.makeText(context, "Filename, label, and secret content cannot be empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SecurityPreferences.setVaultCarrierFileName(context, carrierName)
            SecurityPreferences.setVaultSecretLabel(context, label)

            val secretBytes = content.toByteArray(Charsets.UTF_8)

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val success = PlausibleDeniabilityVault.storeSecretBlob(context, label, secretBytes)
                NativeSecurityBridge.zeroByteArray(secretBytes)

                withContext(Dispatchers.Main) {
                    if (success) {
                        binding.etVaultSecretContent.setText("")
                        Toast.makeText(context, "Secret successfully shaped into DNG container: Pictures/Camera/$carrierName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed storing secret into DNG container. Check logcat.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Vault: Extract Blob from DNG
        binding.btnExtractVaultBlob.setOnClickListener {
            val carrierName = binding.etVaultCarrierName.text?.toString()?.trim().orEmpty()
            val label = binding.etVaultSecretLabel.text?.toString()?.trim().orEmpty()

            if (carrierName.isEmpty() || label.isEmpty()) {
                Toast.makeText(context, "Filename and label must be specified.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            SecurityPreferences.setVaultCarrierFileName(context, carrierName)
            SecurityPreferences.setVaultSecretLabel(context, label)

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val extractedBytes = PlausibleDeniabilityVault.extractSecretBlob(context, label)

                withContext(Dispatchers.Main) {
                    if (extractedBytes != null) {
                        val recoveredText = String(extractedBytes, Charsets.UTF_8)
                        NativeSecurityBridge.zeroByteArray(extractedBytes)
                        binding.etVaultSecretContent.setText(recoveredText)
                        Toast.makeText(context, "Secret successfully extracted & verified.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed extracting secret: file missing, label mismatch, or tag verification failure.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Vault: Purge Carrier
        binding.btnPurgeVault.setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle("Purge Deniability Container")
                .setMessage("Are you sure? The carrier DNG image file will be overwritten with pseudorandom junk and unlinked from internal storage.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Purge File") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        PlausibleDeniabilityVault.purgeVaultContainer(context)
                        withContext(Dispatchers.Main) {
                            binding.etVaultSecretContent.setText("")
                            Toast.makeText(context, "Vault carrier permanently purged.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }

        // Memory Hardening Switches & Manual Trigger
        binding.switchZramScrubbing.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setZramScrubbingEnabled(context, isChecked)
        }

        binding.switchZramRekeying.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setZramReKeyingEnabled(context, isChecked)
        }

        binding.btnTriggerMemoryScrub.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val success = MemoryHardeningEngine.executeVolatileScrub(context)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "Kernel caches dropped, memory compacted, and heap sanitized.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Memory scrub completed with non-fatal userspace fallback.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}