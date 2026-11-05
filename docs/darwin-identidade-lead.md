# Darwin médico — consulta cadastral por CPF conhecido

> A coleta **assíncrona real**, seus gatilhos, cache, preenchimento de nascimento e limites estão em
> [coleta cadastral Darwin](darwin-coleta-cadastral.md). Este documento descreve a consulta manual anterior.

> A consulta manual não é a entrega de “Dados do cliente”. Com `dados_cliente_ficha` habilitada,
> Atendimentos exibe CPF/nascimento locais junto do nome e telefone existentes, sem esse modal
> e sem depender da Darwin. Ver [origens e contratos da ficha](dados-cliente-ficha.md).

## Correção do fornecedor e fonte primária

A Fêmina utiliza **Darwin médico (`darwin.med.br`)**, não `getdarwin.ai`.
A implementação e as instruções da PR #340 apontavam para outro produto e foram substituídas.
Fonte desta correção: coleção fornecida `Darwin-API.postman_collection.json`, auditada
em 03/11/2026, SHA-256
`72aecfdf6805f9cc17df1266f42e1687499ae146470daa6126f66b23eaff61b1`.
A coleção original não é versionada: seus exemplos podem conter dados pessoais/credenciais.
Nenhuma requisição foi executada a partir dela ou contra conta produtiva.

| Contrato documentado | Uso no CRM |
|---|---|
| Base `https://developers.darwin.med.br` | Origem HTTPS permitida; sem path, query, userinfo ou porta alternativa. |
| `GET /api/patients/find/cpf?cpf=000.000.000-00` | Consulta de CPF conhecido; normalização e verificação dos dígitos antes de enviar a máscara exigida. |
| `Authorization: Bearer <token>` | Segredo somente no backend; sem `X-API-KEY`. |
| 200: objeto raiz com `name`, `cpf` e `email` | Nome/CPF já eram aproveitados; e-mail passa a ser opcional, validado e apresentado para confirmação. CPF deve ser válido e exatamente igual ao consultado. |
| Campos não preenchidos: `null` | Nome ausente não é inventado nem aplicado. CPF ausente/inválido impede qualquer aplicação. |
| 404 | Nenhum paciente **no escopo do token**; não prova inexistência global do paciente. |
| 400 documentado | Erro sanitizado; não encaminhar corpo do fornecedor ao navegador. |

O token tem escopo de unidade/profissional conforme a descrição da coleção. Dados clínicos,
endereço, telefone, nomes de familiares e demais propriedades retornadas são descartados.
O contrato não documenta ID de sessão/paciente nesta resposta; nenhum ID é deduzido do CPF.

### Limitação comprovada: não há descoberta de CPF pelo telefone

Foram inventariadas as **19 operações** da coleção. Em pacientes, existem criação, atualização
e consulta por CPF. Não existe consulta por telefone documentada nessa fonte. O campo
`phoneNumber` na resposta não constitui endpoint de pesquisa.
**O CRM não descobre CPF pelo telefone.** O operador precisa conhecer o CPF ou tê-lo na ficha.
Criação/atualização de paciente não é usada como consulta; não há escrita no Darwin, variantes
de telefone, IDs de campos personalizados, catálogo de campos ou paginação de sessões.

## Fluxo da ficha e contrato CRM

1. Na ficha lateral, usuário autorizado abre **Consultar na Darwin**.
2. CPF da ficha pré-preenche o formulário; se ausente, operador informa CPF conhecido e confere
   que pertence ao lead. Abrir o modal não consulta o provedor.
3. **Buscar por CPF** faz uma leitura; digitar/consultar não salva CPF nem nome.
4. Prévia mostra atuais/encontrados. Campos existentes exigem seleção explícita;
   campos vazios podem vir selecionados, mas **Aplicar seleção** continua obrigatório.
5. Confirmação aplica apenas os campos selecionados; cancelar/erro não altera a ficha.
   Sucesso invalida ficha, lista de atendimentos e agenda, sem F5 ou polling novo.

Endpoints existentes, documentados no OpenAPI dos controllers:

