# Contrato — Foto de perfil do lead (UAZAPI → CRM Synapse)

**Versão 2 — substitui integralmente o CONTRATO V1.**
Estrutural Vidros · CRM Synapse · agosto/2026

---

## 1. O que é isto

A Meta não entrega a foto de perfil do contato. A integração externa (n8n + UAZAPI) coleta a foto e
a envia para o CRM. Este documento é o contrato dessa entrega: as duas rotas, o que mandar, o que
esperar de volta e o que fazer quando der erro.

**A integração é quem varre e quem chama.** O CRM só recebe. Ele não busca foto, não agenda nada e
não dispara webhook para a integração — é regra de arquitetura do produto, não preguiça. O ritmo do
polling é decisão de vocês.

> **Estado:** as rotas abaixo estão em implementação (etapa E97) e ainda **não** existem em
> produção. Este documento é o alvo contra o qual programar. Testar primeiro em homologação.

---

## 2. Mudanças em relação ao CONTRATO V1

A ideia e a disciplina de segurança continuam as mesmas. O que muda é transporte — e some metade do
pré-processamento.

| Assunto | CONTRATO V1 | Agora |
| --- | --- | --- |
| Rota | `POST /api/integrations/contact-avatars` | `POST /internal/v1/leads/{leadId}/foto` |
| Autenticação | `Authorization: Bearer <token>` | `X-Synapse-Token: <token>` |
| Chave do contato | campo `phone` | **UUID do lead**, no path |
| Nome da parte do arquivo | `image` | `arquivo` |
| Remover foto | campo `operation: REMOVE` | verbo **`DELETE`** na mesma rota |
| Formato do arquivo | só JPEG, ≤640x640, sem EXIF | **JPEG, PNG ou WebP, original, sem tratar** |
| Tamanho máximo | 512 KiB | **5 MB** |
| `sha256` | obrigatório, calculado por vocês | **não mandar** — o CRM calcula |
| `eventId` / `Idempotency-Key` | obrigatórios | **não mandar** — não existem mais |
| `sourceUpdatedAt` | obrigatório | **não mandar** |
| `provider` / `providerInstance` | obrigatórios | **não mandar** |
| Status de resposta | UPDATED / UNCHANGED / REMOVED / STORED_PENDING / IGNORED_STALE | **ATUALIZADA / INALTERADA / REMOVIDA** |
| Erros | 400 / 401 / 413 / 415 / 503 | **401 / 404 / 413 / 422** |

Campo que sobrar no `multipart` é ignorado em silêncio — não dá erro, só gasta CPU de vocês.

---

## 3. Autenticação

Header **`X-Synapse-Token`**, com o mesmo token que a automação já usa em `/internal/v1` para tags,
transferência e registro de mensagens. Não há token novo, não há OAuth, não há Bearer.

- O token **nunca** vai na URL nem em query string.
- O token **nunca** aparece em log.
- Cada instância do CRM tem o seu token e o seu banco. Token não se compartilha entre clientes.

Requisição sem o header, ou com token errado, recebe **401**.

---

## 4. Enviar / atualizar a foto

```
POST {BASE_URL}/internal/v1/leads/{leadId}/foto
X-Synapse-Token: <token>
Content-Type: multipart/form-data
```

| Parte | Obrigatória | Conteúdo |
| --- | --- | --- |
| `arquivo` | sim | os bytes da imagem, como vieram da UAZAPI |

`{leadId}` é o UUID do lead no CRM.

Formatos aceitos: **`image/jpeg`, `image/png`, `image/webp`**. O tipo é verificado pelos bytes do
arquivo, não pela extensão nem pelo `Content-Type` declarado — mandar um `.jpg` que na verdade é
outra coisa dá **422**.

Tamanho máximo: **5 MB** (configurável no CRM em `anexo.tamanho_maximo_imagem_mb`; hoje 5).

Exemplo:

```bash
curl -X POST "{BASE_URL}/internal/v1/leads/3f2b18a4-.../foto" \
  -H "X-Synapse-Token: $TOKEN" \
  -F "arquivo=@foto-original.jpg"
```

### Resposta

```
200 OK
Content-Type: application/json

{ "leadId": "3f2b18a4-...", "status": "ATUALIZADA" }
```

| status | significa |
| --- | --- |
| `ATUALIZADA` | foto nova gravada |
| `INALTERADA` | é byte a byte a mesma foto que já está lá; nada foi escrito |

**`INALTERADA` é sucesso, não erro.** O CRM guarda o hash da última foto recebida e compara. Reenviar
a mesma imagem é barato de propósito: é o que faz o polling repetido não custar nada. Não trate como
falha e não tente de novo.

---

## 5. Remover a foto

Quando o contato apagar a foto de perfil, ou a UAZAPI passar a não retornar mais imagem para aquele
número:

```
DELETE {BASE_URL}/internal/v1/leads/{leadId}/foto
X-Synapse-Token: <token>
```

```
200 OK
{ "leadId": "3f2b18a4-...", "status": "REMOVIDA" }
```

É **idempotente**: apagar a foto de um lead que já está sem foto também devolve `REMOVIDA`, não 404.
Podem chamar sem verificar antes.

---

## 6. Erros e o que fazer com cada um

Erros vêm em RFC 7807 (`application/problem+json`), com `title` e `detail` em português.

| Código | Quando | O que fazer |
| --- | --- | --- |
| **401** | `X-Synapse-Token` ausente ou inválido | **Não repetir.** Parar o fluxo e avisar. É configuração errada, não falha transitória. |
| **404** | lead não existe (removido, ou UUID errado) | **Não repetir.** Descartar aquele item da fila e seguir para o próximo. |
| **413** | arquivo acima de 5 MB | **Não repetir.** Descartar. |
| **422** | não é JPEG/PNG/WebP, ou imagem corrompida | **Não repetir.** Descartar. |
| **5xx** | CRM indisponível ou erro interno | **Repetir** com backoff exponencial. |

> ### Atenção — este é o ponto que mais dá problema
>
> A regra de retry do contrato antigo era "nunca repetir 400/401/413/415". **404 e 422 não estavam
> nessa lista, e agora existem.** Se a regra do fluxo for "repete tudo que não está na lista de
> nunca-repetir", um lead removido ou uma imagem corrompida vira **retry infinito** batendo no CRM
> para sempre.
>
> A regra correta é a inversa e mais simples: **só 5xx é repetível. Todo 4xx é definitivo.**

---

## 7. O que o CRM já faz — não façam do lado de vocês

Ao receber o arquivo, o CRM valida os bytes, corta no centro, redimensiona para 256x256, reencoda em
PNG (o que descarta EXIF e qualquer metadado), calcula o hash e grava num bucket próprio, servido
apenas por rota autenticada. O arquivo nunca vai para o banco e a URL do storage nunca chega ao
navegador.

Portanto, **não** precisam:

- converter para JPEG;
- remover EXIF;
- redimensionar para 640x640 nem para nada;
- comprimir para caber em 512 KiB;
- calcular SHA-256;
- gerar `eventId` ou controlar idempotência.

Mandem os bytes como vieram. Menos código de vocês, menos coisa para dar errado, e a garantia de que
o que entra no nosso storage foi reprocessado por nós.

---

## 8. O que nunca mandar para o CRM

- payload cru da UAZAPI;
- URL temporária ou assinada da foto (o CRM não busca imagem em lugar nenhum);
- imagem em base64 (é `multipart`, não JSON);
- credencial da UAZAPI;
- token em query string.

## 9. O que nunca registrar em log

- o token;
- o arquivo;
- o telefone completo do contato.

---

## 10. Checklist antes de ligar em produção

- [ ] Rota `/internal/v1/leads/{leadId}/foto`, não `/api/integrations/...`
- [ ] Header `X-Synapse-Token`, não `Authorization: Bearer`
- [ ] Parte do multipart chamada `arquivo`
- [ ] Remoção usando `DELETE`, não `operation: REMOVE`
- [ ] Nenhum campo extra (`eventId`, `sha256`, `phone`, `provider`, `sourceUpdatedAt`)
- [ ] Retry **só** em 5xx; todo 4xx descarta
- [ ] `INALTERADA` tratado como sucesso
- [ ] Nada de tratamento para `STORED_PENDING` / `IGNORED_STALE` — não existem mais
- [ ] Token fora de log e fora da URL
- [ ] Testado em homologação antes de apontar para produção
