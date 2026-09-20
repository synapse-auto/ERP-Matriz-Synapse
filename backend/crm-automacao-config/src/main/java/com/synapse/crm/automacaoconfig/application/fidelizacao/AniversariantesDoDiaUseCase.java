package com.synapse.crm.automacaoconfig.application.fidelizacao;

import java.time.Clock;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.application.ObterConfiguracaoAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.fidelizacao.LeadAniversariante;

/**
 * Aniversariantes de hoje mais a configuracao da mensagem —
 * {@code GET /internal/v1/fidelizacao/aniversariantes-hoje}.
 *
 * <p>Resposta unica de proposito: a Automacao precisa de destinatario e texto na mesma chamada, e
 * duas chamadas abririam uma janela em que a configuracao muda entre uma e outra.
 *
 * <p>O desligamento e aplicado aqui, no servidor, e nao devolvido como dica: com
 * {@code fidelizacao.aniversario.habilitado = false} a lista sai vazia sempre. Se o gate ficasse so
 * na Automacao, um fluxo que esquecesse de checar o campo dispararia parabens com o recurso
 * desligado — e mensagem enviada nao volta atras.
 */
@Service
public class AniversariantesDoDiaUseCase {

    /** Chaves criadas pela V74; a tela de fidelizacao ja edita as duas. */
    static final String CHAVE_HABILITADO = "fidelizacao.aniversario.habilitado";

    static final String CHAVE_MENSAGEM = "fidelizacao.aniversario.mensagem";

    private final ObterConfiguracaoAutomacaoUseCase configuracao;
    private final AniversariantesDoDiaRepositorio aniversariantes;
    private final Clock relogio;
    private final ZoneId fusoDoTenant;

    public AniversariantesDoDiaUseCase(
            ObterConfiguracaoAutomacaoUseCase configuracao,
            AniversariantesDoDiaRepositorio aniversariantes,
            Clock relogio,
            ZoneId fusoDoTenant) {
        this.configuracao = configuracao;
        this.aniversariantes = aniversariantes;
        this.relogio = relogio;
        this.fusoDoTenant = fusoDoTenant;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(readOnly = true)
    public Resultado executar() {
        boolean habilitado = configuracao
                .executar(CHAVE_HABILITADO)
                .map(ConfiguracaoAutomacao::valor)
                .map(valor -> "true".equalsIgnoreCase(valor.trim()))
                .orElse(false);
        String mensagem = configuracao
                .executar(CHAVE_MENSAGEM)
                .map(ConfiguracaoAutomacao::valor)
                .orElse(null);
        if (!habilitado) {
            return new Resultado(false, mensagem, List.of());
        }
        MonthDay hoje = MonthDay.from(relogio.instant().atZone(fusoDoTenant));
        return new Resultado(true, mensagem, aniversariantes.doDia(hoje));
    }

    /** O que a Automacao recebe: o gate, o texto configurado e os destinatarios de hoje. */
    public record Resultado(boolean habilitado, String mensagem, List<LeadAniversariante> leads) {}
}