| Endpoint | Contrato |
|---|---|
| `GET /api/v1/leads/integracoes/darwin` | `{habilitada:boolean, estado}`; não expõe configuração e não chama fornecedor. |
| `POST /api/v1/leads/{id}/darwin/consultas` | Corpo opcional `{cpf:string}`. Sem CPF no corpo, utiliza o CPF persistido; se nenhum disponível, `SEM_IDENTIDADE`, sem HTTP externo. CPF inválido retorna 400. |
| `POST /api/v1/leads/{id}/darwin/aplicacoes` | `{confirmacao,nome:boolean,cpf:boolean,email:boolean}`; não aceita valores arbitrários. Retorna `{leadId,origem:"DARWIN_MEDICO",camposAlterados}`. Não consulta novamente o fornecedor. |

Mantida compatibilidade estrutural da prévia: `sessaoId` fica **null/deprecated**,
`limiteAtingido=false`, e enum legado `AMBIGUO` não é produzido pelo adaptador médico.
`ENCONTRADO`, `SEM_RESULTADO` e `SEM_IDENTIDADE` continuam disponíveis. O corpo antigo sem CPF
agora só consulta se a ficha já tiver CPF; nunca recorre silenciosamente ao telefone.
Catálogos antigos recebem textos de contexto médico por versão de contrato, preservando rótulos
genéricos personalizados; não continuam instruindo consulta por telefone/sessão do outro produto.

## Autorização, concorrência e auditoria preservadas

JWT + `clientes.acessar` + `contatos.editar` em consulta e aplicação; Specification com
participações e RLS permanecem iguais. Lead inexistente/invisível retorna 404. Não ampliamos
tetos de papel ou visibilidade. A entrada manual de CPF não concede acesso adicional ao lead.

Leitura autorizada em transação curta; HTTP em `NOT_SUPPORTED`, fora de transação.
Recibo HMAC-SHA256 vinculado a usuário, lead, telefone, valores anteriores, candidato, origem
e validade (padrão 5m). Domínio `synapse:darwin-medico:previa:v2:` e novo `DARWIN_TOKEN`
invalidam recibos do produto anterior, mesmo se reutilizarem um segredo. Recibo não é
criptografado: contém os dados da prévia, só em memória da tela, nunca URL/localStorage/log.

Aplicação usa `PESSIMISTIC_WRITE`, mesma Specification e atualização parcial pelo caso de uso
existente. Mudança concorrente do CPF para outra identidade bloqueia inclusive aplicação só
do nome; mudança concorrente do telefone ou campo selecionado também retorna 409. Campo não
selecionado é preservado; reaplicar os mesmos valores é no-op. Não altera responsável,
participantes, mensagens, telefone, resumo, estado ou notas.
Auditoria `APLICAR_DADOS_DARWIN` / `LEAD_DARWIN` preserva usuário, UUID, horário, origem
`DARWIN_MEDICO` e nomes dos campos; não registra valores pessoais ou recibos.

Erros Problem Details sanitizados: desligada 403; CPF/recibo inválido 400; concorrência 409;
credencial upstream/CPF divergente/resposta inválida 502; timeout, limite, circuito, ocupação
ou configuração 503. **401/403 upstream não viram falha de autenticação da sessão CRM.**

## Estabilidade e configuração

Uma única consulta externa por ação; sem retries, redirects ou chamadas por mensagem/render.
HTTP Client inicializado somente quando necessário, circuito e bulkhead próprios por réplica,
espera zero, timeout de conexão 2s/total 10s e corpo limitado a 524288 bytes por padrão.
Demais parâmetros opcionais preservados na tabela do README. Mensagens/outbox/scheduler e
regras dos canais não foram alterados. Loopback HTTP somente `dev/test` para transporte simulado.
Não há migration, nova tabela, campo clínico ou sincronização automática.

| Variável | Default | Ação futura no Dokploy |
|---|---|---|
| `DARWIN_HABILITADO` | `false` | Habilitar **somente Fêmina** após homologação/autorização operacional. |
| `DARWIN_URL` | `https://developers.darwin.med.br` | Conferir origem oficial; nunca apontar para o outro produto. |
| `DARWIN_TOKEN` | vazio | Credencial Bearer do Darwin médico pelo canal seguro. |

Remover as variáveis obsoletas `DARWIN_API_KEY`, `DARWIN_CAMPO_NOME`, `DARWIN_CAMPO_CPF`,
`DARWIN_CANAL_ID`, `DARWIN_MAX_PAGINAS`, `DARWIN_MAX_SESSOES`: não são lidas nem usadas
como fallback. Nenhuma variável nova é obrigatória para o deploy de instâncias OFF.
Ativação é configuração por instância (Silo), sem condicional por nome de cliente no código.

## Homologação operacional futura — não executada nesta task

