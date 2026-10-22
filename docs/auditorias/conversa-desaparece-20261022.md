# Conversa que desaparece durante atendimento — auditoria de 22/10/2026

## Escopo e limite da conclusão

Relato na Femina/Uzapi: a área central ficou vazia enquanto uma atendente iniciava o
atendimento e ouvia áudio. Sem horário, usuário ou atendimento identificado. Não há
acesso à VPS nesta auditoria. Os defeitos abaixo são reproduzíveis no código comum
da Base PAI; isso **não comprova a causa da ocorrência real**, a versão implantada,
nem um defeito da Uzapi. Não foi acessada ou alterada produção.

Base de investigação: `origin/main c8bffbb`. Foram preservadas as alterações da pasta
principal, em branch/worktree isolada. Referências: docs/38, 39, 40, 51, 70 e 73.

## Causas locais comprovadas

1. **Leitura cancelava o estado da conversa.** `marcarLeituraDaConversa` cancelava
   todas as queries `atendimentos`, inclusive `/estado`. O cancelamento do TanStack
   restaura o estado anterior ao início do GET: quando começou sem dados, um snapshot
   preenchido por outra leitura podia voltar a `undefined`, desmontando histórico,
   assinatura e player. Agora apenas as queries de lista/contagem são canceladas;
   `estado` e `cartao` não participam desse rollback otimista.
2. **404 atrasado de A fechava B.** O callback capturava o ID selecionado ao iniciar
   a consulta, comparava esse ID antigo com ele mesmo e limpava o estado atual.
   Agora a conclusão assíncrona é vinculada à seleção vigente, inclusive ao percurso
   A → B → A. Revalidação pré-envio obsoleta retorna falso, sem autorizar outro envio.
3. **Socket substituído ainda podia revogar.** A assinatura de mensagens já validava
   a identidade do cliente STOMP; as filas de revogação/notificações não. Um callback
   atrasado do cliente anterior podia fechar a conversa reaberta. Ambas as filas
   agora rejeitam callbacks de cliente substituído. Revogação do cliente atual continua
   funcionando; o teste cobre explicitamente os dois lados.

Os testes novos foram executados contra o comportamento anterior e reprovaram nos
três casos. A correção não depende de cliente, papel ou provedor.

## Inventário do ciclo de vida

| Caminho | Consequência e tratamento |
| --- | --- |
| Clique em cartão | Seleciona `atendimentoAtivoId ?? atendimentoId`, marca leitura e busca estado autorizado. Não depende de continuar na página/filtro. |
| Cartão ausente, reordenação, paginação ou refiltro | Atualiza a lista, não limpa seleção. |
| Abrir conversa interna | Limpa a seleção externa por navegação explícita; não altera o Chat Interno. |
| Botão voltar / título Atendimentos / histórico do navegador | Retorno explícito à lista/painel; deep link pode abrir outro ciclo. |
| Iniciar/reativar | Abertura pelo ID devolvido pelo comando; não substitui implicitamente o ciclo antigo pelo lead. |
| Finalizar | Reconsulta o estado; histórico finalizado permanece conforme autorização, composer fecha. Não apaga mensagens. |
| Evento canônico | Deduplica evento/versão; só a seleção vigente recebe efeitos de conclusão. Outro atendimento atualiza a inbox. |
| Desconectar/reconectar | Mantém seleção e histórico. Snapshot governa liberação de incrementais, não existência da conversa. |
| 5xx, timeout ou erro de rede | Preserva último snapshot autorizado e apresenta falha com tentativa explícita; sem dados iniciais, mostra carregamento/erro em vez de “nenhuma conversa”. |
| 403/404 de estado ou revogação atual | Fecha seleção com aviso e remove cache protegido; não ignora RLS nem concede acesso por otimismo. |
| 401 | Continua passando pela renovação/login existentes; não é tratado como indisponibilidade do provedor. |
| Troca de usuário/papel | QueryProvider existente troca o cache; renovação do token da mesma conta não o troca. |

## Segurança e recuperação

- Snapshot REST continua canônico. Specification, RLS, capacidades, propriedade e
  exceção de participação consentida não foram alteradas.
- Revogação descarta a geração de reconciliação em voo; resposta tardia não pode
  recolocar snapshot revogado no cache. Cancela/remove estado, mensagens, resumo,
  informações de chatbot, ficha/anexos e URLs assinadas daquele recurso.
- Um GET de estado com versão anterior não substitui snapshot mais novo já no cache.
- Não há polling novo, consulta pesada, chamada ao provedor, resend automático ou
  alteração de outbox/n8n. Rascunhos não são apagados pela recuperação técnica.
