package com.calorie.app.ui.chat

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.calorie.app.databinding.FragmentChatBinding
import kotlinx.coroutines.launch

/** Чат с приложением: текст плюс действия, которые применяются к плану и профилю. */
class ChatFragment : Fragment(com.calorie.app.R.layout.fragment_chat) {

    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!
    private val vm: ChatViewModel by viewModels()
    private val adapter = ChatAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentChatBinding.bind(view)

        b.recycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            // Чат должен «прилипать» к низу: иначе новый ответ остаётся за экраном.
            stackFromEnd = true
        }
        b.recycler.adapter = adapter

        val send = {
            val text = b.etChatMessage.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                vm.send(text)
                b.etChatMessage.setText("")
            }
        }
        b.btnChatSend.setOnClickListener { send() }
        b.etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send()
                true
            } else {
                false
            }
        }
        b.btnChatClear.setOnClickListener { vm.clearHistory() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.messages.collect { messages ->
                        adapter.submit(messages)
                        b.txtChatEmpty.visibility =
                            if (messages.isEmpty()) View.VISIBLE else View.GONE
                        if (messages.isNotEmpty()) {
                            b.recycler.post {
                                b.recycler.scrollToPosition(messages.lastIndex)
                            }
                        }
                    }
                }
                launch {
                    vm.error.collect { error ->
                        if (error != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                            vm.clearError()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
