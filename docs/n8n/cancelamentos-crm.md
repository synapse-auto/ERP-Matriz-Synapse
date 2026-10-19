# Dylan — enviar cancelamentos ao CRM (v1)

Implementado em CancelamentosInternalController, testado por HTTP real em CancelamentosPersistenciaIT.
Não fazer INSERT no banco do CRM. O histórico externo de outras ações é independente desta aba.

## Requisição

```http
POST /internal/v1/cancelamentos
X-Synapse-Token: <credencial de serviço existente da instância, como segredo do n8n>
Idempotency-Key: agenda:evento:cancelamento-00042
Content-Type: application/json
```

Exemplo **sintético**, com vínculo canônico conhecido:

```json
{
  "origem": "integracao-agenda",
  "eventoExternoId": "cancelamento-00042",
  "agendamentoExternoId": "agendamento-01234",
  "leadId": "12345678-1234-4234-8234-123456789012",
  "atendimentoId": "abcdefab-1234-4234-8234-123456789012",
  "agendadoPara": "2026-10-25T11:30:00-03:00",
  "canceladoEm": "2026-10-18T14:20:00-03:00",
  "contatoNome": "Contato de demonstração",
  "profissionalNome": "Profissional de demonstração",
  "tipoAgendamento": "Retorno",
  "descricao": "Cancelamento informado pelo contato.\nSem dados clínicos."
}
```

Substituir UUIDs ilustrativos por IDs reais **com relação válida** na homologação; não copiar estes
UUIDs para produção. Sem vínculo confiável, omitir **ambos** leadId/atendimentoId (ou ambos null).
O CRM aceita sem vínculo e restringe leitura à Gestão autorizada. Não correlacionar por contato.

| Campo | Obrigatório | Limite/semântica |
|---|---|---|
| origem | Sim | 80; identificador técnico estável da integração |
| eventoExternoId | Sim | 200; ocorrência, não ID do agendamento |
| agendamentoExternoId | Sim | 200; agendamento que pode ter várias ocorrências |
| Idempotency-Key (header) | Sim | 200; chave estável preservada nos retries |
| agendadoPara | Sim | ISO 8601 com offset, como -03:00 ou Z |
| canceladoEm | Não | Mesmo formato; somente se conhecido; ausente permanece null |
| leadId e atendimentoId | Não | UUIDs, par válido completo ou ambos ausentes |
| contatoNome, profissionalNome, tipoAgendamento | Não | Até 200 cada, somente apresentação |
| descricao | Não | Até 2000; motivo mínimo, sem prontuário/payload/histórico |

Identificadores/chave: ASCII, primeiro caractere alfanumérico, demais alfanuméricos ou `._:/-`.
Não enviar espaços, nome, telefone ou segredo. Campos desconhecidos são recusados, inclusive
clinica_id, payload, recebidoEm e resumo. Datas sem offset são inválidas; normalizadas em UTC/micros.
Espaços marginais dos textos são removidos, branco vira null, LF/CR/TAB só no motivo.

## Idempotência e respostas

Usar ID **estável da ocorrência de origem**, prefixado por operação, como chave. Se não houver ID
na fonte, gerar uma única vez na coleta e persistir no estado da integração **antes** do POST;
preservar evento e chave nas retomadas. Não usar execução/tentativa, relógio atual, telefone ou
somente agendamento. Não foi presumida garantia de ID do fornecedor: validar a estratégia antes
de ligar. Eventos diferentes do mesmo agendamento têm eventos/chaves diferentes.

- **201**: novo registro; `{ "id": "<UUID do CRM>", "recebidoEm": "<ISO UTC>", "repetido": false }`.
- **200**: replay idêntico; mesmo ID e recebimento, repetido=true. Tratar como sucesso.
- **409** Problem Details, codigo=CONFLITO_CANCELAMENTO: chave/evento reutilizado com dados ou
  par chave/evento incompatível. Não sobrescreve. Parar e revisar, nunca contornar com chave nova.
- **400**: contrato, limites, chave, data ou vínculo inválidos. Corrigir/revisar, não repetir cegamente.
- **401**: token ausente/inválido; JWT humano não substitui. Conferir referência segura, sem imprimir
  segredo ou desativar autenticação.
- **5xx/timeout/transporte**: resultado pode ser incerto; repetir somente mesmos dados e mesma chave
  com backoff limitado pela política existente do workflow. Sem loop imediato; 400/401/409 não têm
  retry automático. Nunca reprocessar histórico para testar a rota.

Replay não pode alterar nome, descrição, horário ou vínculos depois da gravação. Só enviar eventos
de cancelamento; confirmação/pedido de reagendamento não são cancelamentos.

## Homologação

Não dispara mensagens, reagendamento, lembretes, finalização ou transferência. Flag cancelamentos
controla **visualização**, não ingestão autenticada; manter OFF até homologação autorizada.
Nenhum workflow ou produção foi alterado nesta entrega.

Validar um evento sintético: POST → 201 → lista/detalhe autorizado → replay 200 → conflito 409 →
atualização da tela. Conferir sem vínculo, token inválido e usuário sem acesso. Diagnóstico só com
IDs técnicos/horários/status; não exportar nomes, motivo, tokens, payload ou execução n8n completa.
Revisar retenção do motivo antes de dados reais. Importação antiga exige operação separada autorizada.

**Dokploy: nenhuma variável nova.** Usar token de serviço existente e release/runner de migrations,
não SQL manual. [Arquitetura, permissões e retenção](../77-cancelamentos-agendamentos.md).
