package com.synapse.crm.equipe.domain.permissao;

import java.util.Arrays;
import java.util.Optional;

/**
 * Modulos do CRM que tem acoes configuraveis em Gestao.
 *
 * <p>So entra aqui o que esta implementado e disponivel na primeira entrega (docs/47). Banco de
 * Arquivos, Campanhas, Relatorios, importacao/exportacao e sub-abas futuras ficam fora de proposito:
 * permissao para algo que nao existe e controle fantasma.
 *
 * <p>{@code nivelMinimoPermitido} existe para modulos cuja leitura e estrutural (Atendimentos,
 * Contatos, Tags): "Sem acesso" nao e oferecido porque a visibilidade vem de RN-CRM-01, da
 * Specification e da RLS, nao deste catalogo — oferecer o botao seria prometer um corte que o
 * sistema nao faz.
 */
public enum Modulo {
    ATENDIMENTOS("atendimentos", NivelDeAcesso.VER, null),
    CONTATOS("contatos", NivelDeAcesso.VER, null),
    TAGS("tags", NivelDeAcesso.VER, null),
    MENSAGENS_RAPIDAS("mensagens_rapidas", NivelDeAcesso.SEM_ACESSO, null),
    TEMPLATES("templates", NivelDeAcesso.SEM_ACESSO, null),
    RESUMO_IA("resumo_ia", NivelDeAcesso.SEM_ACESSO, null),
    DASHBOARD("dashboard", NivelDeAcesso.SEM_ACESSO, "dashboard"),
    MENSAGENS_PROGRAMADAS("mensagens_programadas", NivelDeAcesso.SEM_ACESSO, null),
    LEMBRETES("lembretes", NivelDeAcesso.SEM_ACESSO, null),
    AUTOMACAO("automacao", NivelDeAcesso.SEM_ACESSO, null),
    EQUIPE("equipe", NivelDeAcesso.SEM_ACESSO, null);

    private final String id;
    private final NivelDeAcesso nivelMinimoPermitido;
    private final String flag;

    Modulo(String id, NivelDeAcesso nivelMinimoPermitido, String flag) {
        this.id = id;
        this.nivelMinimoPermitido = nivelMinimoPermitido;
        this.flag = flag;
    }

    public String id() {
        return id;
    }

    public NivelDeAcesso nivelMinimoPermitido() {
        return nivelMinimoPermitido;
    }

    /** Feature flag que liga o modulo; {@code null} = modulo central, sempre disponivel. */
    public String flag() {
        return flag;
    }

    public static Optional<Modulo> porId(String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }
}
