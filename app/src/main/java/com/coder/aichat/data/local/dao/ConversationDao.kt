package com.coder.aichat.data.local.dao

import androidx.room.*
import com.coder.aichat.data.local.entity.ConversationEntity
import com.coder.aichat.data.local.entity.ConversationRow
import com.coder.aichat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    // ── 会话 ──
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /** 会话列表（含最后一条消息预览），按更新时间倒序 */
    @Query("""
        SELECT c.id, c.title, c.providerId, c.modelId,
            c.created_at AS createdAt, c.updated_at AS updatedAt,
            (SELECT m.content FROM messages m WHERE m.conversation_id = c.id
             ORDER BY m.timestamp DESC LIMIT 1) AS lastMessage
        FROM conversations c
        ORDER BY c.updated_at DESC
    """)
    fun getAllConversationsWithPreview(): Flow<List<ConversationRow>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    // 注意：必须用 @Upsert 而不是 @Insert(REPLACE)。
    // REPLACE 会先 DELETE 旧行再 INSERT，触发 messages 的 ON DELETE CASCADE 清空全部消息。
    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    // ── 消息 ──
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversation_id = :id")
    suspend fun deleteMessages(id: String)
}
