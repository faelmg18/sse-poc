package com.example.ssepoc.ui.xml

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ssepoc.R
import com.example.ssepoc.viewmodel.SseViewModel
import kotlinx.coroutines.launch

class SseFragment : Fragment() {

    private val vm: SseViewModel by viewModels()

    private lateinit var tvStatus: TextView
    private lateinit var viewStatusDot: View
    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnClear: Button
    private lateinit var tvError: TextView
    private lateinit var tvEvents: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_sse, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupClicks()
        observeState()
    }

    private fun bindViews(view: View) {
        tvTitle = view.findViewById(R.id.tvTitle)
        tvStatus = view.findViewById(R.id.tvStatus)
        viewStatusDot = view.findViewById(R.id.viewStatusDot)
        btnConnect = view.findViewById(R.id.btnConnect)
        btnDisconnect = view.findViewById(R.id.btnDisconnect)
        btnClear = view.findViewById(R.id.btnClear)
        tvError = view.findViewById(R.id.tvError)
        tvEvents = view.findViewById(R.id.tvEvents)
        scrollView = view.findViewById(R.id.scrollView)
    }

    private fun setupClicks() {
        btnConnect.setOnClickListener { vm.connect() }
        btnDisconnect.setOnClickListener { vm.disconnect() }
        btnClear.setOnClickListener { vm.clearEvents() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    // Status
                    val dotColor = if (state.isConnected) Color.parseColor("#4CAF50") else Color.GRAY
                    viewStatusDot.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(dotColor)
                    tvStatus.text = if (state.isConnected) "Conectado" else "Desconectado"

                    // Botões
                    btnConnect.isEnabled = !state.isConnected
                    btnDisconnect.isEnabled = state.isConnected

                    // Erro
                    tvError.isVisible = state.error != null
                    tvError.text = state.error?.let { "Erro: $it" }

                    // Eventos — renderiza cada um como bloco de texto
                    if (state.events.isEmpty()) {
                        tvEvents.text = ""
                        tvEvents.hint = "Nenhum evento recebido ainda."
                    } else {
                        tvEvents.text = state.events.joinToString(separator = "\n\n") { event ->
                            buildString {
                                event.id?.let { appendLine("[id: $it]") }
                                event.event?.let { appendLine("[event: $it]") }
                                event.data?.let { append(it) }
                            }
                        }
                        // Auto-scroll para o fim
                        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
    }

    // Declaração tardia para evitar crash antes do onViewCreated
    private lateinit var tvTitle: TextView
}
