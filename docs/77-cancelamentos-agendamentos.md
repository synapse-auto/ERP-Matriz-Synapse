# Cancelamentos de agendamentos — persistência genérica no CRM

## Decisão e fronteira

A decisão aprovada em 18/10/2026 substitui a leitura externa preparada na PR #313. O CRM recebe
eventos por API de serviço e mantém projeção local imutável. Não consulta banco/view do n8n.
`docs/n8n/sql/001-agendamento-evento.sql` continua um artefato externo independente, não instalado
pelo CRM nem exigido por esta aba. Interface, permissões, filtros e detalhes existentes foram
reaproveitados. O adaptador não configurado foi removido: banco vazio retorna lista vazia real.

Contrato implementado e exemplo sintético: [documento para Automação](n8n/cancelamentos-crm.md).
`POST /internal/v1/cancelamentos` usa `X-Synapse-Token`, ROLE_SERVICO no filtro e caso de uso.
JWT humano não substitui o token. Não cancela na origem, finaliza/redistribui atendimento, envia
WhatsApp ou cria lembrete/reagendamento. Não houve deploy, ativação ou alteração no workflow.

## Persistência e idempotência

Tabela genérica `cancelamento_agendamento`, no módulo `crm-relatorios`, com composição dos vínculos
em `crm-app`. Sem campo específico de ramo/cliente, tenant_id, clinica_id, telefone ou JSON livre.
Obrigatórios: origem (80), evento externo (200), agendamento externo (200), chave (200), horário
do agendamento. Nomes de contato/profissional/tipo são opcionais (200 cada), motivo opcional (2000).
São limites de contrato/storage para apresentação, não autorização para copiar conteúdo clínico.
Não armazenar prontuário, histórico, resumo ou payload bruto. Identificadores técnicos usam ASCII
alfanumérico inicial e demais caracteres alfanuméricos ou `._:/-`; nunca nome/telefone/token.

Textos são normalizados nas bordas, branco vira null. Campos de uma linha rejeitam controles;
motivo permite LF/CR/TAB e preserva quebras. Datas exigem offset explícito e são normalizadas em
UTC/microssegundos do PostgreSQL. `canceladoEm` permanece null se desconhecido; `recebidoEm` vem
do relógio do CRM e não pode ser informado pelo n8n. Não confundir ocorrência e ingestão.

Par leadId/atendimentoId: ambos presentes ou ambos ausentes. Composição valida existência pelo
LeadRepositorio (Specification) e consulta EXISTS de atendimento no pool geral (RLS), conferindo
a relação do atendimento ao lead.
UUID inexistente/par incompatível responde 400 sanitizado. Não inferir vínculo por nome/telefone.

Constraints únicas `(origem,chave_idempotencia)` e `(origem,evento_externo_id)`, separadas do ID
do agendamento. INSERT ON CONFLICT DO NOTHING espera o concorrente; releitura READ COMMITTED
confere chave e impressão SHA-256 do contrato normalizado. Replay idêntico = ID e recebimento
originais, HTTP 200/repetido=true; novo = 201; incompatível = 409 sem sobrescrever. Eventos distintos
do mesmo agendamento continuam distintos. Espaços marginais/offset equivalente/precisão além de
micros não mudam a impressão. Não se deduplica por contato ou somente agendamento.

Auditoria por @Auditable: AUTOMACAO, REGISTRAR_CANCELAMENTO e ID do registro. Allowlist limitada
a id/recebidoEm/repetido; nomes, motivo e token não entram nos dados de auditoria.
A ingestão autenticada é independente da flag **de visualização**: permite preparar dados com a
aba OFF. Isso não habilita produção e não concede acesso SQL. Não existe backfill automático.

## Permissões e leitura

Flag `cancelamentos` OFF por padrão (V108), capacidade `cancelamentos.ver`, teto somente VER.
GESTOR/SUBGESTOR seguem padrão gerencial; ADMINISTRADOR perfil fixo. ATENDENTE/OPERADOR começam
sem acesso; concessão explícita pela Gestão não aumenta a carteira nem habilita escrita.
Configuração/lista/detalhe mantêm @PreAuthorize por papel/capacidade/flag.

Leituras locais abrem transação read-only no **pool geral**, com contexto RLS antes do SQL.
Tabela com RLS ENABLE + FORCE, sem leitura sem contexto; synapse_app só insere como SERVICO e
não tem UPDATE/DELETE. Não se depende de owner/superusuário para aparentar proteção.

