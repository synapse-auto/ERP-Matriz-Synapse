package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.MudancaDePresenca;
import com.synapse.crm.equipe.domain.usuario.OrigemDaPresenca;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;

/**
 * Unico ponto que muda {@code usuario.status_presenca}: grava o estado, a linha de historico e o log na mesma
 * transacao, para nao existir mudanca sem rastro (o 409 de 05/10 nao pode ser provado depois do fato, docs/62).
 *
 * <p>Nao e caso de uso exposto: a autorizacao fica em quem o chama (o proprio usuario, ou o sistema com papel
 * SERVICO). Exige transacao do chamador ({@code MANDATORY}) para o estado e o historico nunca se separarem.
 */
@Component
public class RegistradorDePresenca {

    /** Marcador de busca no log; so ids e estados, nunca nome, e-mail ou telefone. */
    static final String MARCADOR = "[PRESENCA_ALTERADA]";

    private static final Logger log = LoggerFactory.getLogger(RegistradorDePresenca.class);

    private final EquipeRepositorio equipe;

    RegistradorDePresenca(EquipeRepositorio equipe) {
        this.equipe = equipe;
    }

    /** @return vazio se o usuario nao existe ou esta inativo; senao a mudanca (que pode nao ter mudado nada). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MudancaDePresenca> registrar(
            UUID usuarioId, StatusPresenca novo, OrigemDaPresenca origem, String motivo) {
        Optional<MudancaDePresenca> mudanca = equipe.registrarPresenca(usuarioId, novo, origem, motivo);
        mudanca.filter(MudancaDePresenca::mudou)
                .ifPresent(m -> log.info(
                        "{} usuarioId={} de={} para={} origem={} motivo={}",
                        MARCADOR,
                        usuarioId,
                        m.anterior(),
                        m.novo(),
                        origem,
                        motivo == null ? "-" : motivo));
        return mudanca;
    }
}
