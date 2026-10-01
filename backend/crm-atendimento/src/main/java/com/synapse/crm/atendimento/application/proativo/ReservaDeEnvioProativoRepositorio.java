package com.synapse.crm.atendimento.application.proativo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;

/** Porta de {@code envio_proativo_reserva} (V85). Todo metodo exige transacao ativa. */
public interface ReservaDeEnvioProativoRepositorio {

    /**
     * Serializa as decisoes do mesmo lead ate o fim da transacao. Sem isso, duas reservas de
     * ocorrencias diferentes chegando juntas leriam a mesma contagem e furariam o teto.
     */
    void serializarDecisoesDoLead(UUID leadId);

    Optional<ReservaProativa> porChave(String chave);

    Optional<ReservaProativa> porOcorrencia(UUID leadId, TipoDeOrigem tipo, String regraId, String ocorrencia);

    /** INSERT ... ON CONFLICT DO NOTHING; false = outra transacao ja usou a chave ou a ocorrencia. */
    boolean inserir(ReservaProativa reserva);

    /** Ultimo envio proativo (registrado ou apenas reservado) deste tipo para o lead. */
    Optional<Instant> ultimoEnvioDoTipo(UUID leadId, TipoDeOrigem tipo);

    /** Envios proativos (registrados ou apenas reservados) do lead desde o instante, todos os tipos. */
    int enviosProativosDesde(UUID leadId, Instant desde);

    /** RESERVADO -> ENVIADO. false = a chave nao estava reservada. */
    boolean concluir(String chave, UUID mensagemId, String wamidSaida, Instant agora);

    List<ReservaProativa> pendentesAntesDe(Instant limite, int maximo);

    enum Estado {
        RESERVADO,
        ENVIADO
    }

    record ReservaProativa(
            String chave,
            UUID leadId,
            TipoDeOrigem tipo,
            String regraId,
            String ocorrencia,
            String execucaoId,
            String requisicaoHash,
            Estado estado,
            UUID mensagemId,
            String wamidSaida,
            Instant reservadoEm,
            Instant enviadoEm) {}
}
