package com.museovirtualnacional.strogoff.data.local.converters

import androidx.room.TypeConverter
import com.museovirtualnacional.strogoff.data.local.entity.Category
import com.museovirtualnacional.strogoff.data.local.entity.EditorialLine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromCategoryList(value: List<Category>?): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toCategoryList(value: String): List<Category> {
        val listType = object : TypeToken<List<Category>>() {}.type
        return try {
            Gson().fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromEditorialLine(value: EditorialLine): String {
        return value.name
    }

    @TypeConverter
    fun toEditorialLine(value: String): EditorialLine {
        return try {
            EditorialLine.valueOf(value)
        } catch (e: Exception) {
            EditorialLine.UNKNOWN
        }
    }
}
