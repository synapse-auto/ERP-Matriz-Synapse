# Capacidades WhatsApp no CRM: lacunas e plano de implementação

**Estado:** Fase 1 (itens 1 a 4) implementada na branch `feat/whatsapp-contato-compartilhado` (V81), coberta por testes e **não verificada em instância real**; Fase 0 e Fases 2–4 seguem como plano. **Base:** auditoria estática recebida em 23/09/2026, conferida pontualmente com o código de `origin/main`. Não houve consulta às instâncias, aos bancos, aos logs de produção ou aos workflows do n8n. Este documento não certifica comportamento em produção.

## Fase 1 — comportamento implementado

**Contato compartilhado.** Os tradutores Meta e Uzapi/Autotic leem `messages[].contacts[]` e gravam
uma mensagem `CONTATO` (novo valor de `tipo_mensagem`, V81) com `conteudo` nulo e
`midia_metadados = {"contatos":[{"nome":"...","telefones":[{"numero":"...","waId":"...","tipo":"..."}]}]}`.
`nome`, `waId` e `tipo` só aparecem quando o provedor os envia (a Uzapi não envia `waId`). Vários
contatos e vários números no mesmo cartão são preservados na ordem; contato sem telefone fica com
`telefones: []`; cartão sem nome e sem telefone não traz nada utilizável e vira descarte
`CONTEUDO_INVALIDO`. O `value.contacts[]` do envelope segue servindo só para o nome do remetente.
Id externo, remetente, horário, ordem e deduplicação (`mensagem_recebida_idempotencia`) seguem o
mesmo caminho da localização. A citação não mostra nome/telefone do contato: rotula pelo catálogo.

**Bolha.** `BolhaContato` mostra nome e números; `tel:` e "copiar" só aparecem para número com 8 a
15 dígitos (sem letras). As chaves de texto novas (`media.contato`, `contatoSemNome`,
`contatoSemTelefone`, `copiarTelefone`, `ligarPara`, `mensagem.citacao.contato`) são opcionais no
schema, como as de citação: um catálogo de filho publicado antes delas não reprova a tela.

**Descartes observáveis.** O tradutor devolve `Traducao(mensagens, descartes)`. Cada descarte é
`{tipo, motivo}`, com `tipo` normalizado para os tipos documentados pelos dois provedores (o resto
vira `outro`) e `motivo` em `TIPO_NAO_SUPORTADO`, `CONTEUDO_INVALIDO`, `SEM_IDENTIFICADOR` ou
`ITEM_MALFORMADO`. O processador grava na linha `webhook_entrada.itens_descartados` e
`webhook_entrada.descartes` (JSONB) e escreve um único log
`[DESCARTE_WEBHOOK] entrada=<id_externo> provedor=<p> itens=<n> descartes=[tipo:MOTIVO,...]`.
Nenhum dos dois carrega telefone, nome, conteúdo ou payload. **Não contam como descarte**, por
decisão: `statuses[]` (inclusive `played`/`deleted` da Uzapi), Status/Story da Uzapi e reentrega
de mensagem já registrada. Não há métrica Micrometer: o módulo de atendimento não depende dele e
não há coleta de métricas em produção documentada; o registro durável é a própria linha.

Consulta operacional, limitada por tempo e servida pelo índice parcial `idx_webhook_entrada_com_descarte`:

```sql
SELECT id_externo, provedor, processado_em, itens_descartados, descartes
FROM webhook_entrada
WHERE itens_descartados > 0 AND processado_em >= now() - interval '7 days'
ORDER BY processado_em DESC
LIMIT 100;
```

**Limites conhecidos.** A linha só registra o descarte quando a transação do POST termina bem; se
o POST falha e esgota, vale o alarme `[ALERTA_WEBHOOK_ESGOTADO]` já existente. O SQL de conclusão
sem descarte é idêntico ao anterior à V81, então o caminho comum não depende das colunas novas; já
um contato recebido exige a V81 aplicada (valor `CONTATO` do enum). Onde a V73 ainda estiver
pendente, a V74 em diante — incluindo a V81 — não roda no boot (ver `docs/41`).

## Objetivo e limites

