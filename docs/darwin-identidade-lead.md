# Darwin AI → identidade na ficha do lead

## Escopo e fonte

Consulta **manual e somente leitura** da Darwin AI; aplicação confirmada de nome/CPF no CRM.
Desligada por padrão em todas as instâncias, sem condição por cliente e sem migration.
Não é cadastro médico, sincronização por mensagem, webhook ou pesquisa por CPF.
Fonte exclusiva verificada em 02/11/2026: [documentação oficial](https://api.getdarwin.ai/).
O API Documentation Checker foi usado para conferir os contratos abaixo, não para acessar contas.

| Operação oficial | Contrato usado |
|---|---|
| `GET /v1/custom-fields` | `X-API-KEY`; array de definições `id`, `name`, `type`. Validar IDs configurados e tipo `string`. |
| `GET /v1/sessions` | `id` obrigatório = destino do contato (telefone no WhatsApp), `channelId` inteiro opcional no provedor, `page` a partir de 1. Array de `sessionId` e `customFields[{fieldId,value}]`; ordem decrescente de sessão; páginas fixas de 100. Só continuar se página cheia. |
| `GET /v1/session/{session_id}` | Detalhe com `id`, `phoneNumber` E.164 ou null e valores `customFields[{fieldId,value}]`. Confirmar ID e telefone antes de permitir aplicação. |
| `GET /v1/agent/smart-fields` | Catálogo, **não valores de um contato**; não consultado por esta integração. |

Os exemplos oficiais de `fieldId` não são uniformes entre o catálogo (ID numérico) e o detalhe
(exemplo textual `lead_score`). Não inferimos equivalência por nome/label: o operador deve confirmar
os IDs reais da conta e que são retornados nos dois recursos. Incompatibilidade retorna erro de
mapeamento, nunca sucesso simulado. Não existem IDs fixos de nome/CPF no código de produção.

## Identidade, candidatos e limites

O telefone vem **exclusivamente** do lead persistido, já normalizado pelo `TelefoneCanonico` do CRM
(dígitos com DDI). Apenas números de 8–15 dígitos, primeiro não zero, são enviados ao contrato.
Não alteramos o cadastro, não tentamos variantes de nono dígito, não usamos `telefone_provedor`,
BSUID ou `customer.id` como telefone. No detalhe, só o `+` inicial pode diferir.
Ausência/identidade insegura não aplica nada. Esta primeira versão exige `DARWIN_CANAL_ID`, embora
opcional na Darwin: escolha conservadora para não misturar canais.

Cada candidato mantém nome e CPF **da mesma sessão**. Pares diferentes (incluindo nome em uma
sessão e CPF em outra) são ambíguos: mostrar candidatos sem botão aplicável. Pares idênticos podem
repetir; apenas a sessão mais recente escolhível recebe uma chamada de detalhe. Não há N detalhes
por página. A janela configurada é explícita na interface quando atingida; não se promete ter
investigado todo o histórico. Ausentes não viram zero; CPF numérico não vira string com zeros inferidos.
CPF aceita 11 dígitos ou máscara padrão, rejeita repetidos e valida os dois dígitos verificadores.
Nome respeita o VO existente. Não se extrai identidade de texto livre, mensagens ou resumo de IA.

## Contrato do CRM e autorização

Todos os endpoints exigem JWT, `clientes.acessar` **e** `contatos.editar`. Consulta e aplicação
usam a visibilidade oficial de leads (Specification com participações + RLS); lead invisível e
inexistente devolvem 404. Não foi ampliado nenhum teto de papel.

* `GET /api/v1/leads/integracoes/darwin`: `{habilitada:boolean}`, sem configuração/segredo e sem HTTP externo.
* `POST /api/v1/leads/{id}/darwin/consultas`: sem entrada de telefone/campos. Retorna estado
  `ENCONTRADO | SEM_RESULTADO | SEM_IDENTIDADE | AMBIGUO`, atuais, candidatos, aviso de janela e
  `confirmacao` somente para um par seguro. **Não persiste**.
* `POST /api/v1/leads/{id}/darwin/aplicacoes`: `{confirmacao,nome:boolean,cpf:boolean}`. Não aceita
  valores arbitrários do navegador. Retorna `{leadId,origem:"DARWIN",camposAlterados}`.

OpenAPI gerado pelos controllers documenta esses endpoints. Falhas são Problem Details, com
`codigo`: recurso desligado 403; confirmação inválida/expirada 400; edição concorrente 409;
recusa da credencial upstream/identidade divergente/resposta incompatível 502; limite/timeout/circuito/
ocupação/configuração 503. **401/403 da Darwin não viram 401/403 de sessão CRM**.

O botão e o CPF de consulta só aparecem na ficha lateral quando a capacidade de edição e a
configuração estão habilitadas. O endpoint de disponibilidade é cacheado por usuário; erro fecha
o controle. Campos vazios são pré-selecionados; existentes exigem seleção explícita, mesmo que
o nome pareça telefone. Cancelar a consulta aborta a espera; nenhum cancelamento repete mutation.
Sucesso invalida ficha/listas existentes, sem F5 ou polling. Erro mantém ficha e conversa.

## Concorrência, auditoria e segurança

Leitura autorizada em transação curta; HTTP executado em `NOT_SUPPORTED` **fora de transação**.
Confirmação HMAC-SHA256 com separação de domínio, vinculada a usuário, lead, telefone, sessão,
valores anteriores, mapeamento/canal e validade (5m por padrão). É recibo assinado, não criptografia;
contém PII já mostrada na prévia: manter somente em memória da tela, nunca URL/localStorage/log.
Usa a API key backend como chave de assinatura; rotação invalida prévias e funciona entre réplicas
com a mesma configuração. Não exige segredo adicional, tabela ou cache distribuído.

Aplicação bloqueia a linha por `PESSIMISTIC_WRITE`, usando **a mesma Specification**, compara
telefone e campos selecionados e chama `AtualizarLeadUseCase` com atualização parcial. Edição
concorrente de campo selecionado retorna 409; campo não selecionado é preservado. Repetição com
o mesmo valor já aplicado é no-op. Revalida autorização na aplicação. Não altera responsável,
participantes, telefone, estado, histórico, resumo ou notas.
`@Auditable` registra `APLICAR_DADOS_DARWIN` / `LEAD_DARWIN`, UUID, usuário, horário, origem e
nomes dos campos alterados; allowlist não registra valores pessoais, telefone ou recibo.

API key somente no backend; URL HTTPS oficial fixa. HTTP loopback só é permitido em perfil
`dev/test` para servidor simulado. Redirects desativados; resposta limitada em bytes; deadline total
inclui leitura completa do corpo. Circuit breaker e bulkhead próprios por réplica, sem espera
e sem retry. Instâncias OFF não inicializam HttpClient nem fazem chamadas Darwin. Sem trabalho
no envio/recebimento, scheduler, outbox ou caminho de mensagens.

## Roteiro operacional — nenhuma ação executada em produção

1. Responsável pela conta obtém credencial pelo canal seguro e confere `/v1/custom-fields`,
   IDs **string** de nome e CPF e canal WhatsApp; verificar amostra controlada de sessão/detalhe.
   Não compartilhar token, telefone/CPF ou URL identificável em logs/tickets.
2. **Somente no Dokploy da Fêmina**, cadastrar `DARWIN_API_KEY`, `DARWIN_CAMPO_NOME`,
   `DARWIN_CAMPO_CPF`, `DARWIN_CANAL_ID`. Conferir os limites opcionais na tabela do README.
   Manter `DARWIN_HABILITADO=false` até homologação autorizada. Nenhuma variável é obrigatória
   para subir as demais instâncias; todas usam defaults opcionais na stack.
3. Após autorização operacional separada para deploy/ativação, habilitar só essa instância.
   Homologar com lead controlado: atual/encontrado, seleção explícita, CPF, cancelamento,
   conflito manual, persistência após reload e negativa para usuário sem acesso.
4. Para desligar, `DARWIN_HABILITADO=false` e redeploy autorizado: não muda dados aplicados nem
   interfere no chat. Demais instâncias permanecem OFF. Nenhum webhook/n8n deve ser cadastrado.

Dependências de homologação real: API key, IDs de campos compatíveis e canal da conta. Testes HTTP
simulados não comprovam dados daquela conta, limites comerciais ou disponibilidade do fornecedor.
Evidências e resultados locais/CI serão registrados no relatório da PR; não fazer merge/deploy automático.

## Evidências e correções adjacentes necessárias

[Screenshots e protocolo da validação real local](evidencias/darwin-identidade/README.md).
O editor inline de nome passa a derivar do cadastro quando não há rascunho: a confirmação
Darwin agora aparece sem F5 e um refetch não apaga digitação manual. Corrigidas chaves de
reconciliação distintas na ficha, incluindo colisão anterior entre Notas/Mídias.
Na CI foi encontrada falha de limpeza no teste de encaminhamento: o trigger V65 atualizava
uma citação que o mesmo DELETE em lote tentava excluir. A fixture desvincula apenas referências
internas ao conjunto descartável antes de remover linhas, sem desativar trigger, mudar migration,
enfraquecer asserções de autorização ou alterar o comportamento do chat.
