package com.coder.aichat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 角色扮演设定 */
data class RolePlay(
    val id: String,
    val name: String,
    val prompt: String,          // 人设/系统提示词
    val temperature: Double = 0.7
)

/** 内置预设角色 */
object RolePlayPresets {
    val defaults = listOf(
        RolePlay("assistant", "默认助手", "你是一个乐于助人的 AI 助手。回答简洁、准确、友好，默认使用中文。", 0.7),
        RolePlay("coder", "编程专家", "你是一位资深程序员，精通 Kotlin、Java、Python、C++ 等。回答时给出可运行的代码、清晰的解释，并提醒潜在的坑。", 0.4),
        RolePlay("writer", "写作大师", "你是一位专业作家，擅长小说、散文、文案创作。语言生动有感染力，善用修辞与细节描写。", 0.9),
        RolePlay("translator", "翻译官", "你是一位专业翻译，精通中英互译。翻译准确、地道、符合目标语言习惯，必要时补充注释说明。", 0.3),
        RolePlay("teacher", "耐心导师", "你是一位耐心的老师，善于用生活化类比由浅入深地讲解复杂概念，鼓励提问。", 0.5),
        RolePlay("psychologist", "心理咨询师", "你是一位温暖专业的心理咨询师。善于倾听、共情、不评判，提供温和而有建设性的支持。", 0.6),
    )
}

private val Context.rolePlayDataStore: DataStore<Preferences> by preferencesDataStore(name = "roleplay")

class RolePlayStore(private val context: Context) {

    companion object {
        private val KEY_ROLES = stringPreferencesKey("roles_json")
        private val KEY_ACTIVE = stringPreferencesKey("active_role_id")
        private val gson = Gson()
    }

    val roles: Flow<List<RolePlay>> =
        context.rolePlayDataStore.data.map { prefs ->
            val json = prefs[KEY_ROLES]
            if (json.isNullOrBlank()) RolePlayPresets.defaults
            else runCatching { gson.fromJson(json, Array<RolePlay>::class.java).toList() }
                .getOrDefault(RolePlayPresets.defaults)
        }

    suspend fun getRoles(): List<RolePlay> = roles.first()

    suspend fun saveRoles(roles: List<RolePlay>) {
        context.rolePlayDataStore.edit { it[KEY_ROLES] = gson.toJson(roles) }
    }

    suspend fun getActiveRoleId(): String =
        context.rolePlayDataStore.data.map { it[KEY_ACTIVE] ?: "assistant" }.first()

    suspend fun setActiveRoleId(id: String) {
        context.rolePlayDataStore.edit { it[KEY_ACTIVE] = id }
    }
}