- Sem vínculo: somente GESTOR/SUBGESTOR/ADMINISTRADOR autorizados enxergam.
- Com vínculo: RLS vigentes de **lead e atendimento** devem permitir ambos, antes de filtros e
  LIMIT. Read models usam RLS conforme a arquitetura de relatórios (V12); a validação pontual
  passa pela Specification. Não se copiam regras de dono/participação para o frontend.
- Potenciais/finalizados/participações seguem a política atual, não uma carteira paralela.
- Detalhe não visível/inexistente = 404 indistinguível. IDs canônicos só vêm do registro autorizado.
  Link no detalhe exige par completo + atendimentos.ver; abrir usa fluxo que revalida o acesso.

## Contrato de consulta e datas

JWT normal, prefixo `/api/v1/cancelamentos`:

| Método/rota | Contrato |
|---|---|
| GET `/configuracao` | Fuso, período inicial e limites existentes |
| GET `/` | inicio/fim locais inclusivos, base, profissional/tipo/nome/descricao, pagina/tamanho |
| GET `/{id}` | UUID opaco do registro; fora de escopo/inexistente = 404 |

`base=REGISTRO` agora significa **recebimento no CRM**, conforme a nova decisão (antes só havia
contrato draft de registro na fonte, sem adaptador de produção). AGENDAMENTO usa agendadoPara;
nova base CANCELAMENTO filtra canceladoEm conhecido, nunca substitui ausência por recebimento.
Intervalo `[inicio,proximoDia)` no fuso da instância, incluindo DST.

Resposta `{itens,temProxima}`, sem total global/COUNT. Tamanho+1, página zero-based, data escolhida
DESC + UUID DESC. O intervalo exclui datas nulas antes da ordenação. Controles não apresentam
tamanho de página como total. Inserções entre
páginas podem deslocar offsets; não se promete snapshot histórico. Campos adicionais: recebidoEm,
canceladoEm, leadId, atendimentoId. registradoEm fica como alias de canceladoEm para preservar a
forma antiga da resposta draft, mas não é usado pela UI. Não deduzir recebimento por esse alias.

Filtros combinados no SQL, substring literal case-insensitive escapando %, _ e !. Descrição branca
normaliza para null na entrada, COM/SEM usa IS [NOT] NULL. HTML é escapado pelo React. Falha de
banco = 503 FONTE_INDISPONIVEL sanitizado, nunca vazio. 401/403 preservados. Sem dados fictícios.

## Atualização e desempenho

Transação curta, timeout configurável synapse.cancelamentos.timeout-segundos (inicialmente 5s),
sem rede/provedor no caminho de gravação. SQL parametrizado, projeção explícita, sem N+1. Índices
V109 para as três datas + desempate (ocorrência parcial), vínculo e idempotência. Não criar índice
textual/trigram sem evidência. Filtros sempre limitados a período/página. Limites iniciais: página
25, máximo 100 itens, 90 dias, 100 páginas, busca 200 caracteres; não são capacidade de produção medida.

O ensaio local em PostgreSQL 15, tabela temporária com 20 mil registros, identificou um Sort de
1440 candidatos quando havia NULLS LAST explícito, incompatível com a ordem DESC do índice.
Retirar essa cláusula redundante (o intervalo já exclui NULL) permitiu ler 26 linhas diretamente
na ordem do índice. A migration não precisou mudar. CancelamentosRepositorioJdbcTest confere a
ordem do SQL efetivamente emitido; CancelamentosPersistenciaIT verifica plano na tabela real com
RLS e volume sintético. O microensaio não comprova latência nem capacidade de produção.

CancelamentoRegistrado somente em nova gravação; listener AFTER_COMMIT despacha executor separado
bounded (fila-avisos inicialmente 100), seguindo o relay de presença. Invalidação **sem conteúdo
ou IDs de lead** para usuários ativos com capacidade vigente, pelo Redis/backplane pessoal atual.
Não publica comando em RabbitMQ nem evento de negócio em fila fora de outbox.

Aviso é best-effort: Redis falhando não desfaz ingestão; log sanitizado, foco/reconexão/refetch
recuperam snapshot. Sem garantia de entrega durável, polling, toast ou som. Frontend invalida só
cancelamentos; não cancela refetch em voo por rajada. Evento durante leitura pode aguardar foco
ou reconexão. Cache inclui usuário/papel/revisão/filtros; sem cache Redis de dados de cancelamento.
Não altera caminho de mensagens, outbox, pool do chat, responsabilidade ou provedores.
A validação do lead reutiliza sua Specification; a conferência do par com atendimento usa
`SELECT EXISTS` no pool geral, na mesma transação com RLS. Não reutiliza o repositório de
atendimento ligado ao pool reservado do chat. Há uma barreira arquitetural de teste para esse
qualificador, além dos casos HTTP de vínculo válido, inexistente, parcial e incompatível.

