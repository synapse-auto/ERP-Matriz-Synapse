package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * API da finalizacao em massa: o ponto de entrada real (HTTP, seguranca, validacao, RLS, idempotencia). O que o
 * worker faz depois esta em {@link FinalizacaoEmMassaWorkerIT}.
 */
class FinalizacaoEmMassaApiIT extends FinalizacaoEmMassaITBase {

    // --- escopo e periodo -------------------------------------------------------------------------------

    @Test
    void umAtendenteEUmPeriodoFinalizaSoOsDeleSemApagarNada() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        UUID a1 = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        UUID a2 = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(11, 0)));
        UUID deNayara = atendimentoAberto(nayara, noDia(hoje(), LocalTime.of(10, 0)));
        int leadsAntes = leadsDeTeste();
        int atendimentosAntes = atendimentosDeTeste();
        String token = tokenGestor();

        ResponseEntity<String> previa = previa(token, pedido(List.of(clayton), hoje(), hoje()));
        assertThat(previa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ler(previa).path("total").asLong()).isEqualTo(2);

        ResponseEntity<String> criada = criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova());
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode corpo = ler(criada);
        assertThat(corpo.path("status").asText()).isEqualTo("PENDENTE");
        assertThat(corpo.path("encontrados").asInt()).isEqualTo(2);
        assertThat(corpo.path("finalizados").asInt()).isZero();
        // Assincrono de verdade: a resposta volta antes de qualquer atendimento ser finalizado.
        assertThat(statusDoAtendimento(a1)).isEqualTo("EM_ATENDIMENTO");

        UUID operacao = idDaOperacao(criada);
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(a1)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(a2)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(deNayara)).isEqualTo("EM_ATENDIMENTO");
        assertThat(leadsDeTeste()).isEqualTo(leadsAntes);
        assertThat(atendimentosDeTeste()).isEqualTo(atendimentosAntes);
        JsonNode status = ler(consultar(token, operacao));
        assertThat(status.path("status").asText()).isEqualTo("CONCLUIDA");
        assertThat(status.path("finalizados").asInt()).isEqualTo(2);
        assertThat(status.path("percentual").asInt()).isEqualTo(100);
    }

    @Test
    void variosAtendentesEVariosDiasAlcancamSoOQueCaiNoPeriodo() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        LocalDate de = hoje().minusDays(2);
        UUID a = atendimentoAberto(clayton, noDia(de, LocalTime.of(10, 0)));
        UUID b = atendimentoAberto(nayara, noDia(hoje().minusDays(1), LocalTime.of(15, 0)));
        UUID c = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(9, 0)));
        UUID antes = atendimentoAberto(nayara, noDia(de.minusDays(1), LocalTime.of(23, 0)));
        String token = tokenGestor();

        JsonNode previa = ler(previa(token, pedido(List.of(clayton, nayara), de, hoje())));
        assertThat(previa.path("total").asLong()).isEqualTo(3);
        assertThat(previa.path("porAtendente")).hasSize(2);

        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton, nayara), de, hoje()), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(List.of(statusDoAtendimento(a), statusDoAtendimento(b), statusDoAtendimento(c)))
                .containsOnly("FINALIZADO");
        assertThat(statusDoAtendimento(antes)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void dataInicialEFinalSaoInclusivasComOFuso() {
        UUID clayton = atendente("Clayton");
        LocalDate dia = hoje().minusDays(1);
        UUID noPrimeiroInstante = atendimentoAberto(clayton, noDia(dia, LocalTime.MIDNIGHT));
        UUID noUltimoInstante = atendimentoAberto(clayton, noDia(dia.plusDays(1), LocalTime.MIDNIGHT).minusMillis(1));
        UUID noInstanteSeguinte = atendimentoAberto(clayton, noDia(dia.plusDays(1), LocalTime.MIDNIGHT));
        UUID umInstanteAntes = atendimentoAberto(clayton, noDia(dia, LocalTime.MIDNIGHT).minusMillis(1));
        String token = tokenGestor();

        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), dia, dia), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(noPrimeiroInstante)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(noUltimoInstante)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(noInstanteSeguinte)).isEqualTo("EM_ATENDIMENTO");
        assertThat(statusDoAtendimento(umInstanteAntes)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void horarioFinalEInclusivoNoMinutoEOInicialTambem() {
        UUID clayton = atendente("Clayton");
        LocalDate dia = hoje().minusDays(1);
        UUID cedo = atendimentoAberto(clayton, noDia(dia, LocalTime.of(8, 59, 59)));
        UUID noInicio = atendimentoAberto(clayton, noDia(dia, LocalTime.of(9, 0)));
        UUID noMinutoFinal = atendimentoAberto(clayton, noDia(dia, LocalTime.of(18, 0, 59)));
        UUID tarde = atendimentoAberto(clayton, noDia(dia, LocalTime.of(18, 1)));
        String token = tokenGestor();

        UUID operacao = idDaOperacao(
                criar(token, pedido(List.of(clayton), dia, dia, LocalTime.of(9, 0), LocalTime.of(18, 0)), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(noInicio)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(noMinutoFinal)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(cedo)).isEqualTo("EM_ATENDIMENTO");
        assertThat(statusDoAtendimento(tarde)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void nenhumAtendimentoEncontradoMostraZeroNaPreviaERecusaOPedido() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        String token = tokenGestor();

        JsonNode previa = ler(previa(token, pedido(List.of(clayton, nayara), hoje(), hoje())));
        assertThat(previa.path("total").asLong()).isZero();
        assertThat(previa.path("porAtendente")).hasSize(2);

        ResponseEntity<String> recusada = criar(token, pedido(List.of(clayton, nayara), hoje(), hoje()), chaveNova());
        assertThat(recusada.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(recusada)).isEqualTo("SEM_ATENDIMENTOS");
        assertThat(contarOperacoes()).isZero();
    }

    @Test
    void atendenteSemResultadoApareceComZeroENaoEntraNaOperacao() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        UUID doClayton = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();

        JsonNode previa = ler(previa(token, pedido(List.of(clayton, nayara), hoje(), hoje())));
        Map<String, Long> porNome = new LinkedHashMap<>();
        previa.path("porAtendente").forEach(n -> porNome.put(n.path("nome").asText(), n.path("quantidade").asLong()));
        assertThat(porNome).containsEntry(PREFIXO + "Clayton", 1L).containsEntry(PREFIXO + "Nayara", 0L);

        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton, nayara), hoje(), hoje()), chaveNova()));
        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT atendente_id) FROM finalizacao_em_massa_item WHERE operacao_id = ?",
                        Integer.class,
                        operacao))
                .isEqualTo(1);
        assertThat(statusDoAtendimento(doClayton)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void atendimentoJaFinalizadoNaoEntraNaOperacao() {
        UUID clayton = atendente("Clayton");
        atendimento(clayton, "FINALIZADO", noDia(hoje(), LocalTime.of(10, 0)));
        UUID aberto = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(11, 0)));
        String token = tokenGestor();

        assertThat(ler(previa(token, pedido(List.of(clayton), hoje(), hoje()))).path("total").asLong()).isEqualTo(1);
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(aberto)).isEqualTo("FINALIZADO");
        assertThat(ler(consultar(token, operacao)).path("encontrados").asInt()).isEqualTo(1);
    }

    // --- visibilidade, permissao e payload -------------------------------------------------------------

    @Test
    void quemNaoEnxergaTodosSoPodeEscolherASiMesmo() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        UUID doClayton = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        UUID daNayara = atendimentoAberto(nayara, noDia(hoje(), LocalTime.of(10, 0)));
        String tokenClayton = tokenDe(clayton);

        ResponseEntity<String> previaDoOutro = previa(tokenClayton, pedido(List.of(nayara), hoje(), hoje()));
        assertThat(previaDoOutro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codigo(previaDoOutro)).isEqualTo("ATENDENTE_FORA_DO_ESCOPO");
        ResponseEntity<String> criadaDoOutro =
                criar(tokenClayton, pedido(List.of(clayton, nayara), hoje(), hoje()), chaveNova());
        assertThat(criadaDoOutro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(contarOperacoes()).isZero();

        UUID propria = idDaOperacao(criar(tokenClayton, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(propria);
        assertThat(statusDoAtendimento(doClayton)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(daNayara)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void operacaoDeUmUsuarioNaoApareceParaOutroSemVisaoTotal() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        UUID operacao = idDaOperacao(criar(tokenDe(clayton), pedido(List.of(clayton), hoje(), hoje()), chaveNova()));

        ResponseEntity<String> daNayara = consultar(tokenDe(nayara), operacao);
        assertThat(daNayara.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(codigo(daNayara)).isEqualTo("OPERACAO_NAO_ENCONTRADA");
        assertThat(consultar(tokenGestor(), operacao).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consultar(tokenDe(clayton), operacao).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void semAPermissaoTodosOsPontosDeEntradaRecusam() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String tokenGestor = tokenGestor();
        String tokenClayton = tokenDe(clayton);
        UUID existente = idDaOperacao(criar(tokenClayton, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(existente);

        revogarFinalizarEmLoteDoPerfilAtendente(tokenGestor);

        assertThat(previa(tokenClayton, pedido(List.of(clayton), hoje(), hoje())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(criar(tokenClayton, pedido(List.of(clayton), hoje(), hoje()), chaveNova()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(consultar(tokenClayton, existente).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenClayton, HttpMethod.GET, BASE + "/" + existente + "/itens", null, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenClayton, HttpMethod.GET, BASE, null, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Quem tem a permissao continua passando: o bloqueio e por capacidade, nao um defeito geral.
        assertThat(previa(tokenGestor, pedido(List.of(clayton), hoje(), hoje())).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void semAutenticacaoEhRecusado() {
        ResponseEntity<String> resposta =
                http.postForEntity(BASE + "/previa", pedido(List.of(UUID.randomUUID()), hoje(), hoje()), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void payloadAdulteradoEhRevalidadoNoBackend() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();

        ResponseEntity<String> inexistente =
                criar(token, pedido(List.of(clayton, UUID.randomUUID()), hoje(), hoje()), chaveNova());
        assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(inexistente)).isEqualTo("ATENDENTE_INEXISTENTE");

        ResponseEntity<String> invertido =
                criar(token, pedido(List.of(clayton), hoje(), hoje().minusDays(1)), chaveNova());
        assertThat(invertido.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(invertido)).isEqualTo("PERIODO_INVERTIDO");

        ResponseEntity<String> amplo = criar(token, pedido(List.of(clayton), hoje().minusDays(40), hoje()), chaveNova());
        assertThat(amplo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(amplo)).isEqualTo("PERIODO_EXCEDE_MAXIMO");

        ResponseEntity<String> horarioInvertido = criar(
                token, pedido(List.of(clayton), hoje(), hoje(), LocalTime.of(18, 0), LocalTime.of(9, 0)), chaveNova());
        assertThat(codigo(horarioInvertido)).isEqualTo("PERIODO_INVERTIDO");

        assertThat(criar(token, pedido(List.of(), hoje(), hoje()), chaveNova()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(criar(token, pedido(List.of(clayton), null, hoje()), chaveNova()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> semChave = criar(token, pedido(List.of(clayton), hoje(), hoje()), null);
        assertThat(semChave.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(semChave)).isEqualTo("CHAVE_DE_IDEMPOTENCIA_INVALIDA");
        assertThat(contarOperacoes()).isZero();
    }

    @Test
    void limiteDeAtendimentosPorOperacaoVemDaConfiguracao() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(11, 0)));
        definirParametro("atendimento.finalizacao_em_massa.limite_por_operacao", 1);
        String token = tokenGestor();

        JsonNode previa = ler(previa(token, pedido(List.of(clayton), hoje(), hoje())));
        assertThat(previa.path("excedeLimite").asBoolean()).isTrue();
        assertThat(previa.path("limite").asInt()).isEqualTo(1);

        ResponseEntity<String> recusada = criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova());
        assertThat(recusada.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(recusada)).isEqualTo("LIMITE_EXCEDIDO");
        assertThat(contarOperacoes()).isZero();
    }

    // --- idempotencia e conflito -----------------------------------------------------------------------

    @Test
    void repetirOPedidoDevolveAMesmaOperacaoSemCriarOutraNemAuditarDeNovo() {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        String chave = chaveNova();

        ResponseEntity<String> primeira = criar(token, pedido(List.of(clayton), hoje(), hoje()), chave);
        ResponseEntity<String> segunda = criar(token, pedido(List.of(clayton), hoje(), hoje()), chave);

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idDaOperacao(segunda)).isEqualTo(idDaOperacao(primeira));
        assertThat(ler(segunda).path("repetida").asBoolean()).isTrue();
        assertThat(contarOperacoes()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE entidade_tipo = 'FINALIZACAO_EM_MASSA'"
                                + " AND acao = 'FINALIZAR_ATENDIMENTOS_EM_MASSA'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void mesmaChaveComOutrosFiltrosEhConflito() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        atendimentoAberto(nayara, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        String chave = chaveNova();
        criar(token, pedido(List.of(clayton), hoje(), hoje()), chave);

        ResponseEntity<String> outroFiltro = criar(token, pedido(List.of(clayton, nayara), hoje(), hoje()), chave);

        assertThat(outroFiltro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(outroFiltro)).isEqualTo("CHAVE_DE_IDEMPOTENCIA_REUTILIZADA");
        assertThat(contarOperacoes()).isEqualTo(1);
    }

    @Test
    void ordemEDuplicidadeDosAtendentesNaoMudamAImpressaoDoPedido() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        atendimentoAberto(nayara, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        String chave = chaveNova();

        ResponseEntity<String> a = criar(token, pedido(List.of(clayton, nayara), hoje(), hoje()), chave);
        ResponseEntity<String> b = criar(token, pedido(List.of(nayara, clayton, nayara), hoje(), hoje()), chave);

        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(idDaOperacao(b)).isEqualTo(idDaOperacao(a));
    }

    @Test
    void duasChamadasSimultaneasComAMesmaChaveViramUmaOperacao() throws Exception {
        UUID clayton = atendente("Clayton");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        String chave = chaveNova();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<String>> chamada = () -> {
                largada.await();
                return criar(token, pedido(List.of(clayton), hoje(), hoje()), chave);
            };
            Future<ResponseEntity<String>> um = pool.submit(chamada);
            Future<ResponseEntity<String>> dois = pool.submit(chamada);
            largada.countDown();

            ResponseEntity<String> r1 = um.get();
            ResponseEntity<String> r2 = dois.get();

            assertThat(List.of(r1.getStatusCode(), r2.getStatusCode()))
                    .allMatch(s -> s == HttpStatus.ACCEPTED || s == HttpStatus.OK);
            assertThat(idDaOperacao(r1)).isEqualTo(idDaOperacao(r2));
            assertThat(contarOperacoes()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void soUmaOperacaoAtivaPorVezEAoConcluirALiberaOutra() {
        UUID clayton = atendente("Clayton");
        UUID nayara = atendente("Nayara");
        atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        atendimentoAberto(nayara, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID primeira = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));

        ResponseEntity<String> segunda = criar(token, pedido(List.of(nayara), hoje(), hoje()), chaveNova());
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codigo(segunda)).isEqualTo("OPERACAO_EM_ANDAMENTO");

        processarAteConcluir(primeira);
        ResponseEntity<String> terceira = criar(token, pedido(List.of(nayara), hoje(), hoje()), chaveNova());
        assertThat(terceira.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void operacoesRecentesEResultadoDetalhadoFicamDisponiveisDepoisDeFecharAJanela() {
        UUID clayton = atendente("Clayton");
        UUID a = atendimentoAberto(clayton, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(clayton), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);

        JsonNode recentes = ler(chamar(token, HttpMethod.GET, BASE, null, null));
        assertThat(recentes.get(0).path("id").asText()).isEqualTo(operacao.toString());
        assertThat(recentes.get(0).path("status").asText()).isEqualTo("CONCLUIDA");

        JsonNode itens =
                ler(chamar(token, HttpMethod.GET, BASE + "/" + operacao + "/itens?status=FINALIZADO", null, null));
        assertThat(itens.path("itens")).hasSize(1);
        assertThat(itens.path("itens").get(0).path("atendimentoId").asText()).isEqualTo(a.toString());
        assertThat(itens.path("itens").get(0).path("atendenteNome").asText()).isEqualTo(PREFIXO + "Clayton");
        assertThat(ler(chamar(token, HttpMethod.GET, BASE + "/" + operacao + "/itens?status=FALHA", null, null))
                        .path("itens"))
                .isEmpty();
    }

    // --- apoio ------------------------------------------------------------------------------------------

    private int contarOperacoes() {
        return jdbc.queryForObject("SELECT count(*) FROM finalizacao_em_massa", Integer.class);
    }

    private int leadsDeTeste() {
        return jdbc.queryForObject("SELECT count(*) FROM lead WHERE nome LIKE ?", Integer.class, PREFIXO + "%");
    }

    private int atendimentosDeTeste() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                Integer.class,
                PREFIXO + "%");
    }

    private void revogarFinalizarEmLoteDoPerfilAtendente(String tokenGestor) {
        long revisao = jdbc.queryForObject(
                "SELECT revisao FROM permissao_perfil WHERE papel = CAST('ATENDENTE' AS papel_usuario)", Long.class);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("revisaoEsperada", revisao);
        corpo.put("niveis", Map.of());
        corpo.put("acoes", Map.of("atendimentos.finalizar_lote", false));
        ResponseEntity<String> r =
                chamar(tokenGestor, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE", corpo, null);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }
}
