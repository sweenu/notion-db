package com.notiondb.widgets.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal Notion API models for Phase 0. These cover the "validate the token"
 * round-trip; richer models (data sources, views, pages, properties) arrive in
 * Phase 1 when the data layer starts rendering rows.
 */

@Serializable
data class NotionUser(
    val id: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val type: String? = null,
    val bot: BotInfo? = null,
)

@Serializable
data class BotInfo(
    @SerialName("workspace_name") val workspaceName: String? = null,
)

@Serializable
data class NotionError(
    val status: Int = 0,
    val code: String = "",
    val message: String = "",
)
