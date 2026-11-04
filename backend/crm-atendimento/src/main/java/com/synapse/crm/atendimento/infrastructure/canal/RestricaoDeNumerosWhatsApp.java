package com.synapse.crm.atendimento.infrastructure.canal;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.synapse.crm.core.domain.lead.TelefoneCanonico;

/** Restrição opcional, por instância, aplicada nos limites comuns de entrada e saída do WhatsApp. */
@Component
public final class RestricaoDeNumerosWhatsApp {
    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("[0-9]{10,15}");
    private final boolean habilitada;
    private final boolean configuracaoValida;
    private final Set<String> permitidos;
    private final TelefoneCanonico normalizador;

    public RestricaoDeNumerosWhatsApp(
            @Value("${synapse.canal.whatsapp.restricao-numeros-habilitada:false}") boolean habilitada,
            @Value("${synapse.canal.whatsapp.numeros-permitidos:}") String numeros,
            TelefoneCanonico normalizador) {
        this.habilitada = habilitada;
        this.normalizador = normalizador;
        var parseado = parsear(numeros, normalizador);
        this.configuracaoValida = parseado.valido();
        this.permitidos = parseado.numeros();
    }

    public boolean habilitada() { return habilitada; }

    /** Lista inválida/vazia, JID, grupo ou identidade sem telefone comprovado bloqueia. */
    public boolean permite(String telefone) {
        if (!habilitada) return true;
        if (!configuracaoValida || telefone == null || !SOMENTE_DIGITOS.matcher(telefone).matches()) return false;
        try { return permitidos.contains(normalizador.normalizar(telefone)); }
        catch (RuntimeException invalido) { return false; }
    }

    static Resultado parsear(String texto, TelefoneCanonico normalizador) {
        if (texto == null || texto.isBlank()) return new Resultado(false, Set.of());
        var valores = Arrays.stream(texto.split(",", -1)).map(String::trim).toList();
        if (valores.isEmpty() || valores.stream().anyMatch(v -> !SOMENTE_DIGITOS.matcher(v).matches()))
            return new Resultado(false, Set.of());
        try {
            Set<String> canonicos = valores.stream().map(normalizador::normalizar).collect(Collectors.toUnmodifiableSet());
            return new Resultado(!canonicos.isEmpty(), canonicos);
        } catch (RuntimeException invalido) { return new Resultado(false, Set.of()); }
    }

    record Resultado(boolean valido, Set<String> numeros) {}
}