Garantir que uma mensagem válida e relevante ao atendimento não desapareça silenciosamente entre o provedor WhatsApp e o histórico do CRM. A comparação é por **provedor × direção × capacidade**: recurso do aplicativo WhatsApp não implica suporte da API, da versão instalada ou do CRM. A Base PAI deve funcionar por capacidade, sem condicional pelo nome do cliente.

Antes de qualquer mudança, preservar a disponibilidade da aba Atendimentos: parsing, persistência e observabilidade novos não podem bloquear sincronicamente o caminho de envio/recebimento. Não fazer testes que enviem mensagens a clientes reais sem autorização específica. Payloads, telefones, tokens e nomes não entram em logs, métricas ou fixtures não sanitizados.

## O que a auditoria encontrou

| Capacidade | Meta | UZAPI/Uzapi Autotic | Situação no CRM e evidência | Prioridade proposta |
|---|---|---|---|---|
| Contato compartilhado recebido | Documentado pelo provedor | Documentado pelo provedor | `type: contacts` não consta do mapeamento de nenhum dos dois tradutores; tipo desconhecido gera `WARN` e o item é descartado. `value.contacts[]` é usado para o perfil do remetente e **não** é o contato compartilhado de `messages[].contacts[]`. A linha de entrada pode terminar processada sem mensagem no histórico. **Fase 1: traduzido, persistido e exibido nos dois provedores; coberto por testes, não verificado em instância real.** | P1; elevar a P0 se o incidente real for correlacionado |
| Reação recebida | Documentada | Documentada | Não traduzida; o modelo atual de reação está ligado a usuário do CRM. | P2 |
| Resposta rápida de template recebida | `type: button` documentado | Verificar contrato efetivo | Tradutor Meta não contempla `button`; descarte estático identificado, sem caso real observado. | P1 suspeito; confirmar uso antes da correção |
| Tipo `unsupported` ou novo | Pode ocorrer | Pode ocorrer | Item descartado com `WARN`, sem indicação ao atendente ou contador operacional por tipo. Não transformar todo evento desconhecido em mensagem visível: status e ruído devem continuar filtrados. **Fase 1: o descarte agora fica na linha da fila e no log `[DESCARTE_WEBHOOK]`; aviso ao atendente continua pendente.** | P2 |
| Grupo recebido | Fora do fluxo individual deste plano | Possível na configuração da instância | Suspeita de que mensagem de grupo seja associada à conversa individual do remetente; ainda não demonstrada em produção. | P2, decisão de produto antes de alterar |
| Vídeo enviado pelo atendente | Adaptador tem caminho de envio | Adaptador tem caminho de envio | Tipos permitidos no domínio e seletor do composer não incluem vídeo. | P2 |
| Figurinha recebida | Aceita | Aceita | Renderizada como imagem, sem identificação de figurinha. | P3 |
| Status `played`/`deleted` | Contrato próprio | Eventos possíveis | Não mapeados pela integração UZAPI. Sem decisão de produto registrada sobre exibição/semântica. | P3 |
| Teste ponta a ponta da entrada UZAPI | — | — | Há testes do tradutor, mas não ficou demonstrado teste controller → job → mensagem comparável ao da Meta. | P2 |

Referências de código inicial: [tradutor Meta](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/canal/MetaCloudWebhookTradutor.java), [tradutor UZAPI](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/canal/UzapiAutoticWebhookTradutor.java), [processador de entrada](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/webhook/ProcessadorDeWebhookEntradaOperacoes.java), [tipos de mídia](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/domain/midia/TiposDeMidiaPermitidos.java), [seletor de arquivos](../frontend/src/lib/atendimento/arquivos-do-composer.ts). Contratos existentes: [Meta/UAZAPI](./37-contrato-uazapi.md) e [UZAPI Autotic](./38-contrato-uzapi-autotic.md). Essas referências são pontos de partida; o implementador deve redescobrir o caminho completo no commit em que trabalhar.

### O que **não** está comprovado

