# 60 — Foto do grupo do Chat Interno

Exibir e alterar a foto dos grupos do Chat Interno. Só grupos do Chat Interno: grupos do WhatsApp,
integrações Meta/UZAPI, foto de lead e foto de contato externo não foram tocados. Conversa direta segue
mostrando a foto do outro usuário, como antes.

## Quem pode alterar (decisão)

O modelo do chat não tinha criador nem administrador de grupo: desde a V54 qualquer participante adiciona,
remove e renomeia ("sem hierarquia"), e a gestão de permissões (`docs/47`) deixa o chat interno de fora.
Para a foto, a regra é **somente o criador do grupo**.

- A V95 registra `chat_interno_conversa.criado_por_id` e faz o backfill dos grupos existentes a partir do
  autor da mensagem `GRUPO_CRIADO` (gravada na criação, com remetente = quem criou).
- Grupo sem essa mensagem, ou cujo criador foi apagado, fica com `criado_por_id` nulo: **ninguém** altera a
  foto, e a leitura segue valendo.
- Se o criador for removido do grupo por outro participante, ele deixa de poder alterar (não participa mais)
  e a foto fica como está. Nenhum outro participante herda o direito.
- A autorização vale no backend, em duas camadas: o caso de uso (`participa` → `é grupo` → `é o criador`,
  nessa ordem, para não revelar a existência do grupo) e o próprio `UPDATE ... WHERE criado_por_id = ator`,
  porque a RLS de `chat_interno_conversa` deixa **qualquer participante** escrever na linha.
- O frontend só obedece: `podeAlterarFoto` vem em cada item de `GET /chat-interno/conversas`.

Isso é uma escolha de produto barata de reverter (trocar a condição). Ver "Decisões pendentes".

## API

| Rota | Efeito |
|---|---|
| `POST /api/v1/chat-interno/conversas/{id}/foto` (multipart, parte `arquivo`) | Troca a foto. `200 {fotoUrl}` com a URL versionada. |
| `DELETE /api/v1/chat-interno/conversas/{id}/foto` | Remove a foto. Idempotente: `200 {fotoUrl:null}`, sem nova mensagem se já não havia. |
| `GET /api/v1/chat-interno/conversas/{id}/foto` | Entrega a imagem a quem participa do grupo. `404` sem foto. `Cache-Control: private, no-cache`. |
| `GET /api/v1/chat-interno/conversas` | Passa a trazer `fotoUrl` (também para grupo) e `podeAlterarFoto`. |

Erros em RFC 7807: `400` conversa direta; `403` não participa ou não é o criador; `413` acima do limite;
`422` nome, tipo, conteúdo ou dimensões inválidos; `503` storage indisponível (a foto anterior permanece).

A URL tem a forma `/api/v1/chat-interno/conversas/{id}/foto?v=<epoch ms>`. A versão é `foto_atualizada_em` e
muda a cada troca, então nem o navegador nem o cache do cliente servem a imagem anterior. O bucket nunca é
exposto: a entrega passa pelo backend, com JWT.

## Validação do arquivo

Em ordem, e **antes de ler o corpo** quando possível (o multipart aceita até 110 MB):

