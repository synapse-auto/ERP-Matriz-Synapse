package com.synapse.crm.equipe.domain.permissao;

import static com.synapse.crm.sharedkernel.identidade.PapelUsuario.ADMINISTRADOR;
import static com.synapse.crm.sharedkernel.identidade.PapelUsuario.ATENDENTE;
import static com.synapse.crm.sharedkernel.identidade.PapelUsuario.GESTOR;
import static com.synapse.crm.sharedkernel.identidade.PapelUsuario.OPERADOR;
import static com.synapse.crm.sharedkernel.identidade.PapelUsuario.SUBGESTOR;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Catalogo de capacidades configuraveis em Gestao — uma constante por acao que ja existe, com
 * ponto de entrada e caso de uso reais (inventario em docs/47).
 *
 * <p>O identificador e estavel e e o que se grava no banco, trafega na API e aparece em
 * {@code @PreAuthorize}. Renomear um identificador exige migration de dados; nunca troque o texto.
 *
 * <p><b>Teto</b> e o conjunto de papeis que ja podia executar a acao antes desta etapa — copia fiel
 * do {@code hasAnyRole} de cada caso de uso. A configuracao de Gestao so restringe dentro do teto;
 * ampliar alem dele exige decisao de produto, nao um interruptor. <b>Negada por padrao</b> marca as
 * delegacoes novas ao SUBGESTOR: existem no teto dele, mas nascem desligadas ate um GESTOR ou
 * ADMINISTRADOR conceder.
 *
 * <p><b>Delegavel</b> e o que um SUBGESTOR com {@link #EQUIPE_EXCECOES_ATENDENTES} pode ajustar nas
 * excecoes de um ATENDENTE, ou com {@link #EQUIPE_PERFIS} no perfil ATENDENTE — e so ate o que ele
 * mesmo tem.
 */
public enum Capacidade {

    // --- Atendimentos -----------------------------------------------------------------------
    /** Recorte de conversas. Estrutural: vem de RN-CRM-01/Specification/RLS, nunca do catalogo. */
    ATENDIMENTOS_VER("atendimentos.ver", Modulo.ATENDIMENTOS, NivelDeAcesso.VER, Tipo.ALCANCE_ESTRUTURAL,
            false, todos(), nenhum(), false),
    ATENDIMENTOS_RESPONDER("atendimentos.responder", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true),
    ATENDIMENTOS_INICIAR_CONVERSA("atendimentos.iniciar_conversa", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR,
            Tipo.ACAO, false, todos(), nenhum(), true, "atendimentos.responder"),
    ATENDIMENTOS_ABRIR_PARA_CONTATO("atendimentos.abrir_para_contato", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR,
            Tipo.ACAO, false, todos(), nenhum(), true),
    ATENDIMENTOS_TRANSFERIR("atendimentos.transferir", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), EnumSet.of(OPERADOR), true),
    ATENDIMENTOS_RECEBER_DE_ATENDENTE("atendimentos.receber_de_atendente", Modulo.ATENDIMENTOS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, EnumSet.of(OPERADOR), EnumSet.of(OPERADOR), false,
            "atendimentos.responder"),
    ATENDIMENTOS_RECEBER_DE_OPERADOR("atendimentos.receber_de_operador", Modulo.ATENDIMENTOS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, EnumSet.of(OPERADOR), EnumSet.of(OPERADOR), false,
            "atendimentos.responder"),
    ATENDIMENTOS_RECEBER_DE_SUBGESTOR("atendimentos.receber_de_subgestor", Modulo.ATENDIMENTOS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, EnumSet.of(OPERADOR), EnumSet.of(OPERADOR), false,
            "atendimentos.responder"),
    ATENDIMENTOS_RECEBER_DE_GESTOR("atendimentos.receber_de_gestor", Modulo.ATENDIMENTOS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, EnumSet.of(OPERADOR), EnumSet.of(OPERADOR), false,
            "atendimentos.responder"),
    ATENDIMENTOS_RECEBER_DE_ADMINISTRADOR("atendimentos.receber_de_administrador", Modulo.ATENDIMENTOS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, EnumSet.of(OPERADOR), EnumSet.of(OPERADOR), false,
            "atendimentos.responder"),
    ATENDIMENTOS_DEVOLVER_IA("atendimentos.devolver_ia", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true),
    ATENDIMENTOS_FINALIZAR("atendimentos.finalizar", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true),
    ATENDIMENTOS_COLABORAR("atendimentos.colaborar", Modulo.ATENDIMENTOS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true),
    ATENDIMENTOS_FINALIZAR_LOTE("atendimentos.finalizar_lote", Modulo.ATENDIMENTOS, NivelDeAcesso.GERENCIAR,
            Tipo.ACAO, true, todos(), nenhum(), false, "atendimentos.finalizar"),

    // --- Contatos ---------------------------------------------------------------------------
    CONTATOS_EDITAR("contatos.editar", Modulo.CONTATOS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true),

    // --- Tags -------------------------------------------------------------------------------
    TAGS_APLICAR("tags.aplicar", Modulo.TAGS, NivelDeAcesso.EDITAR, Tipo.ACAO, false, todos(), nenhum(), true),
    TAGS_CRIAR("tags.criar", Modulo.TAGS, NivelDeAcesso.GERENCIAR, Tipo.ACAO, false, gestao(), nenhum(), false),
    TAGS_EDITAR_EXCLUIR("tags.editar_excluir", Modulo.TAGS, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), nenhum(), false),

    // --- Mensagens rapidas ------------------------------------------------------------------
    MENSAGENS_RAPIDAS_USAR("mensagens_rapidas.usar", Modulo.MENSAGENS_RAPIDAS, NivelDeAcesso.VER, Tipo.ACAO,
            false, todos(), nenhum(), true),
    MENSAGENS_RAPIDAS_CRIAR("mensagens_rapidas.criar", Modulo.MENSAGENS_RAPIDAS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true, "mensagens_rapidas.usar"),
    MENSAGENS_RAPIDAS_EDITAR_EXCLUIR("mensagens_rapidas.editar_excluir", Modulo.MENSAGENS_RAPIDAS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, todos(), nenhum(), true, "mensagens_rapidas.usar"),

    // --- Templates do WhatsApp --------------------------------------------------------------
    TEMPLATES_VER("templates.ver", Modulo.TEMPLATES, NivelDeAcesso.VER, Tipo.ACAO, false, todos(), nenhum(), true),
    TEMPLATES_CRIAR("templates.criar", Modulo.TEMPLATES, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true, "templates.ver"),
    TEMPLATES_EDITAR("templates.editar", Modulo.TEMPLATES, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            false, gestao(), nenhum(), false, "templates.ver"),
    TEMPLATES_EXCLUIR("templates.excluir", Modulo.TEMPLATES, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), nenhum(), false, "templates.ver"),

    // --- Resumo por IA ----------------------------------------------------------------------
    RESUMO_IA_VER("resumo_ia.ver", Modulo.RESUMO_IA, NivelDeAcesso.VER, Tipo.ACAO, false, todos(), nenhum(), true),
    RESUMO_IA_SOLICITAR("resumo_ia.solicitar", Modulo.RESUMO_IA, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true, "resumo_ia.ver"),

    // --- Dashboard (visao unica consolidada) ------------------------------------------------
    DASHBOARD_VER("dashboard.ver", Modulo.DASHBOARD, NivelDeAcesso.VER, Tipo.ACAO, false, gestao(), nenhum(), false),

    // --- Mensagens programadas --------------------------------------------------------------
    MENSAGENS_PROGRAMADAS_VER("mensagens_programadas.ver", Modulo.MENSAGENS_PROGRAMADAS, NivelDeAcesso.VER,
            Tipo.ACAO, false, todos(), nenhum(), true),
    MENSAGENS_PROGRAMADAS_CRIAR("mensagens_programadas.criar", Modulo.MENSAGENS_PROGRAMADAS, NivelDeAcesso.EDITAR,
            Tipo.ACAO, false, todos(), nenhum(), true, "mensagens_programadas.ver"),
    MENSAGENS_PROGRAMADAS_EDITAR_CANCELAR("mensagens_programadas.editar_cancelar", Modulo.MENSAGENS_PROGRAMADAS,
            NivelDeAcesso.EDITAR, Tipo.ACAO, false, todos(), nenhum(), true, "mensagens_programadas.ver"),

    // --- Lembretes --------------------------------------------------------------------------
    LEMBRETES_VER("lembretes.ver", Modulo.LEMBRETES, NivelDeAcesso.VER, Tipo.ACAO, false, todos(), nenhum(), true),
    LEMBRETES_CRIAR("lembretes.criar", Modulo.LEMBRETES, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true, "lembretes.ver"),
    LEMBRETES_EDITAR_EXCLUIR("lembretes.editar_excluir", Modulo.LEMBRETES, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, todos(), nenhum(), true, "lembretes.ver"),

    // --- Automacao --------------------------------------------------------------------------
    AUTOMACAO_VER("automacao.ver", Modulo.AUTOMACAO, NivelDeAcesso.VER, Tipo.ACAO, false, gestao(), nenhum(), false),
    AUTOMACAO_EDITAR_PARAMETROS("automacao.editar_parametros", Modulo.AUTOMACAO, NivelDeAcesso.GERENCIAR,
            Tipo.ACAO, true, gestao(), nenhum(), false, "automacao.ver"),
    AUTOMACAO_REGRAS("automacao.regras", Modulo.AUTOMACAO, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            false, gestao(), nenhum(), false, "automacao.ver"),

    // --- Campanhas -------------------------------------------------------------------------
    CAMPANHAS_VER("campanhas.ver", Modulo.CAMPANHAS, NivelDeAcesso.VER, Tipo.ACAO,
            false, EnumSet.of(OPERADOR, SUBGESTOR, GESTOR, ADMINISTRADOR), EnumSet.of(OPERADOR), false),
    CAMPANHAS_VER_DESTINATARIOS("campanhas.ver_destinatarios", Modulo.CAMPANHAS, NivelDeAcesso.VER, Tipo.ACAO,
            false, gestao(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_REGISTRAR_OPT_OUT("campanhas.registrar_opt_out", Modulo.CAMPANHAS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, gestao(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_CRIAR("campanhas.criar", Modulo.CAMPANHAS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, superioresCampanhas(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_EDITAR("campanhas.editar", Modulo.CAMPANHAS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, superioresCampanhas(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_TESTAR("campanhas.testar", Modulo.CAMPANHAS, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, superioresCampanhas(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_OPERAR("campanhas.operar", Modulo.CAMPANHAS, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, superioresCampanhas(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_CONFERIR("campanhas.conferir", Modulo.CAMPANHAS, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            false, superioresCampanhas(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_CONFIGURAR("campanhas.configurar", Modulo.CAMPANHAS, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, superioresCampanhas(), nenhum(), false, "campanhas.ver"),
    CAMPANHAS_OPT_OUT("campanhas.opt_out", Modulo.CAMPANHAS, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, superioresCampanhas(), nenhum(), false, "campanhas.ver"),

    // --- Gestao da equipe -------------------------------------------------------------------
    EQUIPE_VER("equipe.ver", Modulo.EQUIPE, NivelDeAcesso.VER, Tipo.ACAO, false, gestao(), nenhum(), false),
    EQUIPE_DISPONIBILIDADE_IA("equipe.disponibilidade_ia", Modulo.EQUIPE, NivelDeAcesso.EDITAR, Tipo.ACAO,
            false, gestao(), nenhum(), false, "equipe.ver"),
    EQUIPE_CRIAR("equipe.criar", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), EnumSet.of(SUBGESTOR), false, "equipe.ver"),
    EQUIPE_EDITAR("equipe.editar", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            false, gestao(), EnumSet.of(SUBGESTOR), false, "equipe.ver"),
    EQUIPE_ALTERAR_PAPEL("equipe.alterar_papel", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, superiores(), nenhum(), false, "equipe.editar"),
    EQUIPE_SENHA_PROVISORIA("equipe.senha_provisoria", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), EnumSet.of(SUBGESTOR), false, "equipe.ver"),
    EQUIPE_DESATIVAR("equipe.desativar", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), EnumSet.of(SUBGESTOR), false, "equipe.ver"),
    EQUIPE_EXCECOES_ATENDENTES("equipe.excecoes_atendentes", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), EnumSet.of(SUBGESTOR), false, "equipe.ver"),
    EQUIPE_PERFIS("equipe.perfis", Modulo.EQUIPE, NivelDeAcesso.GERENCIAR, Tipo.ACAO,
            true, gestao(), EnumSet.of(SUBGESTOR), false, "equipe.ver");

    /** Acao liga/desliga, ou recorte estrutural apenas exibido. */
    public enum Tipo {
        ACAO,
        ALCANCE_ESTRUTURAL
    }

    /** Recorte estrutural exibido para {@link Tipo#ALCANCE_ESTRUTURAL}. Nao existe "Equipe". */
    public enum Alcance {
        MEUS,
        TODOS
    }

    private final String id;
    private final Modulo modulo;
    private final NivelDeAcesso nivelMinimo;
    private final Tipo tipo;
    private final boolean sensivel;
    private final Set<PapelUsuario> teto;
    private final Set<PapelUsuario> negadaPorPadraoPara;
    private final boolean delegavel;
    private final List<String> dependencias;

    Capacidade(String id, Modulo modulo, NivelDeAcesso nivelMinimo, Tipo tipo, boolean sensivel,
            Set<PapelUsuario> teto, Set<PapelUsuario> negadaPorPadraoPara, boolean delegavel,
            String... dependencias) {
        this.id = id;
        this.modulo = modulo;
        this.nivelMinimo = nivelMinimo;
        this.tipo = tipo;
        this.sensivel = sensivel;
        this.teto = Set.copyOf(teto);
        this.negadaPorPadraoPara = Set.copyOf(negadaPorPadraoPara);
        this.delegavel = delegavel;
        this.dependencias = List.of(dependencias);
    }

    public String id() {
        return id;
    }

    public Modulo modulo() {
        return modulo;
    }

    public NivelDeAcesso nivelMinimo() {
        return nivelMinimo;
    }

    public Tipo tipo() {
        return tipo;
    }

    public boolean estrutural() {
        return tipo == Tipo.ALCANCE_ESTRUTURAL;
    }

    public boolean sensivel() {
        return sensivel;
    }

    public Set<PapelUsuario> teto() {
        return teto;
    }

    public boolean noTetoDe(PapelUsuario papel) {
        return teto.contains(papel);
    }

    public boolean permitidaPorPadraoPara(PapelUsuario papel) {
        return noTetoDe(papel) && !negadaPorPadraoPara.contains(papel);
    }

    public boolean delegavel() {
        return delegavel;
    }

    public List<Capacidade> dependencias() {
        return dependencias.stream().map(d -> porId(d).orElseThrow()).toList();
    }

    /** So para {@link Tipo#ALCANCE_ESTRUTURAL}: atendente fica em Meus (RN-CRM-01), gestao em Todos. */
    public Alcance alcancePara(PapelUsuario papel) {
        return papel.enxergaTodosOsLeads() ? Alcance.TODOS : Alcance.MEUS;
    }

    public static Optional<Capacidade> porId(String id) {
        return Arrays.stream(values()).filter(c -> c.id.equals(id)).findFirst();
    }

    private static Set<PapelUsuario> todos() {
        // Teto historico explicito: um papel futuro nao herda capacidades apenas por entrar no enum.
        return EnumSet.of(ATENDENTE, OPERADOR, SUBGESTOR, GESTOR, ADMINISTRADOR);
    }

    private static Set<PapelUsuario> gestao() {
        return EnumSet.of(SUBGESTOR, GESTOR, ADMINISTRADOR);
    }

    private static Set<PapelUsuario> superiores() {
        return EnumSet.of(GESTOR, ADMINISTRADOR);
    }

    private static Set<PapelUsuario> superioresCampanhas() {
        return EnumSet.of(ADMINISTRADOR);
    }

    private static Set<PapelUsuario> nenhum() {
        return EnumSet.noneOf(PapelUsuario.class);
    }

    static {
        // Garante no carregamento da classe que ATENDENTE nunca recebe alcance alem de Meus e que
        // dependencia sempre aponta para constante declarada antes (o calculo percorre em ordem).
        for (Capacidade c : values()) {
            for (String dependencia : c.dependencias) {
                Capacidade alvo = porId(dependencia)
                        .orElseThrow(() -> new IllegalStateException("dependencia desconhecida: " + dependencia));
                if (alvo.ordinal() >= c.ordinal()) {
                    throw new IllegalStateException(c.id + " depende de " + dependencia + " declarada depois");
                }
            }
        }
        if (ATENDIMENTOS_VER.alcancePara(ATENDENTE) != Alcance.MEUS) {
            throw new IllegalStateException("ATENDENTE precisa ficar em Meus (RN-CRM-01)");
        }
    }
}
