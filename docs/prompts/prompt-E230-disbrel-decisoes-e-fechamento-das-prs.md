Prompt E230 — Disbrel: decisões tomadas e fechamento das PRs #294, #296–#300

Número E230 provisório: confirmar o próximo livre em docs/prompts/. Executor: Claude Code (Sonnet 5.5, esforço alto). Produção. Não faça deploy, não aplique script em produção, não crie secret. Este prompt não pede funcionalidade nova: registra as decisões do responsável, ajusta as PRs abertas ao que foi decidido e prova o que falta provar. Vale o protocolo anti-alucinação do E229 (§0): todo nome citado foi achado por git grep, toda afirmação vem rotulada verificado (arquivo:linha) / inferido / não verificado, e CI verde só com número de run.

1. Decisões do responsável (registradas em 11/10/2026)

Decisões provisórias, todas reversíveis, tomadas pelo responsável do projeto com base na análise do docs/71. As que dependem do cliente final (Disbrel) estão marcadas e não destravam ligar nada em produção.

#	Decisão	Valor decidido	Efeito no código
D-1	Mecanismo de tema/marca por instância	Usar o que já existe (PUT /api/v1/config/marca/{tema,logo,identidade}), sem variável nova	Já refletido na D1 (#294). Nada a fazer
D-2	Sidebar do tema Disbrel	Tema A provisório (conteúdo branco, sidebar vinho). A escolha final é do responsável olhando as capturas	Nenhum. Manter A e B no repositório até a escolha
D-3	Setores por usuário	Um setor por usuário; vínculo em tabela separada com UNIQUE(usuario_id) removível	Conferir que #296 é assim
D-4	"Atendente vinculado"	O último humano que assumiu ou recebeu o atendimento do lead (não necessariamente quem finalizou). Vale só se o usuário está ativo, disponível e em setor que recebe da triagem; caso contrário cai no rodízio do setor, e o novo atendente passa a ser o vinculado	Conferir que #300 implementa exatamente isso; divergência = relatar, não "consertar em silêncio"
D-5 / D-6	Fora do expediente (atribuir a atendente offline; setor sem ninguém)	Não decidido; depende do dono da Disbrel. Nenhum código	Registrar no docs/71 como pendente, com as opções A (só ao vinculado) e B (no início do expediente, entre os online)
D-7	"Sem Retorno"	Sem estado novo: usar o filtro semRetornoDias existente	Conferir que #297 não cria estado novo
D-8	Agenda para o OPERADOR	Manter o OPERADOR fora por enquanto. Se o cliente confirmar que o "Interno" precisa da Agenda: ficha reduzida (nome, telefone, empresa, responsável atual; sem resumo_ia nem notas)	#298 permanece só de caracterização. Não implemente mudança de RLS nem de ficha; registre a opção "ficha reduzida" no docs/71 com o que precisaria mudar
D-11	Login	Continua com a identidade Synapse	Nada
D-12	Diretoria (SUBGESTOR na Disbrel)	Recebe por transferência manual; não entra no rodízio da triagem	Conferir via "recebe da triagem = não" no setor Diretoria; nenhum enum muda
—	Rótulos de papel (Proprietário/Diretoria/Interno)	Aceitar os nomes atuais agora; estender a marca depois, em PR próprio	Registrar como pendência de baixa prioridade; nenhum código agora
—	Itens [a definir], IA, n8n, Canal	Fora do escopo deste repositório/plano	Nada
2. O que fazer
Atualizar o docs/71 e o docs/13 (na #294 ou na PR que fizer mais sentido; diga qual) com a tabela acima como "decisões registradas em 11/10", marcando o que continua em aberto (D-5/D-6, escolha final A/B, o que o cliente precisa confirmar sobre D-8).
Autoauditoria das PRs #296 e #300 contra a tabela, com arquivo:linha:
D-3 e D-4 e D-12 estão implementados como decidido? Liste divergências.
Nenhum teste de integração existente foi editado (TransferenciaAutomacaoIT, CriterioDeRodizioAutomacaoIT, DisponibilidadeParaIaIT, RetornoDoLeadFinalizadoIT, IsolamentoDeAgendaIT)? Prove com git diff --stat origin/main... filtrando esses arquivos. Se algum foi editado, pare e relate.
O texto do SQL do rodízio (as duas consultas e a de contagem que as espelha) muda com tudo desligado? Mostre o diff exato do SQL. Se o texto muda para todos os stacks, diga em que ponto a chave entra e se o plano de execução do caminho desligado pode mudar. Esse diff decide se o EXPLAIN precisa rodar antes ou depois do merge.
O vínculo e o setor não concedem visibilidade de lead a ninguém (cite os testes negativos).
Migrations (#295/V102 → #296/V103 → #300/V104): para cada migration, liste cada comando DDL e diga se ele reescreve a tabela ou pega lock longo (ex.: ALTER TABLE ... ADD COLUMN com default volátil, ADD CONSTRAINT com validação, CREATE INDEX sem CONCURRENTLY em tabela grande). Considere o tamanho real: lead tem ~8 mil linhas, atendimento ~6 mil, mensagem ~100 mil em partições. Diga também qual é a #295 e se a ordem de numeração ainda é a livre na origin/main agora.
#299 (horário por dia): mostre o teste que prova que, sem configuração por dia, o comportamento é idêntico ao de hoje (janela alerta.horario_cliente.* única) e qual fuso o código usa (cite).
Corpos das PRs: trocar "CI: aguardando" pelos números de run reais e pelo head atual de cada PR; listar nelas as decisões que se aplicam.
Plano de mesclagem e deploy (documento curto, não código): ordem de mesclagem respeitando migrations; quais PRs mudam a imagem (frontend/backend) e quais só trazem script/documentação/teste; qual a ação necessária no Dokploy, se houver (esperado: nenhuma); o que verificar após cada deploy (migration aplicada, /actuator/saúde conforme o runbook, tela de login, painel); ordem dos stacks segundo o docs/18 (erp-matriz-hml, disbrel-dbl, fmnaprod-uzapi), um por vez, fora do horário de atendimento; e como reverter cada chave.
EXPLAIN do rodízio: confirme o nome exato e o comando dos scripts docker/operacoes/e229-explain-rodizio-{antes,depois}.sql (3 repetições, menor tempo, como synapse_app em BEGIN/ROLLBACK) e diga, com base no item 2, se rodar antes de mesclar a #300 é obrigatório. Não declare ganho nem "custo desprezível" sem o número.
3. Não-objetivos
Nenhuma funcionalidade nova, nenhuma mudança de RLS, de ficha da Agenda, de enum de papéis, de rótulos, de estado de lead, nem a D3d.
Não mexer em heartbeat, backoff, STOMP, pool, statement_timeout, intervalos de agendadores.
Não mesclar nem marcar como "ready for review" PR nenhuma; isso é do responsável.
Não editar teste existente para fazê-lo passar.
4. Relatório final exigido
Para cada item da §2: resultado rotulado (verificado/inferido/não verificado) com arquivo:linha.
Lista de divergências entre as PRs e as decisões (se houver) e a correção proposta, sem aplicar mudanças de comportamento sem aviso.
Tabela de migrations com o risco de lock de cada comando.
O plano de mesclagem/deploy.
O que ficou sem prova.
5. Opcional, só depois de tudo acima e em PRs separados e pequenos (não misturar)

Bugs preexistentes encontrados na D1, a serem tratados individualmente, cada um com teste: (a) aba ativa da lista de conversas no celular com texto branco sobre fundo claro (lista-conversas.tsx / ui/tabs.tsx); (b) "Remover" tag sem confirmação (/tags); (c) título da aba "Synapse CRM" e a frase com "(Estrutural)" em campanhas, vindos de configuração da instância. Cada um é PR próprio; não os embuta nas PRs da Disbrel.
