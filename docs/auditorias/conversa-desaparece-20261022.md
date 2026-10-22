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

## Operação

Ação necessária no Dokploy: **nenhuma variável nova**. Requer implantação normal do
código aprovado para ter efeito; esta tarefa não realiza merge ou deploy.