- Textos novos são catalogados com defaults para catálogos antigos. Sem mudança de
  família tipográfica, tema ou layout da conversa.

## Evidência e reprodução

`pagina-atendimentos-cliente.test.tsx` exercita página, QueryClient, entrada STOMP
pessoal e conclusões HTTP controladas: leitura concorrente, 404 antigo, negativa
403/404, 408/500/503 com recuperação e envio revalidado após troca de conversa.
`tempo-real.test.ts` cobre cliente substituído e revogação atual; o reconciliador
cobre descarte de resposta em voo e nova leitura autorizada posterior.

Validação visual deve usar a aplicação real, duas sessões, banco sintético e canal
fake explícito; não um HTML reproduzido nem WhatsApp real. A fixture existente
`MidiasFichaLocal` disponibiliza áudio em storage MinIO privado e dois atendimentos.
Resultados finais, screenshots e limitações são vinculados ao SHA/PR no relatório.
Não confundir testes controlados de falha HTTP com homologação na Femina.

### Resultados em 22/10/2026

- Frontend com Node 22.23.3, Next.js 16.4.2 e dependências do lockfile: **167 arquivos,
  1.373 testes aprovados**; typecheck e build aprovados. Lint: zero erros e quatro
  avisos preexistentes (virtualizador e navegação em sidebar/menu/cliente HTTP).
- Backend: `mvnw.cmd clean verify -Dmaven.compiler.release=21`, JDK 21.0.12,
  **BUILD SUCCESS**, 30m47s; Spotless, ArchUnit e Testcontainers incluídos.
  `TempoRealIT`: 12 testes, incluindo assinatura não autorizada, revogação, TTL,
  reconciliação, backplane e evento canônico com dois atendentes; todos aprovados.
- O teste de leitura mantém o POST pendente para isolar seu cancelamento otimista.
  Sem isso, a recarga legítima após a confirmação terminava o GET controlado e
  invalidava a asserção de `fetchStatus`, sem representar uma perda do histórico.
- Primeira execução Linux usou uma cópia antiga do catálogo do backend e falhou em
  `TextosSchema`. Repetida com fontes atuais: passou. Não foi afrouxada a validação
  do catálogo. O CI também passou esse teste com o catálogo versionado.
