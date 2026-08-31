package com.synapse.crm.core.application.lead.importacao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.synapse.crm.core.domain.lead.TelefoneCanonico;
import com.synapse.crm.core.domain.lead.TelefoneInvalidoException;

/** Prepara, valida e deduplica o CSV antes de qualquer acesso ao banco. */
public final class PrepararImportacaoLeadsCsv {

    private final TelefoneCanonico telefoneCanonico;

    public PrepararImportacaoLeadsCsv(String ddiPadrao) {
        this.telefoneCanonico = new TelefoneCanonico(ddiPadrao);
    }

    public Resultado executar(Reader origem) throws IOException {
        BufferedReader leitor = origem instanceof BufferedReader buffered
                ? buffered
                : new BufferedReader(origem);
        String cabecalho = leitor.readLine();
        if (cabecalho == null) {
            throw new IllegalArgumentException("CSV vazio");
        }
        cabecalho = removerBom(cabecalho);
        char delimitador = detectarDelimitador(cabecalho);
        List<String> colunas = separar(cabecalho, delimitador);
        Map<String, Integer> indices = indices(colunas);
        Integer indiceNome = indices.get("nome");
        Integer indiceTelefone = indices.get("telefone");
        if (indiceNome == null || indiceTelefone == null) {
            throw new IllegalArgumentException("CSV deve possuir as colunas nome e telefone");
        }

        List<LeadImportavel> aceitos = new ArrayList<>();
        List<LinhaRecusada> recusados = new ArrayList<>();
        Set<String> telefonesDoArquivo = new HashSet<>();
        int numeroDaLinha = 1;
        int linhasComDados = 0;
        String linha;
        while ((linha = leitor.readLine()) != null) {
            numeroDaLinha++;
            if (linha.isBlank()) {
                continue;
            }
            linhasComDados++;
            try {
                List<String> valores = separar(linha, delimitador);
                if (valores.size() != colunas.size()) {
                    throw new LinhaInvalidaException("quantidade de colunas diferente do cabecalho");
                }
                String nome = valores.get(indiceNome).trim();
                String telefoneRecebido = valores.get(indiceTelefone).trim();
                if (nome.isBlank()) {
                    throw new LinhaInvalidaException("nome vazio");
                }
                if (telefoneRecebido.isBlank()) {
                    throw new LinhaInvalidaException("telefone vazio");
                }
                if (telefoneRecebido.codePoints().anyMatch(Character::isLetter)) {
                    throw new LinhaInvalidaException("telefone contem letras");
                }
                String telefone = normalizar(telefoneRecebido);
                if (!telefonesDoArquivo.add(telefone)) {
                    throw new LinhaInvalidaException("telefone duplicado no arquivo");
                }
                aceitos.add(new LeadImportavel(nome, telefone));
            } catch (LinhaInvalidaException e) {
                recusados.add(new LinhaRecusada(numeroDaLinha, e.getMessage()));
            }
        }
        return new Resultado(linhasComDados, List.copyOf(aceitos), List.copyOf(recusados));
    }

    private String normalizar(String telefone) {
        try {
            return telefoneCanonico.normalizar(telefone);
        } catch (TelefoneInvalidoException e) {
            throw new LinhaInvalidaException("telefone curto ou ilegivel");
        }
    }

    private static Map<String, Integer> indices(List<String> colunas) {
        Map<String, Integer> resultado = new HashMap<>();
        for (int i = 0; i < colunas.size(); i++) {
            resultado.put(normalizarCabecalho(colunas.get(i)), i);
        }
        return resultado;
    }

    private static String normalizarCabecalho(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String removerBom(String valor) {
        return valor.startsWith("\uFEFF") ? valor.substring(1) : valor;
    }

    private static char detectarDelimitador(String cabecalho) {
        int virgulas = contarForaDeAspas(cabecalho, ',');
        int pontosEVirgulas = contarForaDeAspas(cabecalho, ';');
        if (virgulas == 0 && pontosEVirgulas == 0) {
            throw new IllegalArgumentException("CSV deve usar virgula ou ponto e virgula como delimitador");
        }
        return pontosEVirgulas > virgulas ? ';' : ',';
    }

    private static int contarForaDeAspas(String linha, char procurado) {
        boolean entreAspas = false;
        int quantidade = 0;
        for (int i = 0; i < linha.length(); i++) {
            char atual = linha.charAt(i);
            if (atual == '"') {
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (!entreAspas && atual == procurado) {
                quantidade++;
            }
        }
        return quantidade;
    }

    private static List<String> separar(String linha, char delimitador) {
        List<String> valores = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char caractere = linha.charAt(i);
            if (caractere == '"') {
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (caractere == delimitador && !entreAspas) {
                valores.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(caractere);
            }
        }
        if (entreAspas) {
            throw new LinhaInvalidaException("aspas nao fechadas");
        }
        valores.add(atual.toString());
        return valores;
    }

    public record LeadImportavel(String nome, String telefone) {}

    public record LinhaRecusada(int linha, String motivo) {}

    public record Resultado(
            int totalDeLinhas, List<LeadImportavel> aceitos, List<LinhaRecusada> recusados) {}

    private static final class LinhaInvalidaException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private LinhaInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