- Não foi correlacionado o contato compartilhado relatado pelo usuário a um webhook, provedor, horário, mensagem externa ou registro do banco. A causa do incidente específico permanece hipótese, embora o descarte de `contacts` esteja demonstrado no código.
- O código prevê repasse de payload bruto ao n8n quando configurado; **não** está comprovado que o evento foi entregue nem como o workflow o tratou. A automação deve ser auditada separadamente, com export/execução sanitizados e autorização.
- “Implementado e coberto por teste” não equivale a “confirmado na instância”. O suporte operacional dos outros tipos depende de payloads e versões efetivos.
- A classificação de grupo UZAPI, `button` Meta em uso e status `played`/`deleted` carece de amostra real ou decisão funcional.

## Plano de implementação, em ordem de dependência

As etapas são propostas para PRs pequenos e independentes, com deploy gradual por instância. Não há prazo ou percentual de ganho inventado. Cada etapa exige testes negativos que provem que a proteção realmente atua.

### Fase 0 — Confirmar o incidente e fixar a linha de base (investigação, sem deploy)

1. Obter instância/provedor, janela de horário e identificador externo, quando disponível. Consultar `webhook_entrada` por janela **indexada/limitada** e correlacionar com log, persistência e repasse. Evitar varredura por regex no payload inteiro em produção durante pico; primeiro restringir pelo tempo e inspecionar amostra pequena e sanitizada.
2. Confirmar em qual ponto o contato parou: provedor → controller → fila → tradutor → mensagem → UI → n8n. Distinguir “não chegou” de “chegou e foi descartado”.
3. Registrar contagem de descartes por provedor/tipo e taxa de falhas atuais como linha de base; se os dados não existirem, registrar explicitamente a limitação.

**Saída:** incidente confirmado, refutado ou não verificável, com evidência e sem PII. Não elevar para P0 por hipótese.

### Fase 1 — Tornar perdas observáveis e receber contatos compartilhados (P0/P1)

1. Fazer descarte de tipo válido não suportado deixar evidência operacional estruturada por provedor, tipo e motivo, sem payload/telefone. Definir retenção e cardinalidade segura. A linha processada precisa permitir identificar descarte parcial sem transformar eventos de status em falso incidente. Alarmes devem distinguir perda de mensagem de evento intencionalmente ignorado.
2. Adicionar contato compartilhado ao domínio e aos tradutores Meta e UZAPI, com representação estruturada de nome e telefones, preservando deduplicação, lote misto, id externo, remetente e ordem. Escolher migration **nova** se tipo persistido exigir enum; verificar o próximo número em `origin/main` antes de criá-la. Não usar o array de perfil `value.contacts[]` como mensagem.
3. Persistir e exibir bolha acessível de contato no histórico, inclusive contato sem telefone e múltiplos contatos/números. Ação de chamar/copiar só aparece se houver número válido. Textos via catálogo e cores via tokens.
4. Cobrir os dois provedores em teste de entrada real (controller → job → persistência → leitura), além de casos de uso e UI. Testes negativos obrigatórios: envelope com apenas perfil não cria mensagem de contato; item malformado não elimina os demais do POST; duplicata não duplica mensagem; remetente fora da visibilidade não vaza lead.

**Pronto:** um contato de cada provedor aparece no histórico e é recuperável após recarregar; falha/descartes são rastreáveis; nenhuma regressão no recebimento de texto, mídia e status. Validar primeiro em homologação com payload sanitizado e depois em janela controlada por instância.

### Fase 2 — Interações recebidas e segurança do fluxo (P1/P2)

1. Confirmar se templates ativos geram `type: button` na Meta. Se sim, traduzir texto/identificador e contexto de forma que o atendente veja a resposta; não tratar clique vazio como resposta válida. Testar contra payload oficial/observado e webhook completo.
2. Definir semântica e modelo de reação do cliente: associação ao `wamid`, atualização/remoção, reação repetida e origem externa. Não improvisar `usuario_id` do CRM para representar cliente. Provar que reação a mensagem desconhecida não altera outra conversa nem derruba o lote.
3. Resolver grupos UZAPI por decisão explícita: **filtrar com observabilidade** ou implementar conversa de grupo de ponta a ponta. Até essa decisão, não transformar grupo em conversa individual nem responder automaticamente ao privado. Exigir teste de isolamento com `isGroup` e identificador de grupo.
4. Implementar teste de integração de entrada UZAPI incluindo segredo inválido, POST com itens mistos, retries e deduplicação. Validar que evento ignorado não bloqueia mensagem válida do mesmo lote.

