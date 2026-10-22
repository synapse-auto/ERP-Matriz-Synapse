# Cancelamentos — campos, datas e identificacao (21/10/2026)

Base inspecionada: main `97f2849`, incluindo refinamento #314. Sem acesso a VPS/n8n, sem deploy.

## Rastreio de campos

| Dado | Origem e caminho | Decisao |
|---|---|---|
| Agenda | `agendadoPara` → `agendado_para` → projecao → UI | Data e hora da agenda, fuso da instancia |
| Cancelamento | `canceladoEm` → `cancelado_em` → projecao → UI | Sem fallback para recebimento; ausente = Nao informado |
| Registro | relogio CRM → `recebido_em` | Somente detalhe; ordenacao/filtro continuam explicitamente pela base selecionada |
| Medico | `profissionalNome` → validacao/normalizacao → `profissional_nome` → projecao → UI | Contrato ja preservava o campo; nenhum valor inferido |
| Tipo | `tipoAgendamento`, exemplo contratual Retorno | Nao rebatizar como Especialidade |
| CPF/telefone | `lead.cpf`/`lead.telefone` do UUID vinculado autorizado | Projecao minima so no detalhe; dados atuais, nao snapshot |

A listagem anterior mostrava recebimento na quarta coluna, nao a ocorrencia do cancelamento.
Isso foi corrigido sem mudar periodo, paginacao, indices, ordem SQL ou historico.

## Autorizacao e custo

O detalhe primeiro valida capacidade/flag e RLS do cancelamento, incluindo lead e atendimento.
Somente depois a porta de composicao consulta CPF/telefone usando a mesma
`VisibilidadeLeadSpecification.comParticipacao` da ficha. Nao ha contexto de Agenda nem de servico
na leitura humana. `atendimentos.ver` tambem e conferida. A consulta nao carrega entidade inteira,
notas, resumo nem JSONB. Ausencia de vinculo/dados nao impede consultar o registro.

Nao ha N+1 na lista, polling, acesso a fornecedor, cache Redis pessoal novo nem alteracao no chat.
Cache do detalhe continua separado por usuario/papel/revisao e ID, sem placeholder de outro registro.
CPF/telefone nao entram no contrato de ingestao, fingerprint, URLs, auditoria ou invalidacao.
Ingestao e fingerprint nao foram modificados: replays antigos conservam o mesmo contrato.

## Diagnostico real pendente

Executar `docs/diagnosticos/cancelamentos-campos-sanitizados.sql` com periodo informado pelo operador.
Ele devolve somente IDs, horarios e presenca dos campos. Compartilhar IDs por canal privado.
Se `profissional_informado=false`, o registro persistido nao possui medico. Nao e possivel distinguir
retroativamente campo omitido/null/branco (normalizados em null) sem evidencia sanitizada da origem.
Se true e a UI real nao mostrar, conferir SHA implantado, resposta do GET autorizado e catalogo.
Nao exportar nomes, CPF/telefone, motivo, token, URL privada ou payload.

## Para Automacao

Enviar `profissionalNome` no nivel superior, com nome do profissional do agendamento, ate 200
caracteres. Campo ausente/null/branco nao gera nome no CRM. Chaves alternativas como `medico` sao
recusadas com 400 pelo contrato fechado. Nao copiar nome de atendente nem extrair do motivo.
Enviar `canceladoEm` apenas se a origem conhece esse instante, com offset. Nao usar horario do POST.
Nao alterar corpo/chave de evento ja aceito: registro e imutavel e replay divergente responde 409.

Especialidade: os campos customizados existentes pertencem ao lead, nao a este evento imutavel.
Foi solicitada decisao sobre cadastro atual versus snapshot do agendamento antes de evoluir
persistencia/contrato. Nenhuma coluna clinica, reinterpretacao, backfill ou extensao improvisada.

## Configuracao

Sobreposicao de textos da Femina em `docker/provisionamento/instancias/femina/`.
Nao foi aplicada em producao. Nenhuma migration nem variavel nova nesta etapa.
