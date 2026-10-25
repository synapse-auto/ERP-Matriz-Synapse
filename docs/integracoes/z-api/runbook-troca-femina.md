# Z-API — runbook de preparação, corte e rollback da Femina

24/10/2026. **STATUS: BLOQUEADO / NÃO EXECUTAR CORTE.** Esta revisão não possui adaptador
Z-API. Não existe sequência de variáveis que o torne funcional. As verificações abaixo são
preparatórias; as etapas de mutação só podem ser detalhadas/liberadas após as fases de código,
homologação e autorização específica. Não conectar número, registrar callbacks ou enviar agora.

O executor desta auditoria não acessou VPS/Dokploy e não executou estes comandos em produção.
Comandos de VPS abaixo são **Bash/Linux**, não PowerShell. Usar sessões já autenticadas;
não copiar valores de ambiente, tokens, URLs temporárias, payloads ou mensagens para o relatório.

## 1. Pré-requisitos e versão

- Conferir [auditoria](./auditoria-2026-10-24.md) e [progresso](./progresso.md).
- Responsável por operação, automação e homologação devem definir janela e reversão.
  Preservar disponibilidade de Atendimentos; não matar backend para simular pausa do canal.
- Somente Femina. Não alterar Estrutural/Meta nem compartilhar credenciais entre instâncias.
- Ainda faltam: autenticação oficial de callbacks, adaptadores, correlação, mídia, resultado
  ambíguo, resolução de credenciais históricas e destino de programadas. **STOP** enquanto faltarem.
- Na VPS, conferir somente imagem e tarefas do serviço conhecido:

```bash
docker service inspect fmnaprod-uzapi-36acu4_backend \
  --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
docker service ps fmnaprod-uzapi-36acu4_backend \
  --format '{{.ID}} {{.Image}} {{.CurrentState}}'
```

Não usar `docker inspect` sem projeção nem imprimir `ContainerSpec.Env`.

## 2. CI da release e GHCR

A base auditada é `61a7c6f93c0b6840caa50f60f27e1ab7c437b0d0`, run
[39884940281](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/39884940281):
Backend/Frontend/Stack e publicação de ambas as imagens aprovados. **Não contém Z-API**.
O acesso local aos manifests retornou 403; pull/digest no ambiente operador não comprovados.

Quando houver release Z-API aprovada e merge autorizado (não nesta task), substituir os
placeholders por SHA/run exatos e conferir, sem usar `latest` como prova:

```bash
gh run view <RUN_DA_MAIN_DO_SHA_RELEASE> --repo synapse-auto/ERP-Matriz-Synapse \
  --json headSha,status,conclusion,jobs
docker buildx imagetools inspect \
  ghcr.io/synapse-auto/erp-matriz-synapse-backend:<TAG_EXATA_RELEASE>
docker buildx imagetools inspect \
  ghcr.io/synapse-auto/erp-matriz-synapse-frontend:<TAG_EXATA_RELEASE>
```

Confirmar permissões GHCR por credencial aprovada, sem transmitir token em comando/print.
Registrar digest e imagem anterior para rollback. CI de PR tem GHCR skipped por desenho;
isso **não** prova que uma imagem daquela PR foi publicada.

## 3. Configurações/credenciais

**Ação Dokploy agora: nenhuma.** Nenhuma variável nova foi implementada por esta auditoria.
Manter `WHATSAPP_PROVEDOR=uzapi-autotic` na Femina; não preencher Z-API em variáveis da Meta.
Não alterar valor real nem exibi-lo em evidência.

A futura implementação deve documentar nomes reais em README/.env.example/stack para:
habilitação default false, instance ID, referência do token da instância, Client-Token,
timeout e mecanismo de autenticação oficial dos callbacks. Os nomes não são inventados aqui.
Ausência de recurso desativado não pode derrubar instância Meta/Uzapi.

## 4. Callbacks

**STOP G1:** obter contrato oficial autenticável. HTTPS e `instanceId` falsificável não bastam.
Não registrar a URL do CRM na Z-API nem implementar segredo próprio enquanto o gate estiver aberto.
Após implementar/testar: validar recebimento/processamento/status e precedência da opção de
receber enviadas por mim. Não desativar a entrada Uzapi antes de tratar eventos tardios com
identidade do provedor antigo. Se o seletor global não permitir isolamento, isso bloqueia corte.

## 5. Coordenação da automação

Equipe de automação preenche e valida o [documento para automação](./documento-para-automacao.md).
Inventariar nós diretos Uzapi, callbacks, repasse cru, reservas, proativas, avaliações e timers.
Não enviar payload Z-API a parser Uzapi. Nenhuma fila deve operar nos dois provedores em paralelo.
Definir suspensão/reinício dos produtores e dispatcher **por mecanismo comprovado**, sem derrubar
Atendimentos nem usar “intervalo muito alto” como garantia de pausa. Não há pausa nova nesta PR.

## 6. Inventário de pendências — somente leitura

