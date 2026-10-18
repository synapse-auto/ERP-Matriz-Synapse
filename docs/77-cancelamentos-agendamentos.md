# Cancelamentos de agendamentos — leitura preparada, integração pendente

## Estado e fronteira

Esta entrega implementa navegação, autorização, consulta genérica, filtros e detalhes. **Não conecta
uma fonte real e não comprova cancelamentos da Fêmina.** A PR permanece draft até confirmação e
homologação da integração. Não há cancelamento, recuperação, reagendamento nem envio de mensagem.

Auditados `docs/n8n/agendamentos.md` e `docs/n8n/sql/001-agendamento-evento.sql`: são proposta de
provisionamento **externo**, não prova de instalação. `automacao_agendamentos.agendamento_evento`
e `vw_cancelamentos_agendamento` não existem no schema do CRM e não serão duplicados aqui.
`CANCELADO` é distinto de `REAGENDAMENTO_SOLICITADO`; a leitura não transforma o segundo no primeiro.
Nenhuma consulta externa, credencial de n8n ou nova escrita de negócio foi adicionada.

## Arquitetura e autorização

- Read model genérico em `crm-relatorios`, sem módulo clínico ou condicional por cliente.
- Porta `LeituraDeCancelamentos`: lista/detalhe recebem obrigatoriamente o usuário autenticado.
  O adaptador futuro precisa aplicar escopo **antes** de paginar. Para ATENDENTE/OPERADOR, somente
  correlação exata validada e visível pela Specification/RLS; sem correlação, excluir. Gestão
  segue seu alcance existente. Não existe bypass por ter a capacidade da aba.
- Único adaptador em produção: `LeituraDeCancelamentosNaoConfigurada`, que falha explicitamente,
  sem rede/banco externo e **sem substituir ausência de configuração por resultado vazio**.
- Flag `cancelamentos` OFF por padrão (V108, número conferido em `origin/main` 0bc9f64).
- Capacidade `cancelamentos.ver`, módulo `cancelamentos`, teto somente VER. GESTOR/SUBGESTOR têm
  padrão concedido; ADMINISTRADOR mantém perfil fixo convencional. ATENDENTE/OPERADOR começam
  SEM_ACESSO/OFF; concessão explícita pela Gestão não altera visibilidade de leads nem escrita.
  SUBGESTOR não pode delegar esta capacidade, conforme a alçada atual. Exceções individuais
  continuam dependentes da flag existente `gestao_excecoes` para edição.
- Guarda de rota `ExigeCapacidade`, menu e `@PreAuthorize` nos três casos de uso. Flag ausente ou
  desligada também nega acesso direto. Nenhuma autorização fica somente no frontend.
- Links para lead/atendimento **indisponíveis nesta etapa**, com explicação. Não são inferidos por
  telefone/nome. Mesmo no preview não há link operacional fictício. A validação real dessa
  correlação é requisito do futuro adaptador, não uma garantia demonstrada pelo fixture.

## Contrato CRM autenticado (não é contrato de fornecedor)

JWT normal, namespace `/api/v1/cancelamentos`:

| Método/rota | Contrato |
|---|---|
| GET `/configuracao` | `fusoHorario`, `inicioPadrao`, `fimPadrao`, `tamanhoPagina`, `maximoPagina`, `maximoDias`, `maximoPaginas`, `maximoBusca` |
| GET `/` | `inicio`, `fim` obrigatórios (`yyyy-MM-dd` locais inclusivos); `base=REGISTRO\|AGENDAMENTO`, `profissional`, `tipo`, `nome`, `descricao=TODAS\|COM_DESCRICAO\|SEM_DESCRICAO`, `pagina` zero-based, `tamanho` |
| GET `/{id}` | Identificador opaco técnico, ASCII alfanumérico/`_`/`-`, até 128 caracteres; não é telefone/nome |

Lista: `{itens: [...], temProxima: boolean}`. Sem total global nem consulta COUNT. Cada item/detalhe:
`id`, `agendadoPara`, `registradoEm`, `contatoNome`, `profissionalNome`, `tipoAgendamento`, `descricao`.
Campos não fornecidos são `null`, sem horários/motivos inventados. Dados de descrição são texto
escapado, preservando quebras no detalhe. A lista limita a apresentação a três linhas.

Datas são instantes ISO 8601; filtro converte limites locais para `[inicio, proximoDia)` em
`synapse.tenant.timezone`, incluindo dias de mudança de offset. A UI usa o mesmo fuso retornado.
`registradoEm` significa **registro na fonte**, nunca ocorrência efetiva do cancelamento ou
ingestão no CRM. A UI explicita isso. `agendadoPara` é o horário do agendamento, em coluna separada.
Se o fornecedor só tiver ingestão ou não tiver data confiável de registro, não preencher
`registradoEm` com outro conceito: adaptar o contrato depois da confirmação.

Filtros de profissional/tipo/nome são entradas textuais combinadas no servidor (sem catálogos
históricos não limitados). O futuro adaptador deve implementar substring literal normalizada,
escapando curingas de SQL, sem interpolação. Busca por telefone **não implementada**: faltam
contrato e correlação seguros. Descrição ausente inclui nula/branca; alinhar essa semântica com a
fonte (o SQL proposto usa `btrim`, que sozinho não equivale a `String.isBlank` para novas linhas).

