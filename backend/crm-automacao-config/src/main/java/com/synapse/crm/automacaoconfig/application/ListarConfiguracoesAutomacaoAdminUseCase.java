package com.synapse.crm.automacaoconfig.application;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;

/**
 * Lista todos os parametros para o painel administrativo — o que {@code GET /api/v1/automacao/config}
 * (E15b §1) devolve. Mesma leitura de {@link ListarConfiguracoesAutomacaoUseCase} contra
 * {@link ConfiguracaoAutomacaoRepositorio} — nao duplica a query — mas com a autorizacao de quem pode
 * <b>editar</b>, a mesma exigida por {@link AtualizarConfiguracaoAutomacaoUseCase}: nao faz sentido o
 * painel mostrar valor e faixa para quem nao pode salvar nada.
 *
 * <p>Separada de {@link ListarConfiguracoesAutomacaoUseCase} porque aquela e chamada pelo contrato
 * interno ({@code /internal/v1}, autenticado por {@code X-Synapse-Token}) e nao pode passar a exigir
 * papel de usuario humano sem quebrar a Automacao de todo filho.
 */
@Service
public class ListarConfiguracoesAutomacaoAdminUseCase {

    private final ConfiguracaoAutomacaoRepositorio configuracoes;

    public ListarConfiguracoesAutomacaoAdminUseCase(ConfiguracaoAutomacaoRepositorio configuracoes) {
        this.configuracoes = configuracoes;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR')")
    @Transactional(readOnly = true)
    public List<ConfiguracaoAutomacao> executar() {
        return configuracoes.listarTodas();
    }
}
