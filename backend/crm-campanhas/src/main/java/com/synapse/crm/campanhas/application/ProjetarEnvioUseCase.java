package com.synapse.crm.campanhas.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.campanhas.application.PublicoRepositorio.ContagemDoPublico;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.JanelaDeEnvio;
import com.synapse.crm.campanhas.domain.PlanoDeLimite;

/** Estimativa de termino e mini-calendario do passo "Ritmo e agenda": quantas mensagens saem por dia. */
@Service
public class ProjetarEnvioUseCase {

    private final PreverPublicoUseCase previa;
    private final ConfiguracaoDeCampanhas configuracao;
    private final DisponibilidadeDeCampanhas disponibilidade;
    private final TransacoesDeCampanha transacoes;
    private final Clock relogio;
    private final ZoneId fuso;

    public ProjetarEnvioUseCase(
            PreverPublicoUseCase previa,
            ConfiguracaoDeCampanhas configuracao,
            DisponibilidadeDeCampanhas disponibilidade,
            TransacoesDeCampanha transacoes,
            Clock relogio,
            ZoneId fuso) {
        this.previa = previa;
        this.configuracao = configuracao;
        this.disponibilidade = disponibilidade;
        this.transacoes = transacoes;
        this.relogio = relogio;
        this.fuso = fuso;
    }

    /** @param primeiroDia nulo = hoje, no fuso da instancia */
    public record Entrada(
            FiltroDePublico filtro,
            int limiteDiario,
            JanelaDeEnvio janela,
            int ritmoPorMinuto,
            PlanoDeLimite.Rampa rampa,
            LocalDate primeiroDia) {}

    public record Saida(
            long destinatarios,
            PlanoDeLimite.Projecao projecao,
            int tetoDaInstancia,
            int limiteMetaInformado,
            int limiteEfetivoNoPrimeiroDia) {}

    @PreAuthorize(PermissoesDeCampanha.LEITURA_DE_DESTINATARIOS)
    public Saida executar(Entrada entrada) {
        disponibilidade.exigir();
        ContagemDoPublico contagem = previa.executar(entrada.filtro());
        ConfiguracaoDeCampanhas.Parametros parametros = transacoes.noChatSomenteLeitura(configuracao::atuais);
        LocalDate inicio = entrada.primeiroDia() != null
                ? entrada.primeiroDia()
                : LocalDate.ofInstant(Instant.now(relogio), fuso);
        int teto = parametros.tetoDiarioDaInstancia();
        PlanoDeLimite.Projecao projecao = PlanoDeLimite.projetar(
                (int) Math.min(Integer.MAX_VALUE, contagem.elegiveis()),
                inicio,
                inicio,
                entrada.janela(),
                entrada.limiteDiario(),
                entrada.rampa(),
                entrada.ritmoPorMinuto(),
                teto);
        int primeiroLimite = PlanoDeLimite.limiteDoDia(entrada.limiteDiario(), entrada.rampa(), 0, teto);
        return new Saida(contagem.elegiveis(), projecao, teto, parametros.limiteMetaInformado(), primeiroLimite);
    }
}
