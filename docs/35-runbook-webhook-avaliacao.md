# Avaliação conduzida pela Automação após encerramento

## Regra aprovada e sequência

Desde a E124, a finalização de atendimento **não cria automaticamente** uma solicitação de
pesquisa. O n8n decide quando iniciar o fluxo e pode usar o contrato interno existente;
o responsável continua sendo o do atendimento encerrado, nunca o gestor que clicou.
Finalizar todos (mesmo um item), atendimento sem responsável, transferência, devolução à IA,
saída e chat interno não iniciam pesquisa por conta própria. A coleta existente continua na
escala 1–5 e aceita uma nota por atendimento.

n8n inicia o fluxo autorizado → workflow envia/coleta pesquisa → POST interno de avaliação.

O worker e a outbox continuam sendo a infraestrutura para solicitações já enfileiradas e para
um eventual disparo explícito futuro; a finalização humana não é mais produtora dessa fila.

Solicitações já enfileiradas antes da E124 permanecem na outbox e seguem a política de retry;
o deploy não limpa nem fabrica novas solicitações a partir de finalizações antigas.

O CRM não envia uma segunda mensagem de pesquisa pelo canal. Opt-in, janela, template
WhatsApp e o envio efetivo são responsabilidade do workflow de Dylan.

## Contratos e identidade

Saída: POST para a URL configurada, Content-Type application/json. O nome do header
secreto vem de AUTOMACAO_AVALIACAO_AUTH_HEADER (destinatário atual exige
crm-synapse-marc-auth); o valor vem somente de AUTOMACAO_AVALIACAO_TOKEN.
Não é X-Hub-Signature-256 (repasse cru) nem X-Synapse-Token (entrada interna).
Redirecionamentos não são seguidos.

| Campo JSON | Fonte |
|---|---|
| modo | INICIAR_AVALIACAO |
| status_finalizacao | FINALIZADO |
| atendimento_id | UUID encerrado, capturado no backend |
| lead_id | Lead do atendimento |
| atendente_id | Responsável do atendimento no encerramento |
| wa_id | Telefone persistido, DDI/dígitos, sem + |

Esses seis campos são o corpo inteiro. Não há token no corpo/outbox. O retry utiliza
o mesmo snapshot; não consulta o dono atual do lead. A validação de destino exige
10–15 dígitos ASCII, primeiro não zero; não completa nem inventa DDI.

Retorno já existente: POST /internal/v1/atendimentos/{id}/avaliacao, header
X-Synapse-Token com o segredo interno próprio e corpo {"nota":5,"comentario":"opcional"}.
JWT humano não abre esse endpoint. Duplicata é 409; nota fora de 1–5 é 400.
Sem responsável, a coleta continua recusando a avaliação.

## Configuração / ação necessária no Dokploy

Nada é obrigatório para iniciar o CRM. Sem URL, token ou header válidos, o fluxo explícito
da Automação não cria intenção/HTTP. Finalizações humanas continuam funcionando sem chamada
externa. Não há chamadas com token vazio.
Todas as variáveis abaixo têm defaults opcionais no stack; nenhuma usa :?obrigatoria.
Valores já estão em .env.example, application.yml e docker/dokploy-stack.yml.

