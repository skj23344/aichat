package com.coder.aichat.data.repository

import com.coder.aichat.data.api.dto.ChatMessage
import com.coder.aichat.data.api.providers.ProviderRegistry
import com.coder.aichat.data.local.dao.ConversationDao
import com.coder.aichat.data.local.entity.ConversationEntity
import com.coder.aichat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ConversationDao) {

    fun getAllConversations() = dao.getAllConversations()
    fun getAllConversationsWithPreview() = dao.getAllConversationsWithPreview()
    fun getMessages(conversationId: String) = dao.getMessages(conversationId)
    suspend fun getConversation(id: String) = dao.getConversation(id)

    suspend fun createConversation(title: String, providerId: String, modelId: String): String {
        val id = java.util.UUID.randomUUID().toString()
        dao.upsertConversation(
            ConversationEntity(id = id, title = title, providerId = providerId, modelId = modelId)
        )
        return id
    }

    suspend fun saveMessage(conversationId: String, msg: ChatMessage) {
        dao.insertMessage(
            MessageEntity(
                id = msg.id,
                conversationId = conversationId,
                role = msg.role.toString(),
                content = msg.content,
                timestamp = msg.timestamp
            )
        )
        dao.getConversation(conversationId)?.let {
            dao.upsertConversation(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteConversation(conversationId: String) {
        dao.getConversation(conversationId)?.let { dao.deleteConversation(it) }
    }

    fun chatStream(
        providerId: String,
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Double = 0.7
    ): Flow<String> {
        val provider = ProviderRegistry.get(providerId)
            ?: throw IllegalArgumentException("Unknown provider: $providerId")
        return provider.chatStream(messages, modelId, systemPrompt, temperature)
    }
}