**Pronto:** ações do cliente não somem silenciosamente e grupo não gera resposta no destinatário errado. Preservar contratos `/internal/v1` e comportamento existente do n8n salvo mudança contratual aprovada.

### Fase 3 — Saída e acabamento de mídia (P2/P3)

1. Habilitar vídeo no domínio e no composer apenas após conferir limites/tipos reais dos dois provedores, upload, armazenamento, feedback de erro, status e custo operacional. Testar vídeo aceito, acima do limite, MIME incorreto e falha de envio sem travar chat.
2. Diferenciar figurinha de imagem no histórico se o provedor expuser metadado confiável; manter compatibilidade com mensagens antigas.
3. Decidir e documentar se `played` e `deleted` da UZAPI devem atualizar estado, aparecer como evento ou ser ignorados intencionalmente; só então implementar e testar transição sem regressão de status.
4. Avaliar envio de contato, localização, reação e figurinha **por valor de produto e suporte efetivo do provedor**. Hoje não estão disponíveis no composer; não assumir que todos são obrigatórios para a primeira entrega.

### Fase 4 — Recursos avançados (backlog condicionado, não compromisso de paridade)

Avaliar individualmente pedido, Flows, edição/revogação em Coexistência, grupos Meta, troca de número, localização ao vivo, enquetes, catálogo e metadados de anúncio (`referral`). Para cada um: provar suporte na versão/API usada, demanda real, regra de segurança, contrato do n8n, testes e custo. Se não houver suporte ou demanda, registrar “ignorado intencionalmente”, não “bug pendente”. Templates UZAPI e composição manual de botões/listas seguem decisões/fluxos próprios até revisão explícita.

## Critérios transversais de aceite e rollout

- **Segurança:** nenhum webhook de outro número/instância entra no histórico; RLS e `VisibilidadeLeadSpecification` continuam valendo; resposta manual não permite roubar lead fora do recorte. Testar casos negativos por papel e por origem.
- **Resiliência:** mensagem inválida não derruba as válidas do mesmo POST; retries são idempotentes; falhas de mídia e repasse externo não bloqueiam a aba Atendimentos. Observar filas, tempo de processamento e conexões de banco antes/depois.
- **Contrato:** mudanças em `/internal/v1` exigem teste de contrato e coordenação com n8n; não inferir que workflow foi atualizado pelo deploy do CRM.
- **Compatibilidade:** mensagens antigas continuam legíveis; migration nunca altera arquivo Flyway já aplicado; rollback da aplicação não pode tornar o histórico ilegível sem plano de reversão.
- **Verificação:** `backend/./mvnw clean verify -Dmaven.compiler.release=21`, testes frontend, cenários reais sanitizados por provedor e CI com número da run. Deploy e medição por instância, fora do pico, com critério de rollback se aumentar falhas, latência ou uso de recursos.

## Decisões pendentes do produto/operador

1. Qual instância/provedor e horário do contato compartilhado relatado? Isso é necessário para fechar o incidente real.
2. Aprovar contato como tipo estruturado (recomendado) ou texto formatado; definir exposição de múltiplos números e retenção de dados pessoais.
3. Grupos UZAPI: filtrar ou suportar conversa de grupo completa? O comportamento atual não deve ser assumido como correto.
4. Reação recebida: representação e semântica de remoção/atualização; definir se é prioridade antes de vídeo de saída.
5. Quais recursos de saída e recursos avançados têm demanda real nos clientes? Não abrir implementação de paridade total sem essa decisão.

## Divergências documentais a corrigir durante as respectivas fases

- ~~Comentários do processador de entrada citam figurinha como não suportada~~ — corrigido na Fase 1.
- ~~Comentário da bolha de mensagem omite capacidades já presentes~~ — corrigido na Fase 1.
- ~~O contrato UZAPI/Autotic não explicita o descarte atual de contato e reação~~ — `docs/38` §8
  atualizado na Fase 1 (contato traduzido; reação registrada como descarte).