1. acesso e permissão (sem ler nada);
2. nome: sem `/`, `\`, `..`, caracteres de controle, até 255 caracteres, extensão `jpg|jpeg|png|webp`;
3. `Content-Type` declarado: `image/jpeg|png|webp` (`image/jpg` é normalizado);
4. tamanho contra `anexo.tamanho_maximo_imagem_mb` (a mesma chave dos anexos, editável pela gestão);
5. tipo **real** pelos bytes (Tika) igual ao declarado: executável ou texto renomeado, e PNG enviado como
   JPEG, são recusados;
6. dimensões lidas **só do cabeçalho**: menor lado ≥ mínimo, maior lado ≤ máximo, largura × altura ≤ teto de
   pixels. Bytes não limitam pixels: um PNG de poucos KB pode declarar 30000 × 30000 e custar gigabytes na
   decodificação;
7. reprocessamento (o mesmo do avatar de usuário): decodifica, corta ao centro, redimensiona para 256 px e
   regrava em PNG. Isso descarta EXIF e qualquer metadado, e arquivo corrompido vira `422`.

O nome do arquivo nunca chega ao storage (a chave é `grupo/<uuid>.png`); mesmo assim ele é validado.

## Storage

Mesmo bucket dos avatares, **prefixo próprio `grupo/`**. O prefixo é o que isola o alcance de uma referência:
`buscar`/`remover` do adaptador de grupo não tocam `avatar/` (usuário) nem `lead/`, e os deles não tocam
`grupo/`. Nada é salvo no banco além da referência opaca.

Ordem e compensação (`AtualizarFotoDoGrupoChatUseCase`): grava o objeto novo → `UPDATE` → mensagem de
sistema. O objeto novo é apagado se a transação reverter; o antigo só é apagado **depois do commit**. A linha
do grupo é travada (`FOR UPDATE`) durante a troca, então trocas simultâneas não deixam objeto órfão.

## Auditoria

Troca e remoção são `@Auditable` (`ALTERAR_FOTO_GRUPO_CHAT_INTERNO` / `REMOVER_FOTO_GRUPO_CHAT_INTERNO`,
`entidade_tipo = CHAT_INTERNO_CONVERSA`, `entidade_id` = grupo, sem dados), como as demais ações do chat
(editar, excluir, encaminhar). A tentativa recusada (403, 422…) não deixa registro. Além disso, a mensagem de
sistema no próprio histórico do grupo mostra a todos quem trocou ou removeu.

## Tempo real

Não há canal novo. A troca e a remoção gravam uma mensagem de sistema (`FOTO_ALTERADA` / `FOTO_REMOVIDA`,
igual ao que o renomear já faz) e publicam o `MensagemEnviada` do chat para os **outros** participantes, pelo
`RelayDeChatInterno` → Redis → STOMP `/user/queue/notificacoes`. O frontend já recarrega a lista a cada
evento do chat, e a lista traz a URL nova.

- Quem alterou atualiza a lista na hora pela resposta da própria requisição.
- Reconexão: o Redis só entrega, o banco é a fonte de verdade. A página do chat recarrega as conversas a cada
  reconexão do WebSocket (a partir da segunda), então um evento perdido enquanto o socket estava fora não deixa
  foto velha.
- Recarregar a página sempre mostra a foto certa (vem do banco).
- A inbox unificada (`/atendimentos/inbox`) já repassava `fotoUrl` e passou a mostrar a foto do grupo sem
  mudança própria.

## Configuração

Tamanho em bytes: `configuracao_automacao` → `anexo.tamanho_maximo_imagem_mb` (existente). Dimensões, novas,
todas opcionais (os padrões valem sem configurar nada):

| Variável | Padrão | Efeito |
|---|---|---|
| `CHAT_FOTO_GRUPO_LADO_MINIMO_PX` | `64` | Menor lado aceito. |
| `CHAT_FOTO_GRUPO_LADO_MAXIMO_PX` | `8000` | Maior lado aceito. |
| `CHAT_FOTO_GRUPO_PIXELS_MAXIMOS` | `24000000` | Largura × altura máxima (≈ 96 MB decodificada). |

Nenhuma é obrigatória no `dokploy-stack.yml`; não há ação necessária no Dokploy.

## O que mudou no banco (V95)

Colunas novas em `chat_interno_conversa` (tabela pequena e não particionada; `ADD COLUMN` sem default é só
metadado): `criado_por_id` (FK `ON DELETE SET NULL`, com índice parcial), `foto_referencia`,
`foto_atualizada_em`; restrições `foto só em GRUPO` e `referência e versão andam juntas`. A função
`app_criar_conversa_grupo` passa a gravar o criador. **A tabela `mensagem` e o histórico não são tocados.**

Cuidado registrado no código da migration: a V65 concedeu `SELECT, UPDATE` em `chat_interno_mensagem` ao
papel `synapse_chat_rls` em caráter permanente (o trigger de citações é `SECURITY DEFINER` e depende disso).
O backfill usa esse papel e **não** faz `GRANT`/`REVOKE` nessa tabela: uma primeira versão da V95 revogava o
`SELECT` no fim, o que quebraria a exclusão de mensagens do chat; `FotoDoGrupoMigrationIT` agora impede.

## Limites conhecidos

- Criador removido, apagado ou sem registro: a foto fica congelada (ver acima).
- Não há recorte manual: o backend corta ao centro e quadra a imagem.
- `GET .../foto` não responde `304`: o navegador baixa de novo quando a URL muda, o que só ocorre numa troca.
- Falha do storage na leitura (`GET`) vira `404` e o avatar cai no ícone; só a escrita devolve `503`.
- Os ITs usam um storage em memória: o adaptador MinIO real de `grupo/` só tem teste de isolamento de prefixo
  (sem rede), não de gravação/leitura contra um MinIO.

## Decisões pendentes

1. **Quem altera.** Hoje só o criador. Alternativas: qualquer participante (coerente com renomear, e elimina a
   foto congelada) ou criador + gestor/subgestor (exigiria o gestor participar do grupo, pela RLS).
2. **Transferir a autoria** quando o criador sai do grupo, para a foto não congelar.

## Testes

- `ChatInternoFotoDoGrupoIT` (23 cenários, HTTP + Postgres + Redis + STOMP reais, storage em memória):
  exibição, JPEG/PNG/WebP, substituição, remoção idempotente, autorização (participante comum, não
  participante, criador removido, sem token), barreira no próprio `UPDATE`, validações em RFC 7807, limite
  editável, auditoria, falha do storage, falha **depois** do storage com rollback, aviso por STOMP, reconexão e inbox.
- `FotoDoGrupoMigrationIT`: backfill do criador, privilégios da V65 preservados, restrições, função de criação.
- `AtualizarFotoDoGrupoChatUseCaseTest`, `ValidadorDeFotoDeGrupoImagemTest`, `MinioArmazenamentoDeFotoDeGrupoTest`.
- Frontend: `avatar-do-grupo`, `foto-do-grupo`, painel, página (lista, evento, reconexão), mensagens de sistema.
- Cada proteção foi violada de propósito (criador, participante, ordem de leitura, limite, rollback, commit,
  `FOR UPDATE`, versão da URL, trava de duplo envio, permissão na tela, recarga na reconexão, `REVOKE` da
  V95) e o teste correspondente reprovou.
