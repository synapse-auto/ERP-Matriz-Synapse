package com.synapse.crm.atendimento.interfaces;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;

/**
 * A lista de conversas da tela de Atendimentos. O recorte por papel acontece no caso de uso, nunca
 * aqui — este controller so pede a visao e devolve o que veio.
 */
@RestController
@RequestMapping("/api/v1/atendimentos")
class PainelDeAtendimentosController {

    private final ListarAtendimentosVisiveisUseCase listar;

    PainelDeAtendimentosController(ListarAtendimentosVisiveisUseCase listar) {
        this.listar = listar;
    }

    @GetMapping
    List<CartaoAtendimento> listar(@RequestParam VisaoAtendimento visao) {
        return listar.executar(visao);
    }
}
