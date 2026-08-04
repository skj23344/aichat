package com.coder.aichat.data.local.entity

import androidx.room.*

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversation_id")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** 会话列表行（含最后一条消息预览） */
data class ConversationRow(
    val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessage: String?
)