- PR [#318](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/318).
  CI [39841341751](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/39841341751),
  SHA `0a5ff759dd097926a5fb0815ae9e453c2cbda793`: Backend, Frontend e Stack aprovados;
  imagem GHCR skipped. Esse SHA contém o código validado; commits posteriores de
  evidência devem ter sua própria run conferida, sem herdar este resultado.

### Navegador real, somente dados sintéticos

Chromium headed, aplicação Next.js real em `localhost:3012`, backend real em
`localhost:8092`, PostgreSQL/Redis/MinIO descartáveis e canal explicitamente fake.
Login pelo BFF real; tokens/cookies não foram gravados nas evidências. Fonte computada
`Inter, "Inter Fallback", Inter, sans-serif`, após `document.fonts.ready`.

| Cenário | Resultado observado |
| --- | --- |
| Ana responsável e Bruno convidado/aceito, em sessões isoladas | Mensagem de Bruno chegou pelo WebSocket; propriedade continuou com Ana. |
| Áudio durante mensagem colaborativa e leitura da segunda sessão | Mesmo elemento `audio`, conectado e reproduzindo; rascunho preservado. WAV sintético de 1 segundo em loop só no teste. |
| Offline/online e fechamento controlado do WebSocket | Conversa mantida; reconexão real executou nova leitura de estado. |
| 503 na reconciliação | Histórico/player/rascunho preservados e feedback com tentativa explícita; recuperar não fez POST de mensagem. |
| 408, 500 e timeout de rede | Conversa e rascunho preservados; botão de recuperação removeu o erro após GET bem-sucedido. |
| 401 uma vez | Refresh real do BFF e nova leitura, sem redirecionar a conversa para vazio. |
| Filtro excluindo o cartão aberto | Cabeçalho permaneceu no atendimento selecionado, sem substituição pela lista. |
| GET de A atrasado, clique em B e 404 posterior de A | B permaneceu selecionado; zero POSTs de mensagem pela recuperação. |
| Participante sai e reconecta | Backend deixou de autorizar seu estado; cabeçalho protegido foi removido. Saída de B não fechou A. |
| Finalização e reativação pelo cabeçalho | Histórico finalizado permaneceu; reativação abriu novo ciclo, sem mensagem automática. |
| Gestor transfere em outra sessão e responsável anterior reconecta | GET do antigo responsável retornou 404; conversa protegida removida da tela. |

**Limite da fixture:** os agendamentos estão desligados para não executar outros
jobs. A publicação canônica da outbox não foi simulada como se fosse entrega real:
nos cenários de saída/transferência, a negativa foi confirmada na reconexão REST.
O worker real, publicação e revogação pelo backplane são cobertos separadamente pelo
`TempoRealIT`. Não houve homologação de entrega/leitura do WhatsApp/Uzapi.
Eventos duplicados, outro atendimento, versões fora de ordem, lista/página ausente,
atualização de status e isolamento de cache são cobertos pelos testes automatizados;
não se afirma que cada combinação foi reproduzida visualmente com o provedor real.

Capturas da aplicação, não reprodução HTML:

- [Desktop 1920×1080](../evidencias/2026-10-22-conversa-estavel/conversa-desktop.png)
- [Celular 390×844](../evidencias/2026-10-22-conversa-estavel/conversa-mobile.png)
- [503 com conversa preservada](../evidencias/2026-10-22-conversa-estavel/conversa-recuperacao-503.png)
- [Revogação legítima](../evidencias/2026-10-22-conversa-estavel/conversa-revogacao.png)

### Atualização de segurança autorizada

O CI inicial interrompeu o frontend na auditoria de produção, antes dos testes, por
alertas críticos preexistentes em Next.js 16.3.8. Após autorização explícita, commit
separado atualiza `next` e `eslint-config-next` para **16.4.2**, preservando React,
Node, regras de auditoria e configuração de build. O lockfile foi regenerado;
`npm audit --omit=dev --audit-level=high` retornou **zero vulnerabilidades**.
Referências oficiais: [release 16.4.2](https://github.com/vercel/next.js/releases/tag/v16.4.2),
[GHSA-2464-fhr4-w9xw](https://github.com/advisories/GHSA-2464-fhr4-w9xw) e
[GHSA-673q-mjm6-r5xr](https://github.com/advisories/GHSA-673q-mjm6-r5xr).
Não foi executado `audit fix --force`, upgrade de Java ou alteração de permissões.

## Roteiro para a próxima ocorrência real

1. Anotar horário com fuso e versão/tag/digest efetivamente implantados. Não exigir
   que a atendente identifique a paciente nem tente reenviar a mensagem.
2. No console, coletar somente registros estruturados `[atendimento.abertura]`
   próximos à ocorrência: ID de usuário, atendimento, lead, etapa, evento e HTTP.
   `selecao_encerrada` informa HTTP, ciclo de conexão e motivo
   (`SNAPSHOT_INDISPONIVEL` ou `REVOGACAO_WEBSOCKET`) do fechamento por acesso.
3. Na rede, registrar **apenas** horário/status do GET `/estado` e ciclo de reconexão.
   Não exportar HAR bruto, cabeçalhos, cookies, JWT da URL WebSocket, corpo de
   mensagens, áudio, nomes, telefones ou links assinados.
4. Operador correlaciona IDs/horário com revalidação do subscriber e evento canônico
   no backend. Um timeout de revalidação não prova transferência. A correção E208 já
   preserva a assinatura na falha técnica canônica/TTL; conferir versão implantada.
5. Compartilhar IDs somente por canal privado. Não reprocessar eventos, mudar dono,
   relaxar permissões ou editar dados para investigar.

Hipóteses restantes da ocorrência: versão anterior à proteção E208, mudança legítima
de responsável/participação, navegação explícita ou uma das corridas reproduzidas.
Sem a correlação acima, não é possível escolher uma delas com segurança.

Achado separado: o caminho legado `TRANSFERENCIA` ainda trata exceção de revalidação
de colaboração como negativa. Não foi ampliado neste hotfix; necessita reprodução
dedicada antes de alterar a política de entrega. Nenhum frame é liberado por erro.

Na navegação sintética, o console também registrou chave React repetida na inbox
para o mesmo lead, sem perda da seleção nestes cenários. Não se atribui a ocorrência
real a esse aviso sem uma reprodução própria. Os indicadores de quantidade de
mensagens da ficha não são atualizados pela carga direta da fixture de mídia; esses
zeros sintéticos não são evidência de defeito de contagem na instância real.

## Operação

Ação necessária no Dokploy: **nenhuma variável nova**. Requer implantação normal do
código aprovado para ter efeito; esta tarefa não realiza merge ou deploy.
