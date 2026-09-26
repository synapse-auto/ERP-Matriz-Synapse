package com.synapse.crm.equipe.domain.permissao;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Resultado do calculo para um usuario (ou para um perfil sem excecoes). Imutavel e barato de
 * consultar: e o que o cache guarda e o que {@code @capacidades.permite(...)} le a cada chamada.
 */
public record PermissoesEfetivas(
        PapelUsuario papel,
        Map<Modulo, NivelDeAcesso> niveis,
        Map<Capacidade, EstadoDaCapacidade> estados) {

    public PermissoesEfetivas {
        Objects.requireNonNull(papel, "papel obrigatorio");
        niveis = Collections.unmodifiableMap(new EnumMap<>(niveis));
        estados = Collections.unmodifiableMap(new EnumMap<>(estados));
    }

    public boolean permite(Capacidade capacidade) {
        EstadoDaCapacidade estado = estados.get(capacidade);
        return estado != null && estado.permitido();
    }

    public EstadoDaCapacidade estado(Capacidade capacidade) {
        return estados.get(capacidade);
    }

    public NivelDeAcesso nivel(Modulo modulo) {
        return niveis.getOrDefault(modulo, NivelDeAcesso.SEM_ACESSO);
    }

    /** Acoes configuraveis (nao estruturais) disponiveis e no teto do papel: o "M" de "N de M". */
    public long totalConfiguravel() {
        return estados.values().stream()
                .filter(e -> !e.capacidade().estrutural())
                .filter(e -> e.motivo() != EstadoDaCapacidade.Motivo.TETO_DO_PAPEL)
                .filter(e -> e.motivo() != EstadoDaCapacidade.Motivo.FLAG_DESLIGADA)
                .count();
    }

    public long totalPermitido() {
        return estados.values().stream()
                .filter(e -> !e.capacidade().estrutural())
                .filter(EstadoDaCapacidade::permitido)
                .count();
    }
}
