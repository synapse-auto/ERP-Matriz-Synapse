# Chat interno — reconciliação entre abas e encaminhamento idempotente

## Escopo e base

Quarta capacidade da auditoria em [45](45-chat-interno-midia-e-paridade.md), sobre os PRs
#223 (mídia), #224 (vídeo) e #225 (contato). Esses PRs estavam **abertos**, não mergeados,
na conferência de 26/09/2026. Branch empilhada sobre `codex/chat-interno-contato-compartilhado`;
não duplicar os commits anteriores nem tratar essas capacidades como deployadas.

## Defeitos confirmados

1. Os eventos de mensagem/edição excluíam o autor dos destinatários. A resposta HTTP atualizava
   somente sua aba de envio; outra aba do mesmo participante não recebia a atualização.
2. O encaminhamento não possuía chave idempotente. Uma resposta perdida depois da persistência
   permitia que a tentativa seguinte criasse outra cópia.
3. O chat não reconciliava o histórico ao receber um novo `CONNECTED`. Em navegador real,
   o socket fechou, uma mensagem foi persistida durante a interrupção e outra sessão conectou,
   mas a bolha ficou ausente até recarregar. Redis/STOMP não é histórico durável.

## Correção

Mensagem, mídia, contato, resposta, encaminhamento e edição notificam todos os participantes,
inclusive o autor. A política visual existente silencia eventos do próprio usuário, mas mantém
a atualização do cache. Não há destinatários fora da conversa nem privilégio adicional por papel.
Remoção e reação já incluíam todos e não foram reimplementadas. O relay continua `AFTER_COMMIT`.

A página e o painel embutido recuperam as consultas de `chat-interno` em cada ciclo conectado,
depois que as assinaturas já estão ativas. Isso cobre a janela sem eventos, sem polling, outro
socket ou mudança no reconector. HTTP/histórico persistido permanece fonte de verdade.

## Contrato compatível

`POST /api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}/encaminhar`

- JWT/Bearer e participação na origem **e** no destino, verificados antes do replay.
- Corpo existente: `{ "conversaDestinoId": "UUID" }`.
- `Idempotency-Key: UUID` opcional para clientes antigos; o CRM sempre envia uma chave e a
  preserva enquanto tenta novamente para o mesmo destino.
- `201`: mensagem persistida ou replay da mesma mensagem/ID. `400`: chave/pedido inválido ou
  origem incompatível; `401`: sem autenticação; `403`: participação insuficiente; `409`: mesma
  chave com remetente, destino ou origem diferente. Erros seguem RFC 7807 existente.
- Sem chave, mantém o contrato antigo: não há garantia de deduplicação de retries desse cliente.

A reserva usa o repositório existente e namespace `encaminhar:`. O fingerprint SHA-256 identifica
conversa/mensagem de origem; não deduplica por conteúdo, horário ou URL assinada. Reserva, cópia
e conclusão são atômicas na transação. Replay não publica outro evento. Não houve migration.

O encaminhamento reutiliza a referência persistida do mesmo objeto de mídia e seus metadados,
sem download/reupload. O destino autorizado pode ler seus bytes; isso não libera histórico nem
origem a quem só participa do destino. Nenhum envio para WhatsApp é disparado.

## Evidências

- `AcoesDeMensagemChatInternoIT`: 5/5 em Surefire e Failsafe, incluindo HTTP real, concorrência,
  replay/conflito, gestor fora da origem e duas sessões STOMP do autor. Registro legível por
  conexão externa ao receber o evento; nenhum segundo evento no replay.
- `VideoChatInternoIT`: 7/7, ampliado com encaminhamento de MP4 real, legenda, mesma referência
  de storage e bytes idênticos no destino; direta e grupo preservados.
- `OpenApiIT`: 6/6, UUID opcional e erros do encaminhamento. `ChatInternoUseCaseTest`: 14/14.
- Frontend dirigido: 68/68 em 12 arquivos. Teste do diálogo prova chave/destino preservados na falha e
  bloqueio de clique concorrente. Teste da página prova reconciliação por ciclo, não por rerender.
- Playwright CLI headed, aplicação local real e MinIO: contato aparece uma vez na segunda aba
  do remetente sem F5; encaminhamento de vídeo teve resposta real perdida deliberadamente após
  `201`, retry com mesma chave/ID, reprodução e histórico após reload. Não houve resposta mockada.
- Capturas em `output/playwright/` e `docs/assets/chat-interno-reconciliacao/`.
- Reações reais: adicionar, substituir, recarregar e remover atualizam a segunda aba. Resposta
  ao vídeo retorna `201`, chega uma vez na outra aba sem F5 e mantém citação no histórico.
- Reconexão real controlada: contexto offline e fechamento explícito do socket nativo, envio
  `201` na outra sessão durante a interrupção, novo `CONNECTED` e recuperação de uma única
  bolha sem F5. Não simula frames/respostas; não certifica comportamento de proxy de produção.
- `backend/.\\mvnw.cmd clean verify -Dmaven.compiler.release=21`: `BUILD SUCCESS`, 12min08s,
  765 integrações no crm-app e 14 no crm-atendimento, sem falhas/erros; inclui Spotless e ArchUnit.
- Frontend completo: 757/757 em 118 arquivos (496,50s). Typecheck e build aprovados; lint sem
  erros, com quatro avisos preexistentes. `git diff --check` limpo.

As primeiras tentativas locais encontraram Docker desligado e uma montagem incorreta do parâmetro
de autenticação no teste STOMP (`token` em vez do real `access_token`). Ambos foram corrigidos
antes da execução dirigida aprovada; não são falhas de autorização de produção.

CI desta capacidade será registrada no PR após o push; resultados locais não são CI verde.
Nenhum deploy ou alteração de dados de produção.

Capturas revisadas, desktop 1440×1000 e celular 390×844:

- [Falha/retry](assets/chat-interno-reconciliacao/chat-encaminhamento-retry-desktop.png).
- [Vídeo encaminhado desktop](assets/chat-interno-reconciliacao/chat-encaminhamento-grupo-desktop.png)
  e [celular](assets/chat-interno-reconciliacao/chat-encaminhamento-grupo-mobile.png).
- [Reação persistida](assets/chat-interno-reconciliacao/chat-reacao-desktop.png).
- [Resposta desktop](assets/chat-interno-reconciliacao/chat-resposta-desktop.png)
  e [celular](assets/chat-interno-reconciliacao/chat-resposta-mobile.png).
- [Reconexão](assets/chat-interno-reconciliacao/chat-reconexao-mobile.png)
  e [segunda aba](assets/chat-interno-reconciliacao/chat-contato-duas-abas.png).

## Limitações e operação

Prévia de link com imagem permanece indisponível: o contrato não possui destino/metadados reais.
Links textuais são clicáveis; imagens anexas abrem o visualizador. Não há fetch Open Graph.
A última mensagem da lista ainda pode mostrar texto vazio para contato/mídia sem legenda;
isso exige evolução separada do read model, não inferência de identidade/conteúdo.
Em 390px, o composer preexistente comprime o textarea entre os controles; a captura comprova
essa limitação, não uma paridade visual perfeita. Ajuste responsivo deve ser separado desta
capacidade. 3GP tem cobertura de integração, não reprodução visual comprovada no navegador.

Não há variável nova nem ação obrigatória no Dokploy nesta capacidade. Meta, UZAPI, n8n,
RLS de leads, storage externo, migrations e envio externo não foram alterados.
As skills `clean-code`, `architecture-patterns` e `api-design-principles` não estavam disponíveis;
foi usada a skill Playwright para validação headed, preservando a arquitetura existente.
