package com.synapse.crm.core.interfaces.lead;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.synapse.crm.core.domain.lead.LeadResumo;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.tag.Tag;

/**
 * O que qualquer listagem de lead devolve.
 *
 * <p>Sem {@code notas} e sem {@code resumoIa}: nao existem neste tipo. A listagem simples e o filtro
 * modular compartilham este record de proposito — duas formas do mesmo dado seriam duas chances de
 * uma delas ganhar um campo longo sem ninguem notar.
 *
 * <p>{@code tags} chega de fora ({@link #de(LeadResumo, List)}), nunca resolvida aqui: quem monta a
 * resposta busca as tags de toda a pagina numa consulta so (E16 §Bloco 1) e passa a fatia de cada
 * lead — o tipo em si nao sabe de onde ela veio.
 */
record LeadDaLista(
        @Schema(description = "Identificador do lead.") UUID id,
        @Schema(description = "Nome do lead.") String nome,
        @Schema(description = "Telefone principal.") String telefone,
        @Schema(description = "Empresa, quando informada.") String empresa,
        @Schema(description = "Cidade/localização, quando informada.") String localizacao,
        @Schema(description = "Status básico.") StatusBasicoLead status,
        @Schema(description = "Etapa atual do funil.") UUID etapaAtendimentoId,
        @Schema(description = "Atendente responsável; nulo quando o lead está com a IA.") UUID atendenteResponsavelId,
        @Schema(description = "Quantidade consolidada de atendimentos.") int numAtendimentos,
        @Schema(description = "Quantidade consolidada de mensagens.") int numMensagens,
        @Schema(description = "Instante de criação em UTC.") Instant criadoEm,
        @Schema(description = "Instante da última interação; nulo se nunca houve.") Instant ultimaInteracaoEm,
        @Schema(description = "Tags vinculadas ao lead.") List<TagDaLista> tags) {

    static LeadDaLista de(LeadResumo lead, List<Tag> tagsDoLead) {
        return new LeadDaLista(
                lead.id(),
                lead.nome(),
                lead.telefone(),
                lead.empresa(),
                lead.localizacao(),
                lead.statusBasico(),
                lead.etapaAtendimentoId(),
                lead.atendenteResponsavelId(),
                lead.numAtendimentos(),
                lead.numMensagens(),
                lead.criadoEm(),
                lead.ultimaInteracaoEm(),
                tagsDoLead.stream().map(TagDaLista::de).toList());
    }

    /**
     * Tag como aparece dentro da lista de lead. Chave chamada {@code tagId}, e nao {@code id}: este
     * record vive aninhado dentro de {@link LeadDaLista}, e "id" sozinho ficaria ambiguo — de qual
     * dos dois? — no corpo da resposta.
     */
    record TagDaLista(UUID tagId, String nome, String cor, String icone) {
        static TagDaLista de(Tag tag) {
            return new TagDaLista(tag.id(), tag.nome(), tag.cor(), tag.icone());
        }
    }
}
