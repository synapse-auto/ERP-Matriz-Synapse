# Coleta cadastral assíncrona — Darwin médica

Complementa **#348** (ficha local), sem substituir sua exibição. A PR desta etapa tem como base `codex/dados-cliente-ficha`: mergear #348 primeiro. V117 foi escolhida após conferir `origin/main` (até V115) e a V116 da PR-base. Nenhuma migration aplicada foi editada.

## Contrato verificado em 05/11/2026

Fontes primárias: [referência médica](https://developers.darwin.med.br/docs/), [OpenAPI publicado](https://developers.darwin.med.br/docs/openapi.json), versão 1.0.9; coleção médica completa versionada, igual ao anexo (SHA-256 `72aecfdf6805f9cc17df1266f42e1687499ae146470daa6126f66b23eaff61b1`). Não é getdarwin.ai.

Foram conferidas as 19 operações da coleção: timetables (available, professional, create/update/archive), professionals/locations, schedules (create, fitin, update/delete, list/patient), exames (upload/create), patients (create/update/find/cpf), procedures, insurances. Também foram conferidas as operações adicionais do OpenAPI atual: health/measurements/create e forms (list/info, submissions list/info/create, send). Não executar escritas nem coletar formulários/indicadores clínicos nesta integração.

Somente `GET /api/patients/find/cpf?cpf=000.000.000-00` consulta o cadastro necessário. Autenticação `Authorization: Bearer` exclusivamente servidor-servidor. `schedules/list/patient` e `forms/submissions/list` também exigem CPF; não descobrem paciente por telefone. `submissions/info` exige ID obtido na listagem por CPF: não é uma associação disponível no CRM. Criar um paciente/agendamento para procurar não é permitido. Não existe busca por telefone nem identificador de paciente utilizável nessa coleção. O contrato de cancelamentos/agendamentos da automação não fornece associação cadastral equivalente: nome/telefone/schedule_id não autorizam inferir CPF.

Resposta: objeto único, `cpf`, `name`, `birthDate` (`date-time`, UTC à meia-noite, exemplo **sintético** `1990-05-21T00:00:00.000Z`). `phoneNumber` existe mas não é persistido nem substitui o telefone WhatsApp. Sem CPF igual e válido, nenhum candidato é aplicado. Array, duplicidade de chaves, resposta malformada, data impossível/futura/não civil e corpo excedido são rejeitados. 404 significa paciente inexistente **ou fora do escopo do token**; não é prova de inexistência global.

## Fluxo efetivamente implementado

1. `LeadConsultado` (leitura autorizada da ficha), abertura de atendimento por mensagem e alteração cadastral confirmada solicitam trabalho. O listener apenas entrega ao executor dedicado `darwin-coleta-*`, sem HTTP na thread de mensagens. Requisições repetidas são deduplicadas localmente e por lease persistente por lead/identidade.
2. Em transação curta, `LeadRepositorio` aplica Specification/RLS e lê o cadastro. O serviço de coleta usa o contexto explícito `SERVICO`, não se faz passar por gestor. O cache guarda hash de identidade/origem, token de lease, estados e prazos — nunca CPF bruto, conteúdo da resposta ou credenciais.
3. Fora de transação e sem conexão JDBC retida, o cliente HTTP real consulta a Darwin. São preservados timeout, limite de corpo, ausência de redirects/retries, circuit breaker e bulkhead existentes. Nenhuma consulta a cada renderização; durante o cache uma nova abertura só confere a reserva, sem HTTP externo.
4. Sob bloqueio de linha, compara nome, CPF, telefone e nascimento atuais com o snapshot anterior ao HTTP. Edição concorrente impede a aplicação. CPF retornado precisa coincidir com o CPF conhecido. Nome só é preenchido se vazio ou fallback exato do telefone. Nascimento só se ausente, validado pelo metadado `data_nascimento` DATA existente. Nome/nascimento válidos divergentes são preservados e sinalizados para revisão; campos ausentes não conflitantes podem ser preenchidos. CPF já persistido é validado, não substituído. Não altera e-mail, responsável, participantes, histórico ou roteamento.
5. Resultado e lease são concluídos no mesmo commit da aplicação. O sinal cadastral alimenta a **outbox canônica existente**, depois Redis/WebSocket autorizado; a ficha e o estado da coleta são invalidados pelo ID exato. Falhas também sinalizam o estado, sem apagar dados locais. Auditoria `ENRIQUECER_CADASTRO_DARWIN`, vinculada ao UUID do lead, sem captura de dados pessoais.

Não há varredura/importação da carteira, novo polling ou retry programado. Se o pool estiver ocupado, solicitação automática é descartada sem bloquear chat; próxima atividade tenta novamente. Tentativa manual recebe indisponibilidade quando não há capacidade. Crash deixa lease temporária: depois do prazo, nova atividade/tentativa recupera a coleta. Isso não é promessa de fila durável com retry independente de atividade. A comunicação em tempo real depende de um atendimento aberto, como na #348; histórico fechado recupera pela releitura/reconexão, não recebe sinal de atendimento inexistente.

## Ficha e contratos CRM

Os campos locais continuam visíveis mesmo sem Darwin. A ficha habilitada por `dados_cliente_ficha` oferece estado e **Atualizar dados cadastrais**, sem CPF digitado nem modal. Exige `clientes.acessar`, `contatos.editar` e visibilidade do lead. A consulta automática pode preencher cadastro autorizado ao serviço; a permissão de editar controla a ação manual, não concede acesso global.

- `GET /api/v1/leads/{id}/darwin/coleta`: só estado, sem HTTP Darwin.
- `POST /api/v1/leads/{id}/darwin/coleta`: 202, tentativa assíncrona, sem corpo/CPF, respeitando lease/cooldown. 202 não prova coleta concluída. JWT, mesma autorização/Specification da consulta manual. Lead invisível/inexistente: 404. Falta de capacidade do executor: 503 com código sanitizado.
- Estados: AGUARDANDO, PROCESSANDO, IDENTIFICACAO_PENDENTE, ATUALIZADO, DADOS_PARCIAIS, REVISAO_NECESSARIA, NAO_ENCONTRADO e falhas sanitizadas (AUTENTICACAO, LIMITE, INDISPONIVEL, RESPOSTA_INVALIDA, IDENTIDADE_DIVERGENTE, CONFIGURACAO_INCOMPLETA). Lease expirada aparece como AGUARDANDO.
- Sem CPF, nenhuma chamada: IDENTIFICACAO_PENDENTE. Menor complemento necessário: automação persistir **CPF comprovado**. O contrato existente `POST /internal/v1/ev05/leads/{id}/preenchimento` exige `X-Synapse-Token`, `Idempotency-Key` e atendimento **EM_ATENDIMENTO**, conforme o controller atual; não serve, sem extensão contratual separada, para preencher um lead que permaneça exclusivamente em IA. O evento cadastral dispara a consulta, sem mudar n8n para os leads que já têm CPF. Esta etapa não amplia a autorização nem a pré-condição do EV05. Um ID arbitrário ou nome não substitui esse dado.

## Operação — sem deploy automático

Na Fêmina, manter `DARWIN_HABILITADO=true`, `DARWIN_URL=https://developers.darwin.med.br`, `DARWIN_TOKEN` real exclusivamente no Dokploy. O Compose já repassa essas variáveis. Demais instâncias: `DARWIN_HABILITADO=false` (default), nenhum HTTP nem tarefa de coleta.

Variáveis opcionais novas, repassadas no **mesmo** `docker/dokploy-stack.yml`, `.env.example` e README:

| Variável | Default | Finalidade |
|---|---|---|
| DARWIN_COLETA_HABILITADA | true | Desliga só coleta; depende sempre de DARWIN_HABILITADO e configuração válida. |
| DARWIN_COLETA_CACHE | 24h | Reconsulta automática por atividade no máximo uma por identidade/prazo. |
| DARWIN_COLETA_INTERVALO_ERRO | 5m | Cache de erro/ausência de identidade e intervalo entre tentativas manuais. |

Esses prazos são escolhas conservadoras **configuráveis do CRM**, não limites atribuídos ao fornecedor. Nenhuma env nova obrigatória. Antes do deploy operacional aplicar a migration nova pelo runner existente e conferir o provisionamento da #348: flag `dados_cliente_ficha` e metadado DATA `data_nascimento`. Ausência do metadado não gera escrita JSON sem validação: informa configuração incompleta. Nenhuma alteração de proxy, canal, n8n ou credencial WhatsApp é necessária.

## Evidências e limites

`ColetaDarwinIT` exercita PUT real de CPF e GET real de ficha preexistente -> executor -> HTTP real com Bearer/CPF formatado contra servidor simulado -> PostgreSQL -> GET de ficha após coleta/reload. Inclui divergência/array, ausência de CPF, dados manuais, concorrência durante HTTP, timeout/401/403/429/5xx, ausência de loop, cache e nova tentativa após intervalo. O spy verifica que o HTTP acontece sem transação. `DarwinHttpAdapterTest` mantém negativos de configuração OFF/origem/limites/identidade. Frontend testa estado, ação sem CPF, troca rápida de lead, erros e invalidação por evento canônico.

`ColetaDarwinCacheRlsIT` tenta ler/alterar o cache por SQL com ATENDENTE, OPERADOR, GESTOR e ADMINISTRADOR e tenta inserir uma reserva forjada em outro lead. Confere que o executor SQL não é proprietário nem possui SUPERUSER/BYPASSRLS, exige recusa 42501 na inserção e verifica o caminho positivo exclusivo do serviço. Essa prova é independente da Specification/negação HTTP.

A inspeção da tela real encontrou uma regressão preexistente no ciclo de efeitos do StrictMode: a limpeza do último consumidor removia o singleton, mas a remontagem conservava a instância no `useState` sem reinscrevê-la. Um consumidor tardio da ficha criava outra conexão e ficava desconectado, embora o layout atualizasse a lista. `conexao-compartilhada.test.tsx` reproduziu a falha antes da correção; a primeira remontagem agora reinscreve a instância ativa, sem nova conexão/polling. A regressão é verificada pelos hooks reais, além dos testes de frames e autorização existentes. Não confundir isso com homologação externa.

Homologação externa não executada: nenhum paciente real foi consultado. Não há descoberta de CPF, busca por telefone, importação clínica nem escrita Darwin. Testes com transporte simulado não demonstram disponibilidade, escopo ou conteúdo da credencial produtiva.
