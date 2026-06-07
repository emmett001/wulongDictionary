package com.wulong.dict.domain.model

enum class Language(val code: String, val displayName: String) {
    EN("en", "English"),
    JA("ja", "日本語"),
    DE("de", "Deutsch"),
    KO("ko", "한국어"),
    RU("ru", "Русский");

    companion object {
        fun fromCode(code: String): Language =
            entries.firstOrNull { it.code == code } ?: EN
    }
}
