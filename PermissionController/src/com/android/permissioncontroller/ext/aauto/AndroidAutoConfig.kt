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
import com.android.permissioncontroller.ext.IssueCheck
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
    private val aautoVoiceIssueChecks: List<IssueCheck> = listOf(
        IssueCheck.PermissionOnly(
            fragment = this,
            permission = Manifest.permission.RECORD_AUDIO,
            issueStringRes = R.string.aauto_issue_aauto_no_microphone_perm
        ),
        IssueCheck.App(
            packageName = PackageId.G_SEARCH_APP_NAME,
            packageId = PackageId.G_SEARCH_APP,
            notInstalledStringRes = R.string.aauto_issue_gsa_not_installed,
            notEnabledStringRes = R.string.aauto_issue_gsa_disabled,
            permissionChecks = listOf(
                IssueCheck.App.Permission(
                    Manifest.permission.INTERNET,
                    R.string.aauto_issue_gsa_no_network_perm
                ),
                IssueCheck.App.Permission(
                    Manifest.permission.RECORD_AUDIO,
                    R.string.aauto_issue_gsa_no_microphone_perm
                ),
            )
        )
    )

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

        aautoVoiceCommandIssues.updateWithIssues(
            R.string.aauto_issue_voice_commands_header,
            aautoVoiceIssueChecks
        )
        potentialIssues.isVisible = aautoVoiceCommandIssues.isVisible
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
