package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Os atendimentos abertos que o usuário alcança, para escolher o destino do encaminhamento. Busca no servidor:
 * a listagem completa não escala para quem enxerga todos os atendimentos, e a seleção na tela não pode
 * depender de um recorte já carregado.
 *
 * <p>O alcance é o da RLS (RN-CRM-01), a mesma do envio: um atendente vê os seus e os potenciais; gestão e
 * subgestão, todos. Quem não pode responder não escolhe destino algum.
 */
@Service
public class BuscarDestinosDoEncaminhamentoUseCase {

    /** Tamanho da página de resultados: o usuário refina pela busca, não rola uma lista longa. */
    static final int LIMITE_DE_RESULTADOS = 20;

    private final EncaminhamentoDoChatRepositorio encaminhamentos;

    public BuscarDestinosDoEncaminhamentoUseCase(EncaminhamentoDoChatRepositorio encaminhamentos) {
        this.encaminhamentos = encaminhamentos;
    }

    @PreAuthorize("isAuthenticated() and @capacidades.permite('atendimentos.responder')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<DestinoDoEncaminhamento> executar(String busca) {
        return encaminhamentos.buscarDestinosAbertos(busca, LIMITE_DE_RESULTADOS).stream()
                .map(destino -> new DestinoDoEncaminhamento(
                        destino.atendimentoId(),
                        destino.clienteNome(),
                        TelefoneMascarado.de(destino.telefone()),
                        destino.statusAtendimento(),
                        destino.responsavelNome()))
                .toList();
    }
}