Ordem exigida da porta: data da base DESC NULLS LAST, identificador estável DESC. Filtros e escopo
antes da paginação; ler no máximo tamanho+1 para `temProxima`. O fixture testa essa convenção,
**não comprova plano de consulta externo**. Alterações na fonte entre páginas podem deslocar
offsets; snapshot/cursor só pode ser definido quando IDs/semântica forem confirmados.

| Situação | Resposta |
|---|---|
| Consulta bem-sucedida sem registros | 200, `itens: []`, `temProxima: false` |
| Integração não configurada | 503 Problem Details, `codigo=INTEGRACAO_NAO_CONFIGURADA` |
| Fonte temporariamente indisponível | 503 Problem Details, `codigo=FONTE_INDISPONIVEL` |
| Sem autenticação / sem capacidade ou flag | 401 / 403, fluxos normais preservados |
| Filtros/limites inválidos | 400 Problem Details, sem ecoar conteúdo sensível |
| Detalhe inexistente ou fora do escopo | 404 indistinguível |

Não há polling ou retry automático da fonte. Retry de leitura é explícito pelo usuário. Trocar
filtros cancela a leitura anterior via AbortSignal e usa nova chave, sem placeholder de outro
período. Cache de interface inclui usuário, papel, revisão de permissões e filtros; troca de
identidade/configuração remonta a consulta. Não há cache Redis de cancelamentos.

## Limites e plano de consultas

Configuração `synapse.cancelamentos.*` em `application.yml`: página inicial 25, máximo 100 itens;
período máximo 90 dias inclusivos, até 100 páginas e 200 caracteres por busca. São limites
operacionais iniciais conservadores, **não capacidade medida da fonte**. A tela começa no mês
atual até hoje, calculado no fuso do servidor, sem adivinhar o relógio do navegador. Recortes
maiores devem ser divididos; nunca buscar toda a história para filtrar localmente.

O adaptador definitivo exige timeout, circuit breaker, concorrência/pool isolados do chat,
limites de payload/descrição e diagnóstico sanitizado. Nenhum deles é simulado por uma chamada
externa nesta entrega: o adaptador atual não realiza I/O. Antes de conectar, medir volume,
latência fria/quente e plano com dados representativos. Não há evidência para propor índice.
O índice externo proposto `(acao, criado_em DESC)` não garante bom plano para profissional,
tipo, nome ou data de agendamento, nem ordenação por desempate; medir antes de alterar escrita.

## Confirmações objetivas para Dylan

1. Onde a fonte realmente existe (API/banco próprio da integração), versão instalada e processo
   de provisionamento; não enviar senha/token/exportação clínica integral.
2. Schema/campos reais e amostra **sintética/sanitizada**: identidade estável do registro/evento,
   identidade de agendamento, tipo de cada data, offsets/fuso e nulabilidade.
3. Histórico de eventos `CANCELADO` ou estado atual do agendamento? O que fazer após confirmação
   posterior, cancelamento repetido e reagendamento solicitado? Não escolher silenciosamente.
4. Mecanismo de entrega/acesso aprovado e autenticação de menor privilégio; retenção, volume,
   consistência, paginação/limites e autorização da fonte.
5. Se existem `leadId`/`atendimentoId` canônicos e como validar o par no CRM, inclusive inexistente,
   sem acesso, ciclo antigo e ausência. Nome/telefone não são correlação autorizada.
6. Definição de descrição/motivo e data de **registro**, distinguindo ocorrência e ingestão.

Depois dessas respostas: avaliar API autenticada ou projeção **genérica** local por contrato
interno versionado. Não dar ao n8n acesso SQL ao CRM e não conceder leitura do banco n8n por
suposição. Adaptador definitivo, enforcement de visibilidade com dados reais, plano de execução,
falhas de rede e homologação ponta a ponta permanecem pendentes.

## Habilitação futura e Dokploy

**Não habilitar agora.** A migration apenas registra flag falsa; não concede permissão nova a
ATENDENTE/OPERADOR. Depois do contrato, adaptador, CI e homologação, ativar pelo procedimento
operacional versionado de feature flags somente na instância escolhida. Gestão configura os
perfis/exceções autorizados; manter demais instâncias OFF. Não basta ligar a flag para haver dados.
Rollback funcional: desligar flag, sem apagar histórico/configuração. Nenhuma variável nova ou
alteração obrigatória no Dokploy nesta etapa; nenhum deploy realizado.

## Evidências

Automatizadas: `FiltroCancelamentosTest`, `LeituraDeCancelamentosNaoConfiguradaTest`,
`PoliticaDePermissoesTest`, `CancelamentosIT` (HTTP real, Postgres real para permissões;
`FonteCancelamentosDeTeste` somente em `src/test`), testes de rota/consulta/catálogo/menu.
Dados sintéticos não são incluídos no JAR de produção. Screenshots e resultados finais serão
registrados no complemento de validação antes da entrega. Não são homologação com fornecedor.
