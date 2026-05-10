# WulongDict ProGuard Rules
# Keep Room entities, DAOs, and SQLite engine (used via reflection)
-keep class com.wulong.dict.data.local.** { *; }
# Keep domain models (may be serialized/deserialized)
-keep class com.wulong.dict.domain.model.** { *; }