## Retenção, migration e ativação

Política desta entrega: somente os dados mínimos acima, registros imutáveis, **sem expiração/purga
automática**. Chaves/eventos precisam sobreviver a retries tardios. Retenção operacional/legal com
expurgo/anonimização exige procedimento separado autorizado, preservando recibos de idempotência.
Não inventar prazo em dias sem responsável; revisar antes de uso continuado com dados reais.
Não se afirma que uma política legal de retenção já foi aprovada.

V109 conferida em origin/main (última V107) e branch (V108); nenhuma migration aplicada editada.
Conferir de novo antes de merge. Runner de migrations existente deve executar V108/V109 pelo
procedimento de release antes do backend que valida schema. Não executar SQL manual.

**Dokploy: nenhuma variável nova.** Reutiliza o token interno seguro; nunca enviá-lo em PR/documento.
Depois de CI e homologação com a equipe de Automação, operação pode habilitar a flag só na instância escolhida e
conceder capacidades pela Gestão; demais OFF. Rollback funcional desliga a aba sem apagar histórico
nem interromper ingestão autenticada configurada. Importação histórica é operação futura separada.

## Evidências

CancelamentosPersistenciaIT usa HTTP, PostgreSQL e adaptador reais: replay, concorrência, conflitos,
contrato, RLS, auditoria, datas, filtros/páginas e ausência de efeitos em atendimento/outbox.
CancelamentosIT mantém fixture apenas para guards/falhas da porta, sem fingir prova de persistência.
NovoCancelamentoTest, FiltroCancelamentosTest, OpenApiIT, SchemaMigracoesIT e frontend completam
cobertura. Relatório da PR registra resultados/CI do último SHA. Homologação n8n real é separada.

## Refinamento de apresentação — 19/10/2026

A aba usa linguagem operacional: **Filtrar por**, **Data de registro**, **Data do cancelamento**
e **Data do agendamento**. Registro continua sendo recebimento no CRM; cancelamento desconhecido
continua ausente, sem substituição de data. O campo livre continua **Descrição**, não uma causa
obrigatória. Notas sobre integração/processamento saíram da página e dos detalhes. As chaves
antigas de ajuda permanecem no catálogo para compatibilidade, mas não são renderizadas.

Período/tipo de data são os controles principais. Contato, profissional, tipo e descrição ficam
em **Mais filtros**, com indicador da quantidade efetivamente aplicada. Abrir, recolher e digitar
não consultam a API. Somente Aplicar/Limpar, paginação e mecanismos de atualização já existentes
alteram a leitura. Rascunho e filtros aplicados continuam separados; limpar também volta à primeira
página. Fuso e limite reais aparecem em ajuda contextual associada ao botão por aria-describedby.

O formulário antecipa somente erros do período conforme maximoDias recebido do servidor: datas
obrigatórias, início após fim ou limite inclusivo excedido. Associa mensagem ao grupo de campos,
retorna foco à data inicial e preserva a consulta anterior. Não altera limite, fuso ou validação
obrigatória no backend; não inicia outra requisição inválida para explicar o erro.

A listagem tem largura de leitura limitada, sem alterar o shell. Tipo aparece abaixo do contato.
Nomes e descrições têm prévia de duas linhas; detalhes preservam conteúdo completo e quebras,
com rolagem só no corpo do modal e ações sempre visíveis, inclusive com descrição longa no celular.
Abaixo do recorte lg (1024 px) a tabela vira cartões, em duas colunas a partir de md e uma no
celular. Esse breakpoint é local à listagem, sem modificar navegação ou outros módulos.

Vazio por período e busca sem resultados têm mensagens distintas, sem inferir total da instância.
Primeira página vazia e página única sem próxima não exibem paginação. Página posterior vazia
ou com erro preserva a volta. Somente temProxima e os limites reais controlam Próxima; não há
total global inventado. Loading tem placeholders visuais, não registros ou indicadores fictícios;
erro continua erro, com retry explícito. O aviso de vínculo com atendimento só aparece após
detalhe carregado, nunca durante loading/erro. Referências e autorização não mudaram.

Textos vêm do catálogo padrão e dos defaults compatíveis do schema; personalizações da instância
continuam prioritárias. Não há CSS global, fonte, token, biblioteca ou requisição nova. Nenhuma
mudança em ingestão, contrato, persistência, idempotência, retenção, flag, capacidades ou RLS.
**Dokploy: nenhuma ação adicional.** Não houve deploy/alteração de produção.

Capturas antes/depois, cenários e limites da validação: [evidências do refinamento](assets/cancelamentos-refinamento/README.md).
