package com.coder.aichat.ui.history

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.coder.aichat.AiChatApp
import com.coder.aichat.R
import com.coder.aichat.data.local.entity.ConversationRow
import com.coder.aichat.databinding.ActivityHistoryBinding
import com.coder.aichat.ui.chat.ChatActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = HistoryAdapter(::openConversation, ::confirmDelete)
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        observeConversations()
    }

    private fun observeConversations() {
        val app = application as AiChatApp
        lifecycleScope.launch {
            app.repository.getAllConversationsWithPreview().collect { list ->
                adapter.submitList(list)
                binding.emptyView.isVisible = list.isEmpty()
            }
        }
    }

    private fun openConversation(conv: ConversationRow) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_PROVIDER_ID, conv.providerId)
            putExtra(ChatActivity.EXTRA_MODEL_ID, conv.modelId)
            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.id)
        }
        startActivity(intent)
    }

    private fun confirmDelete(conv: ConversationRow) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(conv.title)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    (application as AiChatApp).repository.deleteConversation(conv.id)
                    Toast.makeText(this@HistoryActivity, "已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
