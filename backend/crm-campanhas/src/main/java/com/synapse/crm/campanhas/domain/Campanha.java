package com.synapse.crm.campanhas.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Campanha de template em massa. Imutavel: cada acao devolve uma campanha nova, e uma transicao fora da
 * tabela de {@link StatusDaCampanha} lanca em vez de ser ignorada.
 *
 * <p>O contrato comercial vive aqui: o limite diario nunca passa do teto da instancia, so template de
 * marketing ou utilidade entra (autenticacao e codigo de uso unico, nao campanha) e o mapeamento de
 * variaveis cobre exatamente o que o template declara.
 */
public record Campanha(
        UUID id,
        String nome,
        TemplateSnapshot template,
        MapeamentoDeVariaveis mapeamento,
        FiltroDePublico filtro,
        StatusDaCampanha status,
        boolean desligada,
        int limiteDiario,
        JanelaDeEnvio janela,
        int ritmoPorMinuto,
        PlanoDeLimite.Rampa rampa,
        Instant agendadaPara,
        Contadores contadores,
        String motivoDePausa,
        Instant pausadaEm,
        Instant iniciadaEm,
        Instant concluidaEm,
        UUID criadaPor,
        Instant criadaEm) {

    public static final int TAMANHO_MAXIMO_DO_NOME = 150;
    private static final Set<String> CATEGORIAS_ACEITAS = Set.of("MARKETING", "UTILIDADE");

    /** O template no momento da criacao. Editar ou renomear no provedor nao altera uma campanha em curso. */
    public record TemplateSnapshot(String id, String nome, String idioma, String categoria, String corpo, int parametros) {

        public TemplateSnapshot {
            if (nome == null || nome.isBlank() || idioma == null || idioma.isBlank() || corpo == null) {
                throw new CampanhaInvalidaException("template sem nome, idioma ou corpo");
            }
        }
    }

    /** Funil acumulado: lido tambem conta como enviado e entregue. Atualizado de forma incremental. */
    public record Contadores(
            int total,
            int pendentes,
            int enfileirados,
            int enviados,
            int entregues,
            int lidos,
            int respondidos,
            int falhas,
            int ignorados,
            int conferencia) {

        public static Contadores zerados() {
            return new Contadores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public Campanha {
        Objects.requireNonNull(id, "id da campanha");
        Objects.requireNonNull(template, "template da campanha");
        Objects.requireNonNull(mapeamento, "mapeamento de variaveis");
        Objects.requireNonNull(filtro, "filtro de publico");
        Objects.requireNonNull(status, "status da campanha");
        Objects.requireNonNull(janela, "janela de envio");
        Objects.requireNonNull(contadores, "contadores");
        Objects.requireNonNull(criadaEm, "data de criacao");
        if (nome == null || nome.isBlank() || nome.length() > TAMANHO_MAXIMO_DO_NOME) {
            throw new CampanhaInvalidaException("o nome da campanha e obrigatorio e tem ate " + TAMANHO_MAXIMO_DO_NOME + " caracteres");
        }
        if (limiteDiario <= 0) {
            throw new CampanhaInvalidaException("o limite diario precisa ser maior que zero");
        }
        if (ritmoPorMinuto <= 0) {
            throw new CampanhaInvalidaException("o ritmo por minuto precisa ser maior que zero");
        }
        if (rampa != null && rampa.teto() < limiteDiario) {
            throw new CampanhaInvalidaException("o teto da rampa nao pode ser menor que o limite diario");
        }
        mapeamento.validarPara(template.parametros());
    }

    public static Campanha rascunho(
            UUID id,
            String nome,
            TemplateSnapshot template,
            MapeamentoDeVariaveis mapeamento,
            FiltroDePublico filtro,
            int limiteDiario,
            int tetoDaInstancia,
            JanelaDeEnvio janela,
            int ritmoPorMinuto,
            PlanoDeLimite.Rampa rampa,
            Instant agendadaPara,
            UUID criadaPor,
            Instant agora) {
        if (template == null || !CATEGORIAS_ACEITAS.contains(template.categoria())) {
            throw new CampanhaInvalidaException("somente templates de marketing ou utilidade servem para campanha");
        }
        exigirDentroDoTeto(limiteDiario, tetoDaInstancia);
        return new Campanha(
                id,
                nome == null ? null : nome.trim(),
                template,
                mapeamento,
                filtro,
                StatusDaCampanha.RASCUNHO,
                false,
                limiteDiario,
                janela,
                ritmoPorMinuto,
                rampa,
                agendadaPara,
                Contadores.zerados(),
                null,
                null,
                null,
                null,
                criadaPor,
                agora);
    }

    /** Dispara ou agenda. Agendada para o futuro vira AGENDADA; sem data, ou data ja passada, comeca ja. */
    public Campanha iniciar(Instant agora) {
        exigir(status == StatusDaCampanha.RASCUNHO, "iniciar");
        boolean noFuturo = agendadaPara != null && agendadaPara.isAfter(agora);
        return mudar(
                noFuturo ? StatusDaCampanha.AGENDADA : StatusDaCampanha.EM_ANDAMENTO,
                null,
                null,
                noFuturo ? null : agora,
                null);
    }

    /** A hora agendada chegou. */
    public Campanha comecarAgendada(Instant agora) {
        exigir(status == StatusDaCampanha.AGENDADA, "comecar");
        return mudar(StatusDaCampanha.EM_ANDAMENTO, null, null, agora, null);
    }

    public Campanha pausar(Instant agora) {
        exigir(status == StatusDaCampanha.EM_ANDAMENTO || status == StatusDaCampanha.AGENDADA, "pausar");
        return mudar(StatusDaCampanha.PAUSADA, null, agora, iniciadaEm, null);
    }

    public Campanha pausarAutomaticamente(Instant agora, String motivo) {
        exigir(status == StatusDaCampanha.EM_ANDAMENTO, "pausar automaticamente");
        return mudar(StatusDaCampanha.PAUSADA_AUTOMATICAMENTE, motivo, agora, iniciadaEm, null);
    }

    public Campanha retomar(Instant agora) {
        exigir(status.pausada(), "retomar");
        return mudar(StatusDaCampanha.EM_ANDAMENTO, null, null, iniciadaEm == null ? agora : iniciadaEm, null);
    }

    public Campanha cancelar(Instant agora) {
        exigir(status.podeIrPara(StatusDaCampanha.CANCELADA), "cancelar");
        return mudar(StatusDaCampanha.CANCELADA, motivoDePausa, pausadaEm, iniciadaEm, agora);
    }

    public Campanha concluir(Instant agora) {
        exigir(status == StatusDaCampanha.EM_ANDAMENTO, "concluir");
        return mudar(StatusDaCampanha.CONCLUIDA, null, null, iniciadaEm, agora);
    }

    /** Vale a qualquer momento antes de terminar; o proximo ciclo ja usa o novo valor. */
    public Campanha comLimiteDiario(int novoLimite, int tetoDaInstancia) {
        exigir(!status.terminal(), "alterar o limite de");
        exigirDentroDoTeto(novoLimite, tetoDaInstancia);
        return new Campanha(
                id, nome, template, mapeamento, filtro, status, desligada, novoLimite, janela, ritmoPorMinuto,
                rampa, agendadaPara, contadores, motivoDePausa, pausadaEm, iniciadaEm, concluidaEm, criadaPor, criadaEm);
    }

    public Campanha comRitmo(int novoRitmo) {
        exigir(!status.terminal(), "alterar o ritmo de");
        return new Campanha(
                id, nome, template, mapeamento, filtro, status, desligada, limiteDiario, janela, novoRitmo,
                rampa, agendadaPara, contadores, motivoDePausa, pausadaEm, iniciadaEm, concluidaEm, criadaPor, criadaEm);
    }

    /** Interruptor imediato: a campanha para de enviar no proximo ciclo, sem mudar de status. */
    public Campanha comDesligada(boolean valor) {
        exigir(!status.terminal(), "alterar o interruptor de");
        return new Campanha(
                id, nome, template, mapeamento, filtro, status, valor, limiteDiario, janela, ritmoPorMinuto,
                rampa, agendadaPara, contadores, motivoDePausa, pausadaEm, iniciadaEm, concluidaEm, criadaPor, criadaEm);
    }

    public boolean podeEnviarAgora(boolean envioGlobalHabilitado) {
        return envioGlobalHabilitado && !desligada && status.envia();
    }

    private Campanha mudar(
            StatusDaCampanha novoStatus, String motivo, Instant pausada, Instant iniciada, Instant concluida) {
        return new Campanha(
                id, nome, template, mapeamento, filtro, novoStatus, desligada, limiteDiario, janela, ritmoPorMinuto,
                rampa, agendadaPara, contadores, motivo, pausada, iniciada, concluida, criadaPor, criadaEm);
    }

    private void exigir(boolean valido, String acao) {
        if (!valido) {
            throw new TransicaoDeStatusInvalidaException(status, acao);
        }
    }

    private static void exigirDentroDoTeto(int limiteDiario, int tetoDaInstancia) {
        if (limiteDiario <= 0 || limiteDiario > tetoDaInstancia) {
            throw new CampanhaInvalidaException(
                    "o limite diario precisa estar entre 1 e o teto da instancia (" + tetoDaInstancia + ")");
        }
    }
}
