package com.synapse.crm.app.config.auditoria;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.auditoria.LocalizadorDeEstadoAnterior;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.requisicao.RequisicaoContext;

/**
 * Audita metodos {@link Auditable} sem que o caso de uso saiba disso (E09a).
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)} e proposital, nao decorativo.</b> Sem ordem explicita, um
 * {@code @Aspect} comum e {@code @Transactional} competem pela mesma precedencia padrao
 * ({@code Ordered.LOWEST_PRECEDENCE}), e a posicao relativa entre eles fica indefinida — exatamente o
 * tipo de "briga de ordenacao com o interceptador de transacao" que o Javadoc de
 * {@code TransacaoComRlsConfig} descreve (e evita, ali, trocando o gerente de transacao em vez de
 * usar AOP). Aqui, ao contrario, AOP e a ferramenta certa, entao a ordem precisa ser garantida:
 * {@code HIGHEST_PRECEDENCE} coloca este aspecto no lado de fora de tudo (inclusive
 * {@code @Transactional} e {@code @PreAuthorize}, cuja precedencia padrao e sempre menor). Na pratica:
 *
 * <ol>
 *   <li>{@link #auditar} chama {@code proceed()};
 *   <li>{@code proceed()} atravessa o interceptador de seguranca (nega e propaga sem nunca chegar ao
 *       metodo real, se {@code @PreAuthorize} recusar — nada e auditado);
 *   <li>{@code proceed()} atravessa o interceptador de transacao: abre transacao, roda o metodo,
 *       commita;
 *   <li>o controle so volta para {@link #auditar} depois do commit — ou seja, quando registramos a
 *       auditoria, a operacao ja esta persistida de verdade. Nao precisamos de
 *       {@code @TransactionalEventListener(AFTER_COMMIT)} para isso: ja estamos, por construcao,
 *       depois do commit.
 * </ol>
 *
 * <p>Se o metodo anotado lancar (validacao, autorizacao negada, erro de negocio), a excecao propaga
 * direto por {@code proceed()} e o bloco de auditoria nunca roda — nao existe linha de auditoria para
 * uma acao que nao aconteceu.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditoriaAspect {

    private final Map<String, LocalizadorDeEstadoAnterior> localizadores;
    private final UsuarioContext usuarioContext;
    private final RequisicaoContext requisicaoContext;
    private final SerializadorAuditavel serializador;
    private final EscritorDeAuditoria escritor;
    private final Clock relogio;

    public AuditoriaAspect(
            List<LocalizadorDeEstadoAnterior> localizadores,
            UsuarioContext usuarioContext,
            RequisicaoContext requisicaoContext,
            SerializadorAuditavel serializador,
            EscritorDeAuditoria escritor,
            Clock relogio) {
        this.localizadores = localizadores.stream()
                .collect(Collectors.toMap(LocalizadorDeEstadoAnterior::entidadeTipo, l -> l));
        this.usuarioContext = usuarioContext;
        this.requisicaoContext = requisicaoContext;
        this.serializador = serializador;
        this.escritor = escritor;
        this.relogio = relogio;
    }

    /**
     * A anotacao e lida por reflexao sobre a assinatura do join point, nao via bind de parametro do
     * pointcut ({@code @annotation(auditable)} como segundo argumento do advice). O bind de
     * parametro depende de descobrir em runtime o NOME do parametro do metodo de advice — via
     * {@code -parameters} no javac ou leitura de bytecode — e falhou nesta build com
     * {@code IllegalStateException: Required to bind 2 arguments, but only bound 1}, derrubando
     * toda chamada a um metodo {@code @Auditable} com 500. Um unico parametro
     * ({@code ProceedingJoinPoint}) elimina essa classe inteira de problema.
     */
    @Around("@annotation(com.synapse.crm.sharedkernel.auditoria.Auditable)")
    public Object auditar(ProceedingJoinPoint pontoDeJuncao) throws Throwable {
        Auditable auditable = ((MethodSignature) pontoDeJuncao.getSignature())
                .getMethod()
                .getAnnotation(Auditable.class);

        UUID idDoArgumento = extrairPrimeiroUuid(pontoDeJuncao.getArgs());
        if (idDoArgumento == null && "USUARIO".equals(auditable.entidadeTipo())) {
            idDoArgumento = usuarioContext.atualSeHouver().map(UsuarioAutenticado::id).orElse(null);
        }
        Object antes = carregarAntes(auditable.entidadeTipo(), idDoArgumento);

        Object retorno = pontoDeJuncao.proceed();

        registrar(auditable, idDoArgumento, antes, retorno);
        return retorno;
    }

    private Object carregarAntes(String entidadeTipo, UUID id) {
        if (id == null) {
            return null;
        }
        LocalizadorDeEstadoAnterior localizador = localizadores.get(entidadeTipo);
        return localizador == null ? null : localizador.carregar(id).orElse(null);
    }

    private void registrar(Auditable auditable, UUID idDoArgumento, Object antes, Object retornoCru) {
        Object depois = desembrulhar(retornoCru);
        UUID entidadeId = idDoArgumento != null ? idDoArgumento : extrairUuid(depois, "id");
        UUID leadId = extrairUuid(depois != null ? depois : antes, "leadId");

        Optional<UsuarioAutenticado> usuario = usuarioContext.atualSeHouver();
        UUID atorId = usuario.map(UsuarioAutenticado::id).orElse(null);
        String atorTipo = usuario.isPresent() || !ContextoDeServico.ativo() ? "USUARIO" : "SISTEMA";

        RegistroDeAuditoria registro = new RegistroDeAuditoria(
                atorId,
                atorTipo,
                auditable.acao(),
                auditable.entidadeTipo(),
                entidadeId,
                leadId,
                serializador.paraJson(auditable.entidadeTipo(), antes),
                serializador.paraJson(auditable.entidadeTipo(), depois),
                requisicaoContext.ipOrigemSeHouver().orElse(null),
                Instant.now(relogio));

        escritor.registrar(registro);
    }

    /** {@code Optional} vira o valor de dentro; {@code boolean}/{@code void} nao tem "depois". */
    private static Object desembrulhar(Object retorno) {
        if (retorno instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        if (retorno instanceof Boolean) {
            return null;
        }
        return retorno;
    }

    private static UUID extrairPrimeiroUuid(Object[] argumentos) {
        for (Object argumento : argumentos) {
            if (argumento instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    /** Best-effort via reflexao: um acessor ausente ou que falhe nunca derruba a auditoria. */
    private static UUID extrairUuid(Object objeto, String nomeDoAcessor) {
        if (objeto == null) {
            return null;
        }
        try {
            Method acessor = objeto.getClass().getMethod(nomeDoAcessor);
            Object valor = acessor.invoke(objeto);
            return valor instanceof UUID uuid ? uuid : null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }
}
