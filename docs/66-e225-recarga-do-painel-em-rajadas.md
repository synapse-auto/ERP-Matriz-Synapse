# 66. E225 (B4) — recarga do painel em rajadas (frontend)

Mudança só de frontend. Não mexe em heartbeat 10 s/10 s, backoff de reconexão, broker STOMP, RLS nem em quem recebe
`ATENDIMENTO_ESTADO` no backend (decisão de produto e segurança, fica para depois).

## O que dispara recarga do painel e da contagem

Toda recarga é `invalidateQueries({queryKey: ["atendimentos"]})`, por prefixo: relê a contagem (sempre montada na sidebar),
**todas as páginas já carregadas** da inbox da aba ativa e as listas legadas.

| Gatilho | Onde | Comportamento |
|---|---|---|
| `ATENDIMENTO_ESTADO` (qualquer tipo), `NOVA_MENSAGEM` | ouvinte global (`notificacoes-tempo-real.tsx`) e da tela (`pagina-atendimentos-cliente.tsx`), pela mesma chave | agendador: 1º evento imediato, seguintes coalescidos |
| `TRANSFERENCIA_RECEBIDA`, `ATENDIMENTO_DEVOLVIDO_PARA_IA`, `CONVITE_ATENDIMENTO`, revogação | idem | **urgente**: sem janela, relê tudo |
| `CHAT_INTERNO_*` | só na tela de Atendimentos | agendador |
| Abrir conversa (marcar como lida) | `marcarLeituraDaConversa` | agendador |
| Aviso de finalização em massa; ações locais (enviar, transferir, finalizar, convidar, encaminhar…) | invalidações diretas | **fora do agendador**, não alteradas aqui (são cliques/avisos raros) |
| Foco da janela / reconexão com dado > 30 s | padrões do React Query | não alterados |

Quem recebe `ATENDIMENTO_ESTADO` (backend, `DestinatariosTempoRealRepositorioJdbc`): gestão sempre; atendente/operador se for
dono, se o atendimento estiver `EM_IA` ou `FINALIZADO` ou se for participante. Uma mensagem numa conversa `EM_IA` chega,
portanto, a todos os atendentes e gestores ativos, e cada navegador faz uma recarga — é a amplificação que explica as rajadas.

## O que muda (decisões do responsável)

1. **Janela da lista: 2 s → 5 s** (`JANELA_DA_LISTA_MS`).
2. **Janela própria da contagem: 10 s** (`JANELA_DA_CONTAGEM_MS`), maior que a da lista. Em cada janela o primeiro pedido depois
   de um período quieto continua imediato (mensagem isolada atualiza na hora).
3. **Eventos urgentes** (transferência recebida, devolução à IA, convite, revogação) continuam imediatos e relêem tudo; um
   urgente também cobre o que esperava nas duas janelas.
4. **Aba oculta não recarrega:** o evento só marca as consultas como desatualizadas (`refetchType: "none"`); ao voltar à aba o
   React Query (foco da janela) relê as ativas desatualizadas. Urgente é a exceção.
5. **Diálogos** que listam atendimentos para escolha (encaminhar, lembrete, mensagem programada) saem do prefixo invalidado:
   chave `["dialogos","atendimentos","TODOS"]` com `staleTime` de 5 min (`useAtendimentosParaEscolha`).
6. Não recarrega só a 1ª página da inbox (custo de UX; decisão do responsável).

## Estimativa de chamadas por minuto (simulação, não medição)

Modelo: eventos de Poisson por sessão, janela com borda de entrada e de saída (como o agendador). **Validação do modelo:** 15
sessões × 7,9 eventos/min/sessão dá ~115 recargas/min, e a medição do responsável foi ~118/min.

| Cenário | Lista antes → depois | Contagem antes → depois |
|---|---|---|
| 15 sessões, 2,3 eventos/min (minuto calmo) | 34 → 34 | 34 → 32 |
| 15 sessões, 7,9 eventos/min (janela medida) | 115 → 101 | 115 → 75 |
| 100 sessões, 2,3 eventos/min | 230 → 227 | 230 → 215 |
| 100 sessões, 7,9 eventos/min | 765 → 671 | 765 → 498 |
| teto por aba (rajada contínua) | 30 → 12 por min | 30 → 6 por min |

Leitura honesta: com eventos esparsos (uma recarga a cada ~8 s por sessão) a janela quase não coalesce, porque o primeiro
evento depois de um período quieto é imediato; o ganho aparece na contagem (−35% no cenário medido) e em rajadas. **O efeito de
aba oculta não está no modelo** (não há dado de quantas abas ficam em segundo plano) e tende a ser o maior. Se a contagem
ainda pesar, a opção seguinte é só borda de saída para ela (no máximo 6/min por aba), ao custo de o badge atrasar até 10 s
depois de um evento isolado — decisão do responsável.

## Como medir antes e depois

Chamadas por minuto de `pg_stat_statements` (o responsável já tem a consulta) na mesma janela de pico, antes e depois do deploy;
ou, se o access log do Traefik estiver habilitado, requisições por minuto de `/api/v1/atendimentos/inbox` e `/contagem`.
