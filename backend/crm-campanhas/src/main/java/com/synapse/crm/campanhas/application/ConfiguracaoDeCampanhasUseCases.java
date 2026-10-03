package com.synapse.crm.campanhas.application;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhas.Atualizacao;
import com.synapse.crm.campanhas.application.ConfiguracaoDeCampanhas.Parametros;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** "Configuracoes da instancia" de campanhas: teto diario, limiares de pausa automatica, limite da Meta. */
@Service
public class ConfiguracaoDeCampanhasUseCases {

    private final ConfiguracaoDeCampanhas configuracao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final UsuarioContext usuario;

    public ConfiguracaoDeCampanhasUseCases(
            ConfiguracaoDeCampanhas configuracao, DisponibilidadeDeCampanhas disponibilidade, UsuarioContext usuario) {
        this.configuracao = configuracao;
        this.disponibilidade = disponibilidade;
        this.usuario = usuario;
    }

    @PreAuthorize(PermissoesDeCampanha.LEITURA)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Parametros obter() {
        disponibilidade.exigir();
        return configuracao.atuais();
    }

    @PreAuthorize(PermissoesDeCampanha.CONFIGURAR)
    @Auditable(acao = "ATUALIZAR_CONFIGURACAO_CAMPANHAS", entidadeTipo = "CONFIGURACAO", capturarDados = false)
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Parametros atualizar(Atualizacao atualizacao) {
        disponibilidade.exigir();
        configuracao.atualizar(atualizacao, usuario.atual().id());
        return configuracao.atuais();
    }
}
