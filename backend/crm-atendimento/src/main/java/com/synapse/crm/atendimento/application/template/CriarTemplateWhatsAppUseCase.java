package com.synapse.crm.atendimento.application.template;

import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.PedidoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

/** Submete um template de texto ao provedor ativo — fora do caminho de mensagem. */
@Service
public class CriarTemplateWhatsAppUseCase {

    private static final Pattern NOME_META = Pattern.compile("[a-z0-9_]{1,512}");
    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{(\\d+)\\}\\}");
    private static final Pattern IDIOMA = Pattern.compile("[a-z]{2}(_[A-Z]{2})?");

    private final CanalGateway canal;

    public CriarTemplateWhatsAppUseCase(CanalGateway canal) {
        this.canal = canal;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    public TemplateDoCanal executar(
            String nome, String idioma, TemplateDoCanal.Categoria categoria, String corpo) {
        PedidoDeTemplate pedido = validar(nome, idioma, categoria, corpo);
        ResultadoDeTemplate resultado = canal.criarTemplate(pedido);
        if (resultado instanceof ResultadoDeTemplate.Aceito aceito) {
            return aceito.template();
        }
        if (resultado instanceof ResultadoDeTemplate.Recusado recusado) {
            throw new CanalRecusouTemplateException(recusado.motivo());
        }
        throw new CanalRecusouTemplateException("provedor nao devolveu resultado de criacao");
    }

    static PedidoDeTemplate validar(
            String nome, String idioma, TemplateDoCanal.Categoria categoria, String corpo) {
        if (nome == null || nome.isBlank()) {
            throw new PedidoDeTemplateInvalidoException("template exige um nome");
        }
        String nomeNormalizado = nome.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!NOME_META.matcher(nomeNormalizado).matches()) {
            throw new PedidoDeTemplateInvalidoException(
                    "nome do template aceita so letras minusculas, numeros e _");
        }
        if (corpo == null || corpo.isBlank()) {
            throw new PedidoDeTemplateInvalidoException("template exige um corpo de texto");
        }
        String idiomaNormalizado =
                (idioma == null || idioma.isBlank()) ? "pt_BR" : idioma.trim();
        if (!IDIOMA.matcher(idiomaNormalizado).matches()) {
            throw new PedidoDeTemplateInvalidoException("idioma do template invalido");
        }
        if (categoria == null || categoria == TemplateDoCanal.Categoria.AUTENTICACAO) {
            throw new PedidoDeTemplateInvalidoException(
                    "categoria deve ser UTILIDADE ou MARKETING");
        }
        exigirParametrosSequenciais(corpo.trim());
        return new PedidoDeTemplate(nomeNormalizado, idiomaNormalizado, categoria, corpo.trim());
    }

    private static void exigirParametrosSequenciais(String corpo) {
        Matcher casamento = VARIAVEL.matcher(corpo);
        TreeSet<Integer> indices = new TreeSet<>();
        while (casamento.find()) {
            indices.add(Integer.parseInt(casamento.group(1)));
        }
        int esperado = 1;
        for (int indice : indices) {
            if (indice != esperado) {
                throw new PedidoDeTemplateInvalidoException(
                        "variaveis do corpo precisam ser sequenciais a partir de {{1}}");
            }
            esperado++;
        }
    }
}
