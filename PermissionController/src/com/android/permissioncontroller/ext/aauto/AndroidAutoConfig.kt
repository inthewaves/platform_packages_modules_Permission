package com.android.permissioncontroller.ext.aauto

import android.Manifest
import android.app.compat.gms.AndroidAutoPackageFlag
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.ServiceInfo
import android.ext.PackageId
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.BaseGosPkgStateConfigFragment
import com.android.permissioncontroller.ext.BaseSettingsActivity
import com.android.permissioncontroller.ext.addCategory
import com.android.permissioncontroller.ext.addPref
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment
import getAppInfoOrNull

class AndroidAutoConfigActivity : BaseSettingsActivity() {
    override fun getNavGraphStart() = R.id.android_auto_config
}

class AndroidAutoConfigWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = AndroidAutoConfigFragment()
}

class AndroidAutoConfigFragment : BaseGosPkgStateConfigFragment(
    configuringPkgName = PackageId.ANDROID_AUTO_NAME,
    titleStringRes = R.string.android_auto
) {
    lateinit var aautoSettingsPref: Preference
    lateinit var potentialIssues: PreferenceGroup
    lateinit var aautoVoiceCommandIssues: Preference

    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        aautoSettingsPref = screen.addPref(getText(R.string.aauto_settings)).apply {
            intent = Intent(Intent.ACTION_APPLICATION_PREFERENCES).apply {
                `package` = configuringPkgName
            }
        }

        screen.addCategory(R.string.permissions_category).apply {
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_PERMS_FOR_WIRED_ANDROID_AUTO,
                R.string.aauto_wired_perms_title,
                R.string.aauto_wired_perms_confirm,
            )
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_PERMS_FOR_WIRELESS_ANDROID_AUTO,
                R.string.aauto_wireless_perms_title,
                R.string.aauto_wireless_perms_confirm,
            )
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_AUDIO_ROUTING_PERM,
                R.string.audio_routing_perm_title,
                R.string.audio_routing_perm_confirm,
            )
            addPkgFlagPerm(
                this, AndroidAutoPackageFlag.GRANT_PERMS_FOR_ANDROID_AUTO_PHONE_CALLS,
                R.string.aauto_phone_perm_title,
                R.string.aauto_phone_perm_confirm,
            )

            addPref(getText(R.string.aauto_app_info_title)).apply {
                setSummary(R.string.aauto_app_info_summary)
                intent = createAppInfoIntent(configuringPkgName)
            }

            addPref(getText(R.string.notif_listener_settings_title)).apply {
                setSummary(R.string.notif_listener_settings_summary)
                intent = getNotifListenerSettingsIntent()
            }
        }

        potentialIssues = screen.addCategory(R.string.potential_issues_category).apply {
            addPref(getText(R.string.aauto_issue_voice_commands)).let {
                aautoVoiceCommandIssues = it
            }
        }

        screen.addCategory(R.string.optional_deps_category).apply {
            addAppDependencyPref("com.google.android.apps.maps", R.string.google_maps_app)
            addAppDependencyPref("com.google.android.tts", R.string.speech_services_app)
            addAppDependencyPref(PackageId.G_SEARCH_APP_NAME, R.string.google_search_app)
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {
        aautoSettingsPref.apply {
            isEnabled = applicationInfo.enabled
            if (applicationInfo.enabled) {
                summary = null
            } else {
                setSummary(R.string.aauto_settings_summary_disabled)
            }
        }

        aautoVoiceCommandIssues.apply {
            val text = getVoiceCommandIssuesText()
            isVisible = text != null

            if (text != null) {
                onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
                    AlertDialog.Builder(requireContext()).run {
                        setMessage(text)
                        show()
                    }
                    true
                }
            }
        }

        potentialIssues.isVisible = aautoVoiceCommandIssues.isVisible
    }

    private fun getVoiceCommandIssuesText(): CharSequence? {
        val list = getVoiceCommandIssues()
        if (list.isEmpty()) {
            return null
        }
        return getString(R.string.aauto_issue_voice_commands_header) + "\n\n" +
                list.map {"• " + getString(it) }.joinToString("\n")
    }

    private fun getVoiceCommandIssues(): List<Int> {
        val list = arrayListOf<Int>()

        if (pkgManager.checkPermission(Manifest.permission.RECORD_AUDIO, PackageId.ANDROID_AUTO_NAME) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_aauto_no_microphone_perm
        }

        val gsaName = PackageId.G_SEARCH_APP_NAME
        val gsaAppInfo = pkgManager.getAppInfoOrNull(gsaName)

        var gsaInstalled = false

        if (gsaAppInfo != null && gsaAppInfo.ext().packageId == PackageId.G_SEARCH_APP) {
            val src = pkgManager.getInstallSourceInfo(gsaName)
            gsaInstalled = src.initiatingPackageName == PackageId.PLAY_STORE_NAME
        }

        if (!gsaInstalled) {
            list += R.string.aauto_issue_gsa_not_installed
            return list
        }

        if (!gsaAppInfo!!.enabled) {
            list += R.string.aauto_issue_gsa_disabled
        }

        if (pkgManager.checkPermission(Manifest.permission.INTERNET, gsaName) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_gsa_no_network_perm
        }

        if (pkgManager.checkPermission(Manifest.permission.RECORD_AUDIO, gsaName) != PERMISSION_GRANTED) {
            list += R.string.aauto_issue_gsa_no_microphone_perm
        }

        return list
    }

    private fun getNotifListenerSettingsIntent(): Intent? {
        val intent = Intent(NotificationListenerService.SERVICE_INTERFACE).apply {
            `package` = PackageId.ANDROID_AUTO_NAME
        }

        val notifListenerServices = pkgManager.queryIntentServices(intent, 0)
        if (notifListenerServices.size != 1) {
            return null
        }

        val nls: ServiceInfo = notifListenerServices.first().serviceInfo

        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            val nlsComponent = ComponentName(nls.packageName, nls.name)
            putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, nlsComponent.flattenToString())
        }
    }
}