| Variável | Default | Uso |
|---|---|---|
| AUTOMACAO_AVALIACAO_URL | vazio | URL completa acordada; exemplo https://automacao.example.test/webhook/avaliacao |
| AUTOMACAO_AVALIACAO_TOKEN | vazio | Segredo privado do destinatário; fornecer pelo ambiente seguro |
| AUTOMACAO_AVALIACAO_AUTH_HEADER | vazio | crm-synapse-marc-auth no contrato atual; configurável por filho |
| AUTOMACAO_AVALIACAO_TIMEOUT | 5s | Limite total do HTTP, inclusive leitura/descartamento da resposta |
| AUTOMACAO_AVALIACAO_RESERVA_EXPIRACAO | 30s | Lease; deve exceder o timeout |
| AUTOMACAO_AVALIACAO_LOTE | 10 | Máximo de tarefas oferecidas por tick |
| AUTOMACAO_AVALIACAO_CONCORRENCIA | 2 | Workers exclusivos |
| AUTOMACAO_AVALIACAO_FILA | 10 | Capacidade da fila local, sem CallerRuns |
| AUTOMACAO_AVALIACAO_MAXIMO_TENTATIVAS | 5 | Limite durável, contado ao reservar |
| AUTOMACAO_AVALIACAO_BACKOFF_INICIAL | 10s | Espera após primeira falha |
| AUTOMACAO_AVALIACAO_BACKOFF_MAXIMO | 30m | Teto do backoff exponencial |
| AUTOMACAO_AVALIACAO_MINIMO_CHAMADAS_CIRCUITO | 5 | Amostras da janela do circuit breaker próprio |
| AUTOMACAO_AVALIACAO_ESPERA_CIRCUITO | 30s | Espera com circuito aberto |
| AUTOMACAO_AVALIACAO_INTERVALO_MS | 1000 | Tick do scheduler (respeita synapse.agendamento.habilitado) |

Limites numéricos/durações inválidos são recusados na configuração (não aumentá-los
para esconder instabilidade). URL/token/header ausentes ou inválidos desligam somente
esta integração; o diagnóstico de finalização não contém os valores.
Não usar NEXT_PUBLIC, catálogo, banco ou logs para guardar o segredo.

## Atomicidade, concorrência e entrega

- Finalização e transferência travam primeiro o lead (`FOR UPDATE`), depois o
  atendimento com `FOR NO KEY UPDATE`. Essa ordem é a do envio manual. O modo
  no atendimento serializa escritores do agregado (status, atendente_id,
  finalizado_em — nenhuma é a PK referenciada) e coexiste com o `KEY SHARE` que
  o INSERT de `mensagem` (e as FKs de idempotência da automação) mantém enquanto
  a transação da mensagem ainda não deu commit. `FOR UPDATE` no atendimento
  fechava ciclo com o `UPDATE` posterior do lead: a IT
  `recebimentoPausadoAntesDoContador_eFinalizacaoNaoEntramEmDeadlock` reproduziu
  `40P01` nesse interleaving, com schema/RLS reais do CRM. O diagnóstico JDBC
  anterior em schema reduzido só ilustrava o ciclo; não substitui essa IT.
  O upsert em `salvar` continua recusando cópia antiga (`WHERE status <> 'FINALIZADO'`).
- Atendimento finalizado não pode ser sobrescrito pelo upsert de uma cópia antiga.
  Enviar depois do encerramento abre outro atendimento; não muda o responsável da pesquisa antiga.
- Finalização e intenção usam a mesma conexão/transação do chat. Erro de persistência
  reverte ambos; n8n indisponível não participa dessa transação.
- A intenção usa outbox_evento, tipo automacao.avaliacao.iniciar e UUID determinístico
  derivado de tipo + atendimento_id. Novo atendimento do mesmo lead tem outra chave.
- V44 adiciona apenas avaliacao_reserva_id e índice parcial da fila nova; não altera
  políticas RLS, migrações antigas nem os tipos/limites de mensagem e repasse cru.
- A fila local é limitada. O worker só reserva **quando começa**, nunca enquanto espera
  na fila. Reserva e resultado são transações curtas; rede sem conexão de banco retida.
- Reserva incrementa tentativas e persiste UUID próprio. Expiração recupera órfãos;
  UUID e prazo precisam continuar válidos para aceitar resultado. Resposta velha não
  sobrescreve tentativa nova. Morte repetida também chega ao limite e fica inspecionável.

Entrega é **pelo menos uma vez dentro da política de tentativas**, não exatamente uma vez.
O n8n pode aceitar o POST e a resposta se perder. Antes de ativar, Dylan deve confirmar
deduplicação **persistente por modo + atendimento_id antes de enviar a pesquisa**.
O CRM não prova que o workflow faz isso. Não foi acordado header adicional de idempotência.

