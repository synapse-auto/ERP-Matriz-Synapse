package com.synapse.crm.campanhas.application;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;

/**
 * O publico de uma campanha a partir da Agenda. A previa devolve so contagens (nunca a lista materializada);
 * so ao iniciar a campanha os destinatarios viram linhas. As exclusoes sao decididas por conjunto, no banco,
 * na ordem de {@link MotivoDoDestinatario}: o primeiro motivo que se aplica e o que vale.
 *
 * <p>Esta e a unica consulta de lead fora da {@code VisibilidadeLeadSpecification}, e e deliberada: campanha
 * atinge a Agenda inteira e so quem enxerga a base inteira (gestao e servico) a executa; a RLS do banco
 * continua valendo e devolve zero linhas para um atendente.
 */
public interface PublicoRepositorio {

    ContagemDoPublico prever(FiltroDePublico filtro, int cooldownProativoHoras, ZoneId fuso);

    /** Os excluidos por um motivo, no maximo {@code limite}, para a pessoa conferir antes de iniciar. */
    List<Excluido> excluidos(
            FiltroDePublico filtro, int cooldownProativoHoras, ZoneId fuso, MotivoDoDestinatario motivo, int limite);

    /**
     * Cria os destinatarios da campanha (PENDENTE ou IGNORADO com motivo) e acerta os contadores dela.
     * Idempotente: quem ja e destinatario nao e duplicado nem reenviado. Devolve quantos entraram agora.
     */
    int materializar(UUID campanhaId, FiltroDePublico filtro, int cooldownProativoHoras, ZoneId fuso);

    /** Os campos do lead que as variaveis enxergam, para o envio de teste e a previa do template. */
    java.util.Optional<com.synapse.crm.campanhas.domain.CampoDoLead.Dados> dadosDoLead(UUID leadId);

    record ContagemDoPublico(long total, long elegiveis, Map<MotivoDoDestinatario, Long> excluidosPorMotivo) {

        public ContagemDoPublico {
            excluidosPorMotivo = Map.copyOf(excluidosPorMotivo);
        }

        public long excluidos() {
            return total - elegiveis;
        }
    }

    record Excluido(UUID leadId, String nome, String telefone, MotivoDoDestinatario motivo) {}
}
