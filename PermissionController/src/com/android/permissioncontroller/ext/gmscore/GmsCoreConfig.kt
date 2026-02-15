package com.android.permissioncontroller.ext.gmscore

import android.Manifest
import android.app.compat.gms.GmsCorePackageFlag
import android.content.pm.ApplicationInfo
import android.ext.PackageId
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.android.permissioncontroller.R
import com.android.permissioncontroller.ext.BaseGosConfigFragment
import com.android.permissioncontroller.ext.BaseSettingsActivity
import com.android.permissioncontroller.ext.IssueCheck
import com.android.permissioncontroller.ext.addCategory
import com.android.permissioncontroller.ext.addPref
import com.android.permissioncontroller.permission.ui.handheld.PermissionsCollapsingToolbarBaseFragment

class GmsCoreConfigActivity : BaseSettingsActivity() {
    override fun getNavGraphStart() = R.id.gmscore_config
}

class GmsCoreConfigWrapperFragment : PermissionsCollapsingToolbarBaseFragment() {
    override fun createPreferenceFragment(): PreferenceFragmentCompat = GmsCoreConfigFragment()
}

class GmsCoreConfigFragment : BaseGosConfigFragment(
    configuringPkgName = PackageId.GMS_CORE_NAME,
    titleStringRes = R.string.gmscore_settings
) {
    lateinit var rcsPotentialIssues: Preference

    private val rcsIssueChecks = listOf(
        IssueCheck.OwnerUser(R.string.rcs_issue_not_owner_user),
        IssueCheck.PermissionOnly(
            this,
            Manifest.permission.READ_PHONE_STATE,
            R.string.rcs_issue_gmscore_no_phone_perm,
        ),
        IssueCheck.App(
            packageName = PackageId.BUGLE_NAME,
            packageId = PackageId.BUGLE,
            notInstalledStringRes = R.string.rcs_issue_bugle_not_installed,
            notEnabledStringRes = R.string.rcs_issue_bugle_disabled,
            appChecks = listOf(
                IssueCheck.App.SmsRole(R.string.rcs_issue_bugle_not_sms_app)
            )
        )
    )

    override fun configurePreferenceScreen(screen: PreferenceScreen) {
        screen.addCategory(R.string.rcs_activation_category).apply {
            addPkgFlagPerm(
                this, GmsCorePackageFlag.GRANT_PERMS_FOR_ICC_AUTHENTICATION,
                R.string.gmscore_icc_auth_perms_title,
                R.string.gmscore_icc_auth_perms_confirm,
            )
            addAppDependencyPref(PackageId.BUGLE_NAME, R.string.google_messages_app)
            rcsPotentialIssues = addPref(getText(R.string.rcs_potential_issues))
        }
    }

    override fun updateNonPkgStateUi(applicationInfo: ApplicationInfo) {
        rcsPotentialIssues.updateWithIssues(R.string.rcs_issue_header, rcsIssueChecks)
    }
}
