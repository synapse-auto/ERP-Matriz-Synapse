# Foto de perfil do lead pela Z-API

## Escopo e contrato oficial (03/11/2026)

Opt-in para a instância que seleciona `WHATSAPP_PROVEDOR=z-api`. Não migra
clientes, não altera n8n e não modifica fotos de grupos/instância/usuários.

Fonte: [índice oficial](https://developer.z-api.io/llms.txt),
[Pegar imagem do contato](https://developer.z-api.io/contacts/get-profile-picture).
Consulta `GET https://api.z-api.io/instances/{instanceId}/token/{token}/profile-picture`
com query `phone` (DDI+DDD+número) e `Client-Token`, conforme o modo de autenticação
da conta já configurado. Resposta documentada: objeto com `link`.
Não tentar variantes do telefone nem converter LID em telefone.

Os [callbacks recebidos](https://developer.z-api.io/webhooks/on-message-received-examples)
documentam `senderPhoto` e exemplificam `photo`. Não são imagem anexada. Esta
entrega não usa esses campos como origem de download: resolve a foto do contato
exato pelo endpoint acima, com identidade e credencial ativas verificadas no banco.
Evita confundir remetente de grupo/eco com o lead e não persiste URL temporária.

HTTP 200 com `link` textual não vazio inicia download. HTTP 200 com `link`
explicitamente nulo/vazio significa **nenhuma foto fornecida**, com cache negativo;
não comprova se a causa é privacidade ou ausência de imagem. JSON inválido, campo
ausente, erro embutido, qualquer HTTP diferente de 200, timeout e download recusado
são **erro**, não ausência. A documentação não define uma codificação distinta para
foto privada; a homologação controlada deve registrar a resposta sanitizada desse
caso, sem inventar um status do fornecedor.

## Reuso e lacunas corrigidas

- Reutiliza `CanalGateway.buscarFotoDePerfil`, `fotoDePerfilExecutor`, processador
  de foto do lead, bucket privado de avatares e `GET /api/v1/leads/{id}/foto`.
- A coleta anterior só existia na Uzapi/Autotic, disparada por mensagem recebida;
  seu cache era local e erro temporário removia o cache imediatamente. O fluxo
  legado permanece inalterado para os outros provedores.
- Z-API usa cache/reserva PostgreSQL próprios, por lead e identidade de canal.
  V115 é aditiva; não modifica migrations aplicadas. Sem índice de varredura:
  acesso pontual pela PK, sem job de carteira inteira.
- Nova leitura autorizada da ficha publica `LeadConsultado`; listener apenas
  submete tarefa ao executor. Leads antigos são enriquecidos ao usar a ficha
  (inclusive Agenda) ou receber mensagem, não numa coleta em massa.
- Agenda não tinha campo de foto na projeção/lista. `fotoUrl` opcional foi
  acrescentado ao DTO/OpenAPI e consumido pelos avatares desktop/mobile. Continua
  projeção paginada sem notas, resumo, JSON customizado ou bytes.

## Política de origem, cache e concorrência

`lead.foto_url` pode ser editada manualmente. Fotos em `foto_referencia` também
podem ter vindo do endpoint interno da automação; não havia origem persistida.
Portanto, fotos/URLs preexistentes são preservadas. O coletor só substitui a
referência que ele próprio registrou em `lead_foto_captura.referencia_gerada`,
na mesma instância/credencial/telefone. Troca de identidade não adota imagem antiga.
Não há adoção automática de fotos legadas.

Uma reserva condicional no banco deduplica nós/réplicas; um conjunto limitado às
tarefas em fila/em execução evita filas locais duplicadas. A reserva expira após
crash. Cache positivo, negativo e erro têm prazos distintos e persistem em restart.
Expirar o prazo só permite nova coleta **na próxima atividade**; não há polling.

Falha e ausência nunca apagam foto válida. A gravação usa compare-and-set de
telefone, URL manual, referência e hash; edição durante download vence. Storage
novo é compensado em falha/rollback, e o objeto antigo só é removido após commit
do caminho automático. Conteúdo idêntico não provoca nova gravação.

Consulta/download do fornecedor ficam fora das transações e no executor separado
do chat. Breaker próprio `canal-z-api-foto` reutiliza `CANAL_CB_*`; concorrência,
fila e duração são limitadas. Falhas não são repassadas para o envio/recebimento.
O cache e a referência da foto são atualizados na mesma transação.

## Download, autorização e atualização visual

Somente HTTPS, porta 443, host **exato** configurado, sem userinfo/fragmento,
redirects, proxy do ambiente ou retry transparente. DNS do próprio transporte
recusa IP privado/loopback/metadata/blocos especiais, prevenindo rebinding.
O GET do CDN não recebe token da Z-API. Bytes são limitados, MIME é detectado e
dimensões são verificadas antes de decodificar; o processador existente recodifica
para PNG 256px e descarta metadados. URL temporária não é salva nem enviada ao browser.

A leitura da foto continua pela Specification/RLS do lead e permissão de clientes;
lead invisível e inexistente respondem 404. Cache operacional é exclusivo de serviço.
Logs registram apenas etapa e status técnico, sem telefone, token, URL ou imagem.

Após commit, sinal best-effort `FOTOS_LEADS_ATUALIZADAS` usa o Redis/fila pessoal
existentes. O sinal não contém ID de lead, foto ou nome; só usuários com acesso a
clientes recebem a invalidação, e cada releitura passa pela autorização normal.
Frontend invalida avatar/ficha/lista/Agenda, sem toast, som, polling ou alteração
de mensagens. Reconexão revalida o cache de avatares; foco recupera leituras caso
um sinal Redis seja perdido. Não é garantia de entrega durável de notificação.

## Configuração opcional e ação no Dokploy

**Somente na Fêmina**, após revisão e deploy autorizado em outra etapa:
`Z_API_FOTO_HABILITADO=true`. Os parâmetros Z-API de instância/credencial já
existentes devem corresponder ao cadastro ativo (`env://Z_API_TOKEN`). Não há
segredo novo. Nas demais instâncias, manter o default `false`.

| Variável | Default | Finalidade |
| --- | --- | --- |
| `Z_API_FOTO_HABILITADO` | `false` | Opt-in independente do coletor legado. |
| `Z_API_FOTO_CACHE_TTL` | `6h` | Foto disponível/atualizada ou origem preservada. |
| `Z_API_FOTO_CACHE_NEGATIVO` | `1h` | Foto não fornecida em resposta válida. |
| `Z_API_FOTO_INTERVALO_ERRO` | `15m` | Próxima tentativa após falha, somente por atividade. |
| `Z_API_FOTO_RESERVA` | `2m` | Lease entre nós, maior que duas vezes o timeout. |
| `Z_API_FOTO_TIMEOUT` | `10s` | Limite por consulta/download. |
| `Z_API_FOTO_CONCORRENCIA` | `1` | Bulkhead HTTP exclusivo de foto. |
| `Z_API_FOTO_LIMITE_BYTES` | `5242880` | Máximo do download; limite de imagem do CRM também é respeitado ao salvar. |
| `Z_API_FOTO_PIXELS_MAXIMOS` | `24000000` | Limite antes da decodificação, consistente com o limite já usado em fotos de grupo. |
| `Z_API_FOTO_HOSTS_PERMITIDOS` | `pps.whatsapp.net` | Lista de hosts exatos. Nunca curinga/sufixo genérico. |

O executor reutilizado mantém `CANAL_FOTO_PERFIL_CONCORRENCIA`/`FILA` (2/100).
`CANAL_FOTO_PERFIL_HABILITADO` controla o coletor legado, não substitui o opt-in
Z-API. Todas as novas variáveis são opcionais no `.env.example`, README e stack;
o desligamento não exige credenciais novas nem impede o boot de outros canais.

## Homologação controlada (não executada na conta real nesta task)

1. Confirmar SHA implantado, migração aditiva e identidade/credencial da instância.
2. Conferir o host real de uma URL de foto, sem compartilhá-la; se diferente,
   permitir somente esse host após análise. Não ampliar allowlist indiscriminadamente.
3. Ativar opt-in apenas na instância autorizada. Usar contato de teste consentido,
   sem foto manual/preexistente. Abrir ficha ou receber mensagem desse contato.
4. Conferir avatar na lista, cabeçalho, ficha e Agenda, recarregar/reconectar.
5. Validar contato sem foto/privado e preservação de uma foto válida em falha.
   Reabrir repetidamente não deve produzir consultas adicionais dentro do cache.
6. Guardar somente horários, etapa/status e resultado de cache sanitizados. Não
   exportar números, imagens, URLs temporárias, credenciais ou payloads completos.

Testes de transporte simulado, PostgreSQL e storage descartáveis não substituem
essa homologação. Não houve chamada produtiva, coleta em massa, alteração n8n ou deploy.
