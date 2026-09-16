package com.hamoon.uncleted.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hamoon.uncleted.core.DefenseCoordinator
import com.hamoon.uncleted.data.SecurityPreferences
import com.hamoon.uncleted.databinding.FragmentDestructionProtocolsBinding
import com.hamoon.uncleted.util.RootActions
import com.hamoon.uncleted.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DestructionProtocolsFragment : Fragment() {

    private var _binding: FragmentDestructionProtocolsBinding? = null
    private val binding get() = _binding!!
    private var isRooted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDestructionProtocolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSettings()
        setupListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            isRooted = withContext(Dispatchers.IO) { RootChecker.isDeviceRooted() }
            binding.btnExecLevel2.isEnabled = isRooted
            binding.btnExecLevel3.isEnabled = isRooted
            binding.btnExecLevel4.isEnabled = isRooted
        }
    }

    private fun loadSettings() {
        val context = requireContext()
        binding.switchHardwareWipe.isChecked = SecurityPreferences.isHardwareWipeEnabled(context)
        binding.switchWipeDeviceOnCritical.isChecked = SecurityPreferences.isWipeDeviceEnabled(context)
        binding.switchRootSecureWipe.isChecked = SecurityPreferences.isSecureWipeEnabled(context)
    }

    private fun setupListeners() {
        val context = requireContext()

        binding.switchHardwareWipe.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setHardwareWipeEnabled(context, isChecked)
            if (isChecked) {
                Toast.makeText(context, "Hardware Button Wipe armed: Press Vol UP, DOWN, UP, DOWN rapidly.", Toast.LENGTH_LONG).show()
            }
        }

        binding.switchWipeDeviceOnCritical.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setWipeDeviceEnabled(context, isChecked)
        }

        binding.switchRootSecureWipe.setOnCheckedChangeListener { _, isChecked ->
            SecurityPreferences.setSecureWipeEnabled(context, isChecked)
        }

        // Level 1: Standard Factory Reset
        binding.btnExecLevel1.setOnClickListener {
            confirmAndExecute(
                title = "EXECUTE LEVEL 1: FACTORY RESET",
                message = "This will invoke standard Android factory reset via RecoverySystem. All user partitions will be formatted and the device will reboot.\n\nProceed?",
                level = RootActions.WipeLevel.STANDARD_WIPE
            )
        }

        // Level 2: JEDEC Silicon Shred
        binding.btnExecLevel2.setOnClickListener {
            confirmAndExecute(
                title = "EXECUTE LEVEL 2: JEDEC SILICON SHRED",
                message = "This will destroy discrete Titan M2/StrongBox master keys, evict Vold keys, and issue direct JEDEC BLKSECDISCARD IOCTLs to metadata headers.\n\nData is mathematically unrecoverable. Proceed?",
                level = RootActions.WipeLevel.FAST_USERDATA
            )
        }

        // Level 3: OS Suicide (Soft Brick)
        binding.btnExecLevel3.setOnClickListener {
            confirmAndExecute(
                title = "EXECUTE LEVEL 3: OS SUICIDE (SOFT BRICK)",
                message = "This will execute a Level 2 shred and zero all boot/ramdisk partitions (boot, vendor_boot, init_boot). The phone will not boot into Android without complete factory firmware reflashing.\n\nProceed?",
                level = RootActions.WipeLevel.SYSTEM_DESTRUCTION
            )
        }

        // Level 4: Nuclear Winter (Hard Brick)
        binding.btnExecLevel4.setOnClickListener {
            confirmAndExecute(
                title = "⚠️ CRITICAL WARNING: LEVEL 4 NUCLEAR WINTER ⚠️",
                message = "This zeroes partition tables, raw boot blocks, and triggers low-level kernel SysRq hardware panics. HIGH PROBABILITY OF PERMANENT HARDWARE BRICK.\n\nAre you absolutely certain?",
                level = RootActions.WipeLevel.NUCLEAR_WINTER
            )
        }
    }

    private fun confirmAndExecute(title: String, message: String, level: RootActions.WipeLevel) {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("ABORT", null)
            .setPositiveButton("CONFIRM EXECUTION") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val strategy = DefenseCoordinator.resolveStrategy(context)
                    if (strategy.isHardwareSecured && level == RootActions.WipeLevel.STANDARD_WIPE) {
                        strategy.executeWipe("MANUAL_PANIC_${level.name}")
                    } else {
                        RootActions.executeWipeProtocol(context, level)
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}