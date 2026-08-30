
# Prompt E97 — Foto de perfil do lead vinda da integração (UAZAPI)

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/foto-de-perfil-do-lead`) e PR. **Sem merge, sem deploy.**
> Toca migration, contrato `/internal/v1` e mais de um módulo: verificação no **degrau mais alto** —
> `./mvnw verify` no reator **e** a suíte do frontend.

---

## O pedido

A Meta não entrega a foto de perfil do contato. Uma integração externa (n8n + **UAZAPI**, API não
oficial) coleta a foto e a envia para o CRM. Esta etapa é **só o lado do CRM**: receber, reprocessar,
guardar e exibir. O CRM **não** busca foto em lugar nenhum e **não** agenda nada — **RN-CRM-07**: o
CRM configura a automação, não a executa. Quem varre e quem chama é o n8n.

## Bloco 0 — O que já existe (não reinvente)

Antes de escrever qualquer linha, leia estes arquivos. O CRM **já tem** a pipeline inteira de avatar,
feita no E50 para foto de usuário:

- `backend/crm-equipe/.../application/usuario/ProcessadorDeAvatar.java` — porta.
- `backend/crm-app/.../config/avatar/ProcessadorDeAvatarImagem.java` — valida *magic bytes* com Tika
  (aceita `image/jpeg`, `image/png`, `image/webp`), corta no centro, redimensiona para 256px e
  **reencoda em PNG**. Reencodar já descarta EXIF e qualquer metadado: não existe "tirar EXIF" a
  fazer, é consequência do reencode.
- `backend/crm-equipe/.../application/usuario/ArmazenamentoDeAvatar.java` — porta
  (`salvar` / `buscar` / `remover`, referência opaca).
- `backend/crm-app/.../config/avatar/MinioArmazenamentoDeAvatar.java` — bucket separado
  (`midia.bucket() + "-avatares"`), chave `avatar/<uuid>.png`, **o browser nunca recebe URL do MinIO**.
- `backend/crm-equipe/.../application/usuario/AtualizarMinhaFotoUseCase.java` — a ordem correta:
  valida limite → processa → salva no storage → grava a referência → **só então** remove a antiga, e
  em caso de erro remove a nova. Copie essa ordem, não invente outra.
- `V40__foto_de_usuario.sql` — `usuario.foto_referencia VARCHAR(255)`, referência opaca; o arquivo
  nunca vai para o banco.
- `MeuUsuarioController` — entrega em `GET /api/v1/me/foto/{id}`, autenticado, `no-cache`.

**Consequência direta para o contrato com o integrador:** ele **não** precisa converter para JPEG,
nem tirar EXIF, nem redimensionar para 640x640, nem calcular SHA-256. Ele manda os bytes originais;
o CRM faz o resto. Menos superfície do lado dele, menos coisa para dar errado, e a garantia de que
o que entra no bucket foi reprocessado por *nós*.

## Bloco 1 — Migration V48

`origin/main` está em **V47** (`V47__lead_codigo.sql`). Faça `git fetch` e confirme antes de numerar.

`V48__foto_de_perfil_do_lead.sql`, na tabela `lead`:

| coluna | tipo | para quê |
| --- | --- | --- |
| `foto_referencia` | `VARCHAR(255)` | referência opaca no bucket, igual ao padrão de `usuario` |
| `foto_hash` | `CHAR(64)` | SHA-256 **dos bytes originais recebidos** |
| `foto_atualizada_em` | `TIMESTAMPTZ` | quando o CRM gravou |

Comente as colunas como as outras migrations comentam. **Não** mexa em `lead.foto_url`: ela já existe
(`V4__crm_core.sql`), é editável pela tela de lead e pode conter URL externa legada. A precedência
está no Bloco 4.

O `foto_hash` não é paranoia de segurança: é o que faz o *polling* do n8n ser barato. Ele varre a
mesma lista de tempos em tempos e reenvia a mesma foto; se o hash bate, o CRM responde `INALTERADA`
sem escrever no bucket e sem escrever no banco.

## Bloco 2 — O endpoint interno

**Não crie um esquema de autenticação novo.** Este CRM já tem namespace interno com contrato
versionado: `/internal/v1`, header `X-Synapse-Token`, `@SecurityRequirement(name = "synapseToken")`,
`ContextoDeServico.buscarComo(...)` (que grava `app.papel = 'SERVICO'` para a RLS) e erros em
RFC 7807 (`ProblemDetail`). Veja `TagsAutomacaoInternalController` como modelo — é o controller
interno que já vive em `crm-core` e mexe com lead.

```
POST   /internal/v1/leads/{id}/foto     multipart/form-data, parte "arquivo"
DELETE /internal/v1/leads/{id}/foto
```

Resposta 200: `{ "leadId": "<uuid>", "status": "ATUALIZADA" | "INALTERADA" | "REMOVIDA" }`.

Erros:

| status | quando |
| --- | --- |
| 401 | `X-Synapse-Token` ausente ou inválido |
| 404 | lead inexistente |
| 413 | arquivo acima do limite configurado |
| 422 | não é JPEG/PNG/WebP, ou conteúdo de imagem inválido |

Regras:

- `POST` com hash igual ao `foto_hash` gravado → `INALTERADA`, **sem** tocar storage nem banco.
- `DELETE` num lead sem foto → `REMOVIDA` mesmo assim. Idempotente, não 404.
- Todo `POST`/`DELETE` roda dentro de `ContextoDeServico.buscarComo("...-foto-do-lead", ...)`.

**Sem `Idempotency-Key`, sem `sourceUpdatedAt`, sem status `STORED_PENDING` e sem
`IGNORED_STALE`.** A chave é o UUID do lead — o lead existe ou não existe; o hash já dá
idempotência; e há uma fonte só escrevendo, então "chegou fora de ordem" não é um caso real aqui.
Se você achar que algum desses é necessário, **pare e explique no relatório** em vez de implementar.

**Regenerar o snapshot.** `ContratoInternalV1IT` compara `/internal/v1` contra
`src/test/resources/contrato/internal-v1-snapshot.json` e vai falhar. Rode, copie o bloco "ATUAL" da
mensagem de falha para o arquivo, e confira no diff do PR que a única mudança são as duas rotas
novas. Se aparecer mais coisa no diff, você mudou algo sem querer.

## Bloco 3 — Onde a porta mora (a armadilha de arquitetura)

`ArmazenamentoDeAvatar` e `ProcessadorDeAvatar` estão em **`crm-equipe`**. O lead está em
**`crm-core`**. `crm-core` **não pode** depender de `crm-equipe` — o ArchUnit vai te parar, e com
razão.

Não force. Escolha e **relate qual escolheu e por quê**:

- **(a)** subir as duas portas para `crm-shared-kernel` (onde já mora `ArmazenamentoDeMidia`,
  `DetectorDeTipoReal`, `CategoriaDeMidia` — é exatamente esse tipo de porta), com `crm-equipe`
  passando a usar as de lá;
- **(b)** portas novas e próprias em `crm-core`, com a implementação em `crm-app` reusando
  `ProcessadorDeAvatarImagem`.

(a) é mais limpo e mexe em `crm-equipe`; (b) duplica interface e não mexe em ninguém. Prefira a
menor mudança que deixe o ArchUnit verde **sem** duplicar a lógica de reprocessamento de imagem —
duplicar a interface é aceitável, duplicar o `ProcessadorDeAvatarImagem` não é.

No storage, use **prefixo próprio** (`lead/<uuid>.png`), não `avatar/`: os dois `buscar`/`remover`
existentes filtram por prefixo justamente para não se confundirem.

## Bloco 4 — Precedência e a URL que sai do backend

Hoje três lugares leem `lead.foto_url` cru:

- `PainelDeAtendimentosRepositorioJdbc` (`l.foto_url AS lead_foto_url`);
- `ListarInboxUnificadaUseCase` / `InboxUnificada`;
- `LeadController` (leitura e escrita manual).

Passem todos a devolver, no mesmo campo:

```sql
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END
```

Exatamente o que `ChatInternoRepositorioJdbc` e `FeedbackRepositorioJdbc` já fazem com
`foto_referencia` do usuário. Foto da integração **ganha** da URL externa digitada à mão.

E crie a entrega autenticada, espelhando `GET /api/v1/me/foto/{id}`:

```
GET /api/v1/leads/{id}/foto
```

Autenticado, `no-cache`, 404 quando não há foto. **Não** contorne a RLS: um atendente que não
enxerga o lead não pode baixar a foto dele — o `ContextoDeServico` não entra aqui, é requisição de
usuário.

## Bloco 5 — O seam do frontend (é aqui que isso quebra na tela)

Verificado em `origin/main`, e é o motivo de esta etapa não ser só backend:

- `frontend/src/lib/utils.ts` → `urlSegura()` faz `new URL(url)` **sem base**. Uma URL relativa como
  `/api/v1/leads/<id>/foto` **lança e volta `undefined`**. Ou seja: com o backend certo, a foto
  simplesmente não aparece.
- `cartao-conversa.tsx` e `cabecalho-conversa.tsx` renderizam a foto do lead com
  `urlSegura(...)` + `<img src>` **sem autenticação** — desenhado para CDN externo (o teste usa
  `https://cdn.example/foto.webp`).