1. Conferir com responsável Darwin a credencial e seu escopo; guardar token somente no Dokploy
   da Fêmina. Manter OFF até deploy/ativação **separadamente autorizados**.
2. Usar lead/CPF controlados e consentidos. Abrir modal sem consultar automaticamente; consultar
   CPF conhecido. Conferir nome/CPF com a identidade esperada antes de aplicar.
3. Confirmar só os campos desejados, verificar ficha/lista/agenda sem F5 e persistência após reload.
4. Conferir CPF sem paciente no escopo, erro de credencial, cancelamento, alteração concorrente
   e usuário sem acesso. Falha não pode modificar a ficha nem interromper Atendimentos.
5. Desligamento: `DARWIN_HABILITADO=false` em operação autorizada; dados já confirmados ficam.
   Não configurar webhook/n8n, cadastrar paciente ou testar telefones alternativos.

Testes usam CPF e cadastro sintéticos, transporte HTTP simulado e PostgreSQL/Testcontainers;
não comprovam credencial, dados ou disponibilidade da conta real. As capturas históricas da
PR #340 estão [explicitamente obsoletas](evidencias/darwin-identidade/README.md) para este contrato.
Descoberta telefone→CPF depende de contrato oficial adicional; não foi entregue nem simulada.

## Auditoria da coleção médica e melhoria cadastral

A coleção fornecida `Darwin-API.postman_collection.json` declara API de integração médica v1.0.9,
gerada de OpenAPI 3.0.3; seu SHA-256 está registrado acima. Autenticação é Bearer. Foram
inventariadas as operações da coleção sem executar chamadas à conta Darwin. Capacidades de leitura
confirmadas:

- Paciente: somente `GET /api/patients/find/cpf?cpf=...`; exige CPF no formato `000.000.000-00`,
  retorna 404 quando não há paciente no escopo do token. Não existe busca por telefone, nome ou
  outro identificador demonstrado pela coleção.
- A resposta cadastral tem `name`, `cpf`, `email`, `phoneNumber`, `extraPhones`, `birthDate`,
  `address` e outros campos pessoais/sensíveis. No CRM, `nome`, `cpf`, `email` e `telefone` existem.
Nome/CPF já eram suportados; esta melhoria passa a oferecer **e-mail** em prévia e seleção
  explícita. E-mail que não passa a validação conservadora é omitido e marcado inválido; o endpoint
  de aplicação também o rejeita se for selecionado diretamente. Telefone não é aplicado porque é identidade de roteamento do WhatsApp; os demais
  campos não são importados por não terem correspondência direta necessária na ficha.
- A coleção também documenta leitura de agendamentos de um paciente por CPF e intervalo/status;
  profissionais, locais, grades/horários disponíveis, procedimentos e convênios. Procedimentos e
  convênios incluem paginação `page`/`amount` (máximo 100). Essas leituras não foram integradas:
  expor histórico de saúde/agenda na ficha demanda escopo operacional, autorização e benefício
  aprovados separadamente.
- Existem operações de escrita para criação/edição/remoção de agendamentos, cadastro de pacientes,
  grades e resultados de exames. Todas permanecem fora desta integração. Não há endpoint de
  cancelamento separado: a coleção documenta `DELETE /api/schedules/delete`, uma escrita excluída
  desta tarefa.
- A coleção não define webhook/assinatura para atualização de paciente, busca por CPF em lote,
  paginação de pacientes nem uma cota/limite geral de consultas. Não inferimos esses contratos.
  O HTTP de leitura continua sem retry, com circuit breaker, bulkhead, timeout e limite de resposta.

`GET /api/v1/leads/integracoes/darwin` agora mantém `habilitada` e acrescenta `estado` (`DESABILITADA`,
`CONFIGURACAO_INCOMPLETA`, `DISPONIVEL`). O estado não revela qual campo ambiental falta nem valores;
permite que a ficha mostre orientação de configuração em vez de ocultar silenciosamente a ação.
O endpoint permanece protegido pelas capacidades `clientes.acessar` e `contatos.editar`.

A configuração `DARWIN_HABILITADO`, `DARWIN_URL` e `DARWIN_TOKEN` já é repassada pelo serviço
backend em `docker/dokploy-stack.yml`, com flag padrão `false` e token opcional vazio. O código
versionado não permite comprovar quais valores estão salvos no Dokploy nem a imagem implantada na
Fêmina; essa conferência exige leitura do serviço implantado. Nenhuma variável nova foi criada.
