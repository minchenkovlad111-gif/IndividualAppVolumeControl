package com.duda.appvolumecontrol.core

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

object ProfileUtils {
    fun currentUserId(): Int? {
        return PlaybackUtils.getUserIdFromUid(Process.myUid())
    }

    fun buildProfileLabel(context: Context, userId: Int?): String {
        if (userId == null) return "All profiles"
        val current = currentUserId()
        val isManaged = isManagedProfile(context, userId)
        return when {
            current != null && userId == current -> "Personal (u$userId)"
            isManaged -> "Work (u$userId)"
            else -> "User u$userId"
        }
    }

    fun collectUserIds(context: Context, extraUserIds: Collection<Int?> = emptyList()): List<Int> {
        val userIds = mutableSetOf<Int>()
        extraUserIds.filterNotNullTo(userIds)
        currentUserId()?.let { userIds.add(it) }
        val userManager = context.getSystemService(UserManager::class.java)
        val profiles = runCatching { userManager.userProfiles }.getOrNull().orEmpty()
        for (handle in profiles) {
            getUserId(handle)?.let { userIds.add(it) }
        }
        return userIds.sorted()
    }

    private fun getUserId(handle: UserHandle): Int? {
        return try {
            val method = UserHandle::class.java.getDeclaredMethod("getIdentifier")
            method.isAccessible = true
            method.invoke(handle) as? Int
        } catch (_: Exception) {
            null
        }
    }

    private fun isManagedProfile(context: Context, userId: Int): Boolean {
        val userManager = context.getSystemService(UserManager::class.java)
        return try {
            val method = UserManager::class.java.getDeclaredMethod(
                "isManagedProfile",
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(userManager, userId) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
}