- O caminho certo já existe para usuário: `AvatarIniciais` (`components/ui/avatar-iniciais.tsx`)
  busca com `apiFetchBlob` (autenticado), cria `objectURL` e cai nas iniciais coloridas enquanto não
  carrega.

O que fazer:

1. Uma regra só, **escrita num lugar só**: URL que começa com `/` → caminho autenticado
   (`apiFetchBlob`, como `AvatarIniciais`); URL absoluta → `urlSegura` + `<img>` como hoje.
   Não espalhe esse `if` por três componentes.
2. Migre a foto do lead em `cartao-conversa.tsx` e `cabecalho-conversa.tsx` para esse caminho.
   Procure se `tabela-de-leads.tsx` e `lista-de-leads-mobile.tsx` também exibem foto de lead — se
   exibem, entram junto.
3. Não remova `urlSegura` nem afrouxe a checagem de esquema: `foto_url` externa continua sendo dado
   de fora, e `javascript:`/`data:` continuam tendo que morrer ali.
4. Fallback é o que já existe: iniciais coloridas. Nada de *spinner*, nada de imagem quebrada.

## Bloco 6 — Testes

- IT do endpoint interno: 200 `ATUALIZADA` na primeira vez; `INALTERADA` no reenvio do **mesmo**
  arquivo; `REMOVIDA` no `DELETE` e no `DELETE` repetido; 401 sem token; 401 com **JWT humano**
  (o interno não abre para usuário — veja `AvaliacaoAtendimentoIT`); 404 em lead inexistente;
  422 com um arquivo que não é imagem; 413 acima do limite.
