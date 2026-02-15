package com.android.permissioncontroller.ext

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.ext.PackageId
import android.provider.Telephony
import androidx.annotation.StringRes
import getAppInfoOrNull

sealed class IssueCheck {

    abstract fun getStringResOfIssues(context: Context, packageManager: PackageManager): List<Int>

    /**
     * Checks [permission] of the [packageName] without any installed or enabled checks.
     */
    class PermissionOnly(
        fragment: BaseGosConfigFragment,
        private val permission: String,
        @StringRes private val issueStringRes: Int,
    ) : IssueCheck() {
        private val packageName = fragment.configuringPkgName
        override fun getStringResOfIssues(context: Context, packageManager: PackageManager): List<Int> {
            return if (
                packageManager.checkPermission(permission, packageName)
                != PERMISSION_GRANTED
            ) {
                listOf(issueStringRes)
            } else {
                emptyList()
            }
        }
    }

    class App(
        private val packageName: String,
        private val packageId: Int,
        @StringRes private val notInstalledStringRes: Int,
        @StringRes private val notEnabledStringRes: Int,
        private val appChecks: List<Check> = emptyList(),
    ) : IssueCheck() {

        sealed interface Check {
            val issueStringRes: Int
        }

        class Permission(val permission: String, override val issueStringRes: Int) : Check
        class SmsRole(override val issueStringRes: Int) : Check

        override fun getStringResOfIssues(context: Context, packageManager: PackageManager): List<Int> {
            val appInfo = packageManager.getAppInfoOrNull(packageName)

            var appInstalled = false

            if (appInfo != null && appInfo.ext().packageId == packageId) {
                val src = packageManager.getInstallSourceInfo(packageName)
                appInstalled = src.initiatingPackageName == PackageId.PLAY_STORE_NAME
            }

            if (appInfo == null || !appInstalled) {
                return listOf(notInstalledStringRes)
            }

            return buildList {
                if (!appInfo.enabled) {
                    add(notEnabledStringRes)
                }

                for (permCheck in appChecks) {
                    when (permCheck) {
                        is Permission -> {
                            if (
                                packageManager.checkPermission(permCheck.permission, packageName)
                                != PERMISSION_GRANTED
                            ) {
                                add(permCheck.issueStringRes)
                            }
                        }
                        is SmsRole -> {
                            if (Telephony.Sms.getDefaultSmsPackage(context) != packageName) {
                                add(permCheck.issueStringRes)
                            }
                        }
                    }
                }
            }
        }
    }
}