Executar no banco da Femina com acesso autorizado. Não presume nome de banco, usuário ou container.
As consultas não retornam telefone, conteúdo, payload completo, URL ou segredo. Timeout impede
varredura demorada; se estourar, parar e pedir plano/consulta mais restrita, não aumentar a carga.

```sql
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';

SELECT COALESCE(payload->>'provedorOrigem', 'LEGADO_SEM_ORIGEM') AS origem,
       count(*) AS pendentes,
       count(*) FILTER (WHERE despachado_em IS NOT NULL) AS com_despacho
FROM outbox_evento
WHERE tipo = 'canal.mensagem.enviar'
  AND publicado_em IS NULL AND esgotado_em IS NULL
GROUP BY 1;

SELECT id AS outbox_id, criado_em, proxima_tentativa_em, despachado_em,
       despacho_ambiguo_em, esgotado_em, tentativas,
       payload->>'mensagemId' AS mensagem_id,
       payload->>'credencialId' AS credencial_id,
       payload->>'provedorOrigem' AS provedor_origem
FROM outbox_evento
WHERE tipo = 'canal.mensagem.enviar' AND publicado_em IS NULL
ORDER BY criado_em DESC LIMIT 100;

SELECT id AS programada_id, lead_id, data_envio, status
FROM mensagem_programada
WHERE status = 'AGENDADA'
ORDER BY data_envio, id LIMIT 101;

ROLLBACK;
```

Mais de 100 programadas exige inventário paginado completo, não supor total 100. Linhas esgotadas
ou ambíguas não podem ser republicadas para “zerar a fila”. Contagem de pendentes zero não prova
ausência de chamadas em voo, filas do n8n ou agendamentos futuros. Conferir todos os produtores.

Exigir decisão por programação: manter no caminho anterior, cancelar **explicitamente** pelo
fluxo autorizado, ou reagendar no novo canal com consentimento. Não redirecionar silenciosamente.
Filas legadas sem snapshot devem ser drenadas/conferidas no canal original antes do corte.
Vínculos de credencial precisam ser versionados/resolvidos, não apenas manter o UUID no payload.

## 7. Homologação controlada — autorização futura

Após código/CI prontos e autorização, usar número/lead de teste com acesso restrito. Testar
texto (acento, emoji, newline), imagem, vídeo, áudio gravado e anexado, PDF e JPG/PNG DOCUMENTO;
bytes/MIME/nome/duração; citação; encaminhamento interno→cliente e programada. Registrar IDs
técnicos e tempos. Nenhuma mensagem deve ir a cliente real.

Correlacionar envio → aceite → processamento → entrega/leitura reais → persistência → WebSocket
→ recarga. Repetição/eco não duplica. Negativos: outra instância, usuário sem acesso, timeout,
callback inválido, mídia indisponível e HTTP 2xx incompleto. Mensagem antiga Uzapi deve continuar
legível; citação entre provedores precisa rejeição/feedback explícitos, nunca enviar ID incompatível.

## 8. Corte explícito — Femina somente

**Não liberado por esta revisão.** Só após gates anteriores: congelar produtores/dispatch de forma
coordenada, confirmar nenhuma chamada em voo e reconciliar pendências; aplicar release/configuração
aprovadas e credencial versionada da Femina; validar saúde e apenas então retomar cada produtor.
Não fazer update SQL de payload/ID para trocar provedor. Não criar janela de duplo envio.
Se não houver mecanismo seguro para callbacks tardios/credenciais antigas, parar aqui.

## 9. Verificação posterior

Operador verifica saúde crítica, estado da conexão (HTTP 200 não basta), filas, mensagens sintéticas
e status persistidos após recarga/reconexão. Conferir mídia privada e permissões. Diagnóstico
sanitizado: horários, códigos e IDs somente. Nunca URL com `/token/`, headers ou corpo de cliente.
Não iniciar replay/backfill para preencher histórico.

## 10. Rollback sem duplicação

1. Suspender de forma comprovada novos produtores/dispatch Z-API e workflows diretos, sem reenviar.
2. Aguardar/identificar chamadas em voo e guardar versão/digest/identidade dos dois canais.
3. Inventariar mensagens Z-API aceitas, pendentes, ambíguas e programadas. Conferir cada resultado
   incerto com o fornecedor; não transformar ausência de callback em autorização para reenviar.
4. Manter IDs Z-API e credenciais/origem; callbacks tardios só atualizam o namespace correto.
5. Não simplesmente voltar `WHATSAPP_PROVEDOR` com fila pendente. O novo guard bloqueia snapshot
   divergente, mas não resolve legado nem segredo histórico. Linhas bloqueadas ficam esgotadas
   para revisão; voltar configuração não as reativa.
6. Reativar Uzapi somente com autorização e prova de que nenhuma pendência Z-API será despachada
   por ela. Recriação/reenvio exige decisão por ocorrência e comprovação de não envio anterior.

Se esses passos não puderem ser garantidos pela versão final, **não realizar o corte**.
Este documento não contém comandos de mutation fictícios para um adaptador que não existe.
