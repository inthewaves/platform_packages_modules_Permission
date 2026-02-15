package com.android.permissioncontroller.ext

import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.ext.PackageId
import androidx.annotation.StringRes
import getAppInfoOrNull

sealed class IssueCheck {

    abstract fun getStringResOfIssues(packageManager: PackageManager): List<Int>

    /**
     * Checks [permission] of the [packageName] without any installed or enabled checks.
     */
    class PermissionOnly(
        fragment: BaseGosPkgStateConfigFragment,
        private val permission: String,
        @StringRes private val issueStringRes: Int,
    ) : IssueCheck() {
        private val packageName = fragment.configuringPkgName
        override fun getStringResOfIssues(packageManager: PackageManager): List<Int> {
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
        private val permissionChecks: List<Permission> = emptyList(),
    ) : IssueCheck() {

        class Permission(val permission: String, val issueStringRes: Int)

        override fun getStringResOfIssues(packageManager: PackageManager): List<Int> {
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

                for (permCheck in permissionChecks) {
                    if (
                        packageManager.checkPermission(permCheck.permission, packageName)
                        != PERMISSION_GRANTED
                    ) {
                        add(permCheck.issueStringRes)
                    }
                }
            }
        }
    }
}
