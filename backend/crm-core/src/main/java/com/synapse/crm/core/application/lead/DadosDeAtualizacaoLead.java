package com.synapse.crm.core.application.lead;

import java.util.Map;
import java.util.UUID;

import com.synapse.crm.core.domain.lead.CodigoDoLead;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.TelefoneCanonico;

/**
 * Campos editaveis da ficha do lead. Atualizacao parcial: {@code null} significa "nao mexa".
 *
 * <p>Parcial, e nao substituicao total, porque a tela edita dois ou tres campos por vez. Com
 * semantica de substituicao, salvar o telefone apagaria empresa, e-mail e localizacao — perda de
 * dado silenciosa, do tipo que so aparece quando alguem procura a informacao que sumiu.
 *
 * <p>Tres campos do schema <b>nao</b> estao aqui, de proposito:
 *
 * <ul>
 *   <li>{@code atendenteResponsavelId} — trocar o dono e transferencia de lead, nao edicao de
 *       ficha. Aceitar isso num PUT daria a qualquer atendente uma forma de largar um lead
 *       dificil, ou de puxar para si o de um colega. A transferencia tem regra propria (RN-CRM-06)
 *       e vira caso de uso separado.
 *   <li>{@code resumoIa} — quem escreve e a Automacao.
 *   <li>contadores — so mudam por incremento, na transacao que cria o atendimento ou a mensagem.
 * </ul>
 */
public record DadosDeAtualizacaoLead(
        String nome,
        String fotoUrl,
        String telefone,
        String email,
        String cpf,
        String empresa,
        String codigo,
        String localizacao,
        UUID canalOrigemId,
        StatusBasicoLead statusBasico,
        UUID etapaAtendimentoId,
        String notas,
        Map<String, Object> dadosCustomizados) {

    /**
     * Aplica sobre o lead atual, preservando tudo que veio nulo e tudo que nao e editavel.
     *
     * <p>{@code dadosCustomizados} nao e mesclado aqui: ao contrario dos demais campos, ele precisa
     * ser validado contra {@code campo_customizado} (consulta ao banco), o que esta fora do alcance
     * de um metodo puro de dominio. {@link AtualizarLeadUseCase} faz essa validacao e aplica o
     * resultado com {@link Lead#comDadosCustomizados}. Aqui o valor atual so e preservado.
     */
    Lead aplicarEm(Lead atual, TelefoneCanonico telefoneCanonico) {
        return new Lead(
                atual.id(),
                ouAtual(nome, atual.nome()),
                ouAtual(fotoUrl, atual.fotoUrl()),
                telefone == null ? atual.telefone() : telefoneCanonico.normalizar(telefone),
                ouAtual(email, atual.email()),
                ouAtual(cpf, atual.cpf()),
                ouAtual(empresa, atual.empresa()),
                codigo == null ? atual.codigo() : CodigoDoLead.normalizar(codigo),
                ouAtual(localizacao, atual.localizacao()),
                ouAtual(canalOrigemId, atual.canalOrigemId()),
                ouAtual(statusBasico, atual.statusBasico()),
                ouAtual(etapaAtendimentoId, atual.etapaAtendimentoId()),
                // Dono preservado sempre: transferencia de lead nao passa por edicao de ficha.
                atual.atendenteResponsavelId(),
                ouAtual(notas, atual.notas()),
                atual.resumoIa(),
                atual.numAtendimentos(),
                atual.numMensagens(),
                atual.criadoEm(),
                atual.dadosCustomizados());
    }

    private static <T> T ouAtual(T novo, T atual) {
        return novo == null ? atual : novo;
    }
}
