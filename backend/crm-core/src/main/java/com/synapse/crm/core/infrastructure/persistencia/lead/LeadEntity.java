package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/**
 * Mapeamento JPA da tabela {@code lead}.
 *
 * <p>Deliberadamente fora do dominio: o dominio nao conhece {@code jakarta.persistence}, e o teste
 * de arquitetura reprova se conhecer. Pacote-privada — so o adaptador deste pacote a toca.
 */
@Entity
@Table(name = "lead")
class LeadEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(name = "telefone")
    private String telefone;

    @Column(name = "email")
    private String email;

    @Column(name = "cpf")
    private String cpf;

    @Column(name = "empresa")
    private String empresa;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "localizacao")
    private String localizacao;

    @Column(name = "canal_origem_id")
    private UUID canalOrigemId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status_basico", nullable = false)
    private StatusBasicoLead statusBasico;

    @Column(name = "etapa_atendimento_id")
    private UUID etapaAtendimentoId;

    @Column(name = "atendente_responsavel_id")
    private UUID atendenteResponsavelId;

    @Column(name = "notas")
    private String notas;

    @Column(name = "resumo_ia")
    private String resumoIa;

    @Column(name = "num_atendimentos", nullable = false)
    private int numAtendimentos;

    @Column(name = "num_mensagens", nullable = false)
    private int numMensagens;

    @Column(name = "criado_em", nullable = false, insertable = false, updatable = false)
    private Instant criadoEm;

    /**
     * Denormalizado, como os contadores: quem registra a interacao escreve aqui na mesma transacao.
     *
     * <p>Nao aparece em {@link #paraDominio()} nem em {@link #aplicar(Lead)} justamente por isso —
     * edicao de ficha nao pode sobrescrever, com um valor lido da tela, o instante que outra
     * transacao acabou de gravar. So o filtro {@code semRetornoDias} o le, pela Specification.
     */
    @Column(name = "ultima_interacao_em")
    private Instant ultimaInteracaoEm;

    /** Mapeado via {@code FormatMapper} do Hibernate 6 (Jackson no classpath) — sem JSON manual aqui. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_customizados", nullable = false)
    private Map<String, Object> dadosCustomizados = new LinkedHashMap<>();

    protected LeadEntity() {
        // exigido pelo JPA
    }

    Lead paraDominio() {
        return new Lead(
                id, nome, fotoUrl, telefone, email, cpf, empresa, codigo, localizacao, canalOrigemId,
                statusBasico, etapaAtendimentoId, atendenteResponsavelId, notas, resumoIa,
                numAtendimentos, numMensagens, criadoEm, dadosCustomizados);
    }

    /**
     * Copia os campos editaveis do dominio.
     *
     * <p>Contadores ficam de fora de proposito: eles so mudam por incremento relativo, na transacao
     * que cria o atendimento ou a mensagem. Deixa-los aqui abriria caminho para uma edicao de ficha
     * sobrescrever, com um valor velho lido da tela, o que outra transacao acabou de somar.
     */
    void aplicar(Lead lead) {
        this.nome = lead.nome();
        this.fotoUrl = lead.fotoUrl();
        this.telefone = lead.telefone();
        this.email = lead.email();
        this.cpf = lead.cpf();
        this.empresa = lead.empresa();
        this.codigo = lead.codigo();
        this.localizacao = lead.localizacao();
        this.canalOrigemId = lead.canalOrigemId();
        this.statusBasico = lead.statusBasico();
        this.etapaAtendimentoId = lead.etapaAtendimentoId();
        this.atendenteResponsavelId = lead.atendenteResponsavelId();
        this.notas = lead.notas();
        this.resumoIa = lead.resumoIa();
        this.dadosCustomizados = new LinkedHashMap<>(lead.dadosCustomizados());
    }

    /** Nomes de atributo usados pela Specification e pela projecao de listagem. */
    static final class Campos {
        static final String ID = "id";
        static final String NOME = "nome";
        static final String TELEFONE = "telefone";
        static final String EMAIL = "email";
        static final String CPF = "cpf";
        static final String EMPRESA = "empresa";
        static final String CODIGO = "codigo";
        static final String LOCALIZACAO = "localizacao";
        static final String CANAL_ORIGEM_ID = "canalOrigemId";
        static final String STATUS_BASICO = "statusBasico";
        static final String ETAPA = "etapaAtendimentoId";
        static final String ATENDENTE_RESPONSAVEL_ID = "atendenteResponsavelId";
        static final String NUM_ATENDIMENTOS = "numAtendimentos";
        static final String NUM_MENSAGENS = "numMensagens";
        static final String CRIADO_EM = "criadoEm";
        static final String ULTIMA_INTERACAO_EM = "ultimaInteracaoEm";
        static final String DADOS_CUSTOMIZADOS = "dadosCustomizados";

        private Campos() {}
    }
}
