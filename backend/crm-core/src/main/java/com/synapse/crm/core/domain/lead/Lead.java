package com.synapse.crm.core.domain.lead;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lead: a pessoa que procurou a empresa. Ficha completa.
 *
 * <p>Java puro — sem JPA, sem Spring. O mapeamento para tabela mora em infrastructure.
 *
 * <p>Para listagem existe {@link LeadResumo}, que nao carrega {@code notas} nem {@code resumoIa}.
 * Sao campos longos e a separacao e por tipo, nao por disciplina: o metodo de listar nao tem onde
 * colocar esses campos, entao nao ha como traze-los por descuido. {@code dadosCustomizados} (E06b)
 * segue a mesma regra — tambem fica de fora de {@code LeadResumo}.
 */
public record Lead(
        UUID id,
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
        UUID atendenteResponsavelId,
        String notas,
        String resumoIa,
        int numAtendimentos,
        int numMensagens,
        Instant criadoEm,
        Map<String, Object> dadosCustomizados) {

    public Lead {
        Objects.requireNonNull(id, "id do lead e obrigatorio");
        Objects.requireNonNull(nome, "nome do lead e obrigatorio");
        Objects.requireNonNull(statusBasico, "status basico do lead e obrigatorio");
        dadosCustomizados = dadosCustomizados == null ? Map.of() : Map.copyOf(dadosCustomizados);
    }

    /** Copia com os dados customizados substituidos — ja validados contra {@code campo_customizado}. */
    public Lead comDadosCustomizados(Map<String, Object> novosDadosCustomizados) {
        return new Lead(
                id, nome, fotoUrl, telefone, email, cpf, empresa, codigo, localizacao, canalOrigemId,
                statusBasico, etapaAtendimentoId, atendenteResponsavelId, notas, resumoIa,
                numAtendimentos, numMensagens, criadoEm, novosDadosCustomizados);
    }

    /** RN-CRM-02: lead atribuido a um atendente pertence a ele. */
    public boolean pertenceA(UUID atendenteId) {
        return atendenteResponsavelId != null && atendenteResponsavelId.equals(atendenteId);
    }

    /** Sem dono: esta no grupo "Potenciais" e qualquer atendente pode pegar. */
    public boolean estaDisponivelParaTodos() {
        return statusBasico == StatusBasicoLead.IA;
    }

    /**
     * Constroi um lead so com o que a regra de visibilidade precisa.
     *
     * <p>Existe para teste e para exercicios de regra; nunca para representar um lead vindo do
     * banco, que sempre chega completo.
     */
    public static Lead apenasParaVisibilidade(
            UUID id, String nome, StatusBasicoLead status, UUID atendenteResponsavelId) {
        return new Lead(
                id, nome, null, null, null, null, null, null, null, null, status, null,
                atendenteResponsavelId, null, null, 0, 0, null, null);
    }
}
