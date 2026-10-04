package com.synapse.crm.campanhas.application;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.campanhas.application.OptOutRepositorio.Origem;
import com.synapse.crm.campanhas.application.OptOutRepositorio.Registro;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Opt-out de campanhas por lead, respeitado por todas elas. A gestao registra (proteger o cliente nunca e o
 * lado arriscado); desfazer um opt-out e do administrador.
 */
@Service
public class OptOutUseCases {

    private final OptOutRepositorio optOuts;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final UsuarioContext usuario;

    public OptOutUseCases(
            OptOutRepositorio optOuts, DisponibilidadeDeCampanhas disponibilidade, UsuarioContext usuario) {
        this.optOuts = optOuts;
        this.disponibilidade = disponibilidade;
        this.usuario = usuario;
    }

    @PreAuthorize(PermissoesDeCampanha.REGISTRAR_OPT_OUT)
    @Auditable(acao = "REGISTRAR_OPTOUT_CAMPANHA", entidadeTipo = "LEAD", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void registrar(UUID leadId, String motivo) {
        disponibilidade.exigir();
        optOuts.registrar(leadId, Origem.MANUAL, motivo, usuario.atual().id());
    }

    @PreAuthorize(PermissoesDeCampanha.OPT_OUT)
    @Auditable(acao = "REMOVER_OPTOUT_CAMPANHA", entidadeTipo = "LEAD", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean remover(UUID leadId) {
        disponibilidade.exigir();
        return optOuts.remover(leadId);
    }

    public record Lista(java.util.List<Registro> itens, long total, int pagina, int tamanho) {}

    @PreAuthorize(PermissoesDeCampanha.LEITURA_DE_DESTINATARIOS)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Lista listar(int pagina, int tamanho) {
        disponibilidade.exigir();
        int paginaSegura = Pagina.paginaSegura(pagina);
        int tamanhoSeguro = Pagina.tamanhoSeguro(tamanho);
        return new Lista(optOuts.listar(paginaSegura, tamanhoSeguro), optOuts.contar(), paginaSegura, tamanhoSeguro);
    }
}
