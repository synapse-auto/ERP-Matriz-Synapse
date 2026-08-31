package com.synapse.crm.core.domain.lead;

import java.util.regex.Pattern;

/**
 * Telefone no formato persistido pelo CRM: somente digitos, incluindo o codigo do pais.
 *
 * <p>A normalizacao mora no dominio para que entradas da tela, webhook e futuras importacoes usem
 * exatamente a mesma regra. O DDI padrao e configuracao da instancia, recebida no construtor: o
 * dominio nao conhece ambiente, Spring nem o cliente que esta usando a Base PAI.
 *
 * <p>A regra do nono digito e a mesma implementada em SQL por {@code app_telefone_canonico}, criada
 * na V50. Duas implementacoes existem porque o caminho critico normaliza em Java, sem ida ao banco,
 * e a migration normaliza antes de a aplicacao subir. O que impede as duas de divergirem e o teste
 * de paridade {@code TelefoneCanonicoParidadeIT}, que roda a mesma tabela de casos nas duas.
 */
public final class TelefoneCanonico {

    private static final Pattern NAO_DIGITO_ASCII = Pattern.compile("[^0-9]");
    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("[0-9]{1,3}");

    /**
     * A regra do nono digito e do Brasil, nao da Estrutural: vale para qualquer filho cujo numero
     * chegue com DDI 55, e nao vale para nenhum outro pais. Por isso ela olha o DDI do <b>numero</b>,
     * nunca o DDI padrao da instancia — um filho brasileiro pode ter contato portugues na base, e
     * esse contato nao pode ganhar digito nenhum.
     */
    private static final String DDI_BRASIL = "55";

    /** {@code 55} + DDD (2) + assinante (8): o formato que a Meta entrega sem o nono digito. */
    private static final int DIGITOS_SEM_O_NONO = 12;

    /** Indice do primeiro digito do assinante em {@code 55DDNNNNNNNN}. */
    private static final int INICIO_DO_ASSINANTE = 4;

    /**
     * Celular brasileiro comeca em 9 desde 2016; antes disso comecava em 6, 7, 8 ou 9. Fixo nunca
     * comeca em 6, 7, 8 ou 9 — e o que torna a inferencia segura.
     */
    private static final char PRIMEIRO_DIGITO_DE_CELULAR = '6';

    private static final char ULTIMO_DIGITO_DE_CELULAR = '9';

    private final String ddiPadrao;

    public TelefoneCanonico(String ddiPadrao) {
        if (ddiPadrao == null || !SOMENTE_DIGITOS.matcher(ddiPadrao).matches()) {
            throw new IllegalArgumentException("DDI padrao deve conter de um a tres digitos");
        }
        this.ddiPadrao = ddiPadrao;
    }

    /** Retorna {@code null} somente quando o telefone esta ausente. */
    public String normalizar(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = NAO_DIGITO_ASCII.matcher(telefone).replaceAll("");
        return comNonoDigito(comDdi(digitos));
    }

    private String comDdi(String digitos) {
        return switch (digitos.length()) {
            case 10, 11 -> ddiPadrao + digitos;
            default -> {
                if (digitos.length() < 10) {
                    throw new TelefoneInvalidoException();
                }
                yield digitos;
            }
        };
    }

    /**
     * Devolve o celular brasileiro com nono digito; qualquer outro numero passa intacto.
     *
     * <p>A Meta entrega o {@code wa_id} de boa parte dos numeros brasileiros sem o nono digito,
     * enquanto o cadastro manual usa o formato de discagem. Sem esta etapa os dois formatos sao
     * clientes diferentes para o indice unico, e o mesmo cliente vira dois cadastros com historicos
     * separados.
     *
     * <p>Nao adivinhar e parte da regra: assinante de 8 digitos comecando em 2, 3, 4 ou 5 e fixo e
     * fica como esta; qualquer outro tamanho fica como esta; e assinante comecando em 0 ou 1 nao
     * existe no plano brasileiro — aqui ele passa intacto, e na migration ele <b>aborta</b>, porque
     * la mexer no numero errado e irreversivel.
     */
    private static String comNonoDigito(String comDdi) {
        if (comDdi.length() != DIGITOS_SEM_O_NONO || !comDdi.startsWith(DDI_BRASIL)) {
            return comDdi;
        }
        char inicioDoAssinante = comDdi.charAt(INICIO_DO_ASSINANTE);
        if (inicioDoAssinante < PRIMEIRO_DIGITO_DE_CELULAR
                || inicioDoAssinante > ULTIMO_DIGITO_DE_CELULAR) {
            return comDdi;
        }
        return comDdi.substring(0, INICIO_DO_ASSINANTE)
                + ULTIMO_DIGITO_DE_CELULAR
                + comDdi.substring(INICIO_DO_ASSINANTE);
    }
}
