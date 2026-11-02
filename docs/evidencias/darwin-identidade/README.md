# Validação visual — Darwin na ficha real

02/11/2026, Playwright CLI **headed/Chrome**, Next real em `localhost:3014`, backend real
em `localhost:8094`, Postgres 15/Redis descartáveis e HTTP Darwin simulado em loopback.
Fixture opt-in `DarwinIdentidadeLocal` somente em test-classes, `DARWIN_VISUAL_LOCAL=true`.
Contas do seed dev e identidade/CPF exclusivamente sintéticos. Nenhuma conta ou chamada produtiva.
Arquivos originais em `output/playwright/`; estas são cópias para revisão versionada.

| Evidência | Comprovação |
|---|---|
| [Ficha desktop](darwin-ficha-desktop.png) | Entrada na lateral real, CPF ausente e botão manual. |
| [Prévia desktop](darwin-previa-desktop.png) | Atual/encontrado, sessão 123, nome existente desmarcado e CPF vazio selecionado. |
| [Loading](darwin-loading-desktop.png) | Consulta suspensa na fixture HTTP; aplicar bloqueado, cancelar disponível. |
| [Conflito](darwin-conflito-desktop.png) | Edição manual no banco descartável entre consulta/aplicação; HTTP 409, não sobrescreve, exige nova consulta. |
| [Vazio](darwin-vazio-desktop.png) | Array de sessões vazio, nenhum valor inventado e aplicação bloqueada. |
| [Erro](darwin-erro-desktop.png) | HTTP 403 upstream convertido em 502 sanitizado, sem deslogar ou fechar a conversa. |
| [Sucesso desktop](darwin-sucesso-desktop.png) | Nova consulta e seleção explícita; nome confirmado salvo. |
| [Ficha atualizada desktop](darwin-ficha-atualizada-desktop.png) | Nome da ficha, cabeçalho e lista + CPF atualizados **sem F5**, depois da correção do editor. |
| [Prévia mobile](darwin-previa-mobile.png) | Entrada pela ficha móvel e modal com todas as ações na viewport. |
| [Sucesso mobile](darwin-sucesso-mobile.png) | Nome/CPF selecionados e aplicação confirmada pelo backend real. |
| [Ficha mobile](darwin-ficha-atualizada-mobile.png) | Campos presentes na ficha lateral responsiva, sem sobreposição. |
| [Reload mobile](darwin-persistido-mobile.png) | Reload, reabertura do atendimento e da ficha recuperam nome/CPF persistidos. |

Desktop: **1920×1080**; celular: **390×844**; zoom 100%, mesmo tema padrão do seed.
`document.fonts.ready` concluído, status `loaded`, fonte computada
`Inter, "Inter Fallback", Inter, sans-serif` nos dois tamanhos. Sem overflow horizontal.
Modal móvel medido: left 16, right 374, top 175,5, bottom 668,5 (viewport 390×844).
Teclado: foco inicial em Cancelar; Tab move para Consultar novamente e permanece no diálogo.
Cancelar fecha sem persistir; campos existentes exigem checkbox explícito. Não foi enviada mensagem.

As primeiras capturas documentam a rodada que revelou colisões de chaves React: a integração
recebeu prefixo próprio, e a colisão preexistente Notas/Mídias também foi corrigida. Um teste de
renderização reprova essa condição. A captura final desktop foi refeita após navegação limpa:
o editor de nome agora segue o valor salvo sem apagar um rascunho manual em curso (dois testes
específicos), em vez de manter o nome inicial após refetch.

Limitações do ambiente visual: Next em desenvolvimento mostra sua badge em parte das capturas;
a fixture sem proxy não resolve o logo em `/api/v1/config/logo` nem o WebSocket em `/ws`.
Não são evidências de homologação da conta Darwin ou de realtime; esta integração não adiciona
realtime/polling e usa invalidação das queries existentes. Nada de reprodução HTML simplificada.

## Resultados automatizados

- `CpfTest`: 12/12; `DarwinHttpAdapterTest`: 15/15.
- `DarwinLeadIT`: 8/8 e `DarwinDesabilitadaIT`: 1/1, HTTP CRM/PostgreSQL/Testcontainers reais.
- Frontend completo Node 22.23.4: **170 arquivos / 1.438 testes**, sem falhas.
- Typecheck, lint (4 avisos preexistentes), build e audit de produção (zero vulnerabilidades).
- Primeira CI PR `41398089248`: frontend/stack passaram; backend falhou na limpeza antiga de
  `EncaminhamentoDoChatParaClienteIT` (20 erros do trigger V65 em DELETE com citações) e em
  `RespostaEEncaminhamentoIT` (um timeout). Não foi chamado de verde nem ignorado.
  Limpeza da fixture corrigida, sem alterar trigger/migration/produção, com regressão própria;
  resultado da nova verificação e CI do último SHA constam do relatório atualizado da PR.

O `clean verify` completo local está documentado separadamente da CI. Testes simulados não
comprovam credencial, mapeamento, disponibilidade comercial ou dados reais da Fêmina.