- Teste de que **o original não vai para o storage**: o que foi salvo é PNG 256x256.
- `ContratoInternalV1IT` verde com o snapshot atualizado.
- Frontend: foto do lead renderiza a partir de caminho relativo autenticado; cai nas iniciais quando
  não há foto; URL externa absoluta continua funcionando; `javascript:` continua bloqueada.

## Bloco 7 — O contrato já foi publicado para o integrador

`docs/23-contrato-foto-de-perfil-do-lead.md` **já existe** e já foi enviado ao integrador. Ele é a
especificação desta etapa, não um subproduto dela: rotas, nome da parte do multipart, formatos,
limite, os três status, a tabela de erros e a regra de retry estão fixados lá.

Sua implementação tem que bater com aquele documento. **Não reescreva o documento para descrever o
que você fez.** Se em algum ponto o código não puder cumprir o que está escrito, **pare, não
implemente diferente em silêncio, e relate qual linha do contrato não fecha e por quê** — quem
decide se o contrato muda é o Marcondes, porque tem alguém do outro lado já programando contra ele.

A única edição permitida no doc é trocar o valor do limite, **se e somente se** você encontrar na
configuração um valor diferente de 5 MB — e nesse caso diga no relatório onde encontrou.

## Verificação

Migration + contrato interno + mais de um módulo → degrau mais alto:

```
./mvnw verify        # no reator, na raiz de backend/
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

## Relatório

Termine relatando, com caminho de arquivo e linha:

1. qual opção do Bloco 3 você escolheu e o que o ArchUnit disse;
2. o diff do `internal-v1-snapshot.json` (só as duas rotas novas?);
3. onde ficou a regra única do Bloco 5 e quais componentes passaram a usá-la;
4. o limite de bytes que você encontrou e onde ele está configurado;
5. qualquer lugar que lia `lead.foto_url` e você **não** alterou, e por quê.