## Evidência e diagnóstico (somente leitura)

Um HTTP 2xx significa recebido pelo n8n, não WhatsApp enviado nem nota coletada.
Os logs usam evento/atendimento, status e classe de falha; não imprimem telefone,
payload, token ou resposta bruta. Relacione evento e atendimento pela consulta abaixo.

```sql
SELECT id AS evento_id, payload->>'atendimento_id' AS atendimento_id,
       criado_em, publicado_em, tentativas, proxima_tentativa_em,
       avaliacao_reserva_id, esgotado_em, ultimo_erro
  FROM outbox_evento
 WHERE tipo = 'automacao.avaliacao.iniciar'
 ORDER BY criado_em DESC;
```

| Estado | Evidência / ação |
|---|---|
| Não enfileirado | Lote não gera intenção; individual sem dono/telefone/configuração gera diagnóstico sem PII |
| Pendente/reservado | publicado_em e esgotado_em nulos; proxima_tentativa_em controla lease/backoff |
| Recebido pelo n8n | publicado_em preenchido após 2xx; conferir execução do workflow pelo atendimento_id |
| Recuperável | TIMEOUT, FALHA_TRANSPORTE, INTERROMPIDO, CIRCUITO_ABERTO, HTTP_408/429/5xx |
| Permanente | Outros HTTP (inclui 3xx/401/403/422), PAYLOAD_INVALIDO, CONFIGURACAO_INVALIDA |
| Esgotado | esgotado_em preenchido; não some nem vira publicado; LIMITE_APOS_RESERVA_EXPIRADA inclui morte do worker |
| Coletado | Linha em avaliacao para atendimento_id; nota e responsável conforme contrato existente |

Não exportar payload/telefone para logs ou relatórios operacionais. Erro permanente
demanda corrigir credencial/contrato/configuração; retry automático não o cura.

## Ativar, pausar, retomar e rotacionar

1. Obter autorização para deploy e teste real; esta etapa não a concede.
2. Dylan confirma contrato, segredo e deduplicação persistente. Recomenda-se rotacionar
   a credencial anteriormente compartilhada no chat, coordenando os dois lados. Não foi rotacionada aqui.
3. Configurar as três variáveis de destino/segredo/header; ajustar limites apenas se necessário.
4. Em ambiente controlado, encerrar individualmente um atendimento de teste com
   responsável/contato autorizados. Conferir intenção, depois 2xx, depois execução no
   workflow, envio WhatsApp e retorno interno. São quatro evidências distintas.
5. Pausa: remover a URL e reiniciar/deployar coordenadamente os workers. Reservas em voo
   podem terminar até o shutdown; depois nenhuma pendência é marcada como entregue por estar pausada.
6. Antes de reativar, inspecionar pendências por atendimento/evento e obter autorização
   para qualquer backlog anterior. Reconfigurar a URL torna pendências elegíveis novamente:
   **reativação não é autorização implícita para disparar backlog**.
7. Rotação: pausar, coordenar o segredo novo no destinatário e no ambiente do CRM,
   confirmar deduplicação, então retomar somente o conjunto autorizado.

O deploy não reconstrói finalizações antigas e não consulta audit_log. Eventos não
criados quando desligado não são fabricados depois. Para reprocessar esgotados, primeiro
confirmar no workflow se já houve envio: timeout não prova ausência de efeito. A seleção
de UUIDs, remoção de esgotamento e novo orçamento de tentativas são operações manuais,
transacionais e **dependem de autorização própria**; não há botão/API nem script de replay em massa.
Preservar id e payload originais e invalidar qualquer reserva antiga. Nunca apagar evento,
forjar publicado_em ou reatribuir dono para esconder a falha.

O repasse cru legado ainda executa HTTP dentro da transação; foi preservado e não é
modelo para o novo publisher. docs/13 permanece defasado em E58 e não prova ausência
de funcionalidades posteriores.
