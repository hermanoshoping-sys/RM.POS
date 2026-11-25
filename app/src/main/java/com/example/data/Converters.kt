package com.example.data

import androidx.room.TypeConverter

/**
 * Room TypeConverters for seamless mapping between SQLite data types and Kotlin representations.
 */
class RoleConverters {
    @TypeConverter
    fun fromUserRole(role: UserRole?): String {
        return (role ?: UserRole.CASHIER).name
    }

    @TypeConverter
    fun toUserRole(value: String?): UserRole {
        return UserRole.fromString(value)
    }
}
