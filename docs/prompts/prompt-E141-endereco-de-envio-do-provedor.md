# Prompt E141 — O CRM envia para um número que ele mesmo derivou

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/endereco-de-envio-do-provedor`) e PR. **Sem merge, sem deploy.**
> Backend. **Uma migration nova, estrutural** (use o próximo número livre na `main`).
> `cd backend && ./mvnw -pl crm-app -am verify`.

**Cliente inalcançável em produção.** Mensagens voltam da Meta com
`131026 — Message undeliverable` para leads que **comprovadamente existem no WhatsApp**.

---

## A causa, confirmada com dado de produção — não reinvestigue

Caso real de 02/09, lead "Humberto 3987":

```
wa_id que a Meta usa (webhook_entrada.payload → "from"):   556182736306    12 dígitos
telefone gravado em lead.telefone:                        5561982736306    13 dígitos
```

Ele **escreveu** às 16:55 — o número existe. O envio para a forma de 13 dígitos voltou
`131026 undeliverable`.

A cadeia inteira está no código:

```
EnviarMensagemUseCase:254   contato.telefone()          ← lead.telefone (canônico)
        ↓
Outbox.enfileirarEnvio(..., telefoneDestino, ...)
        ↓
MetaCloudApiAdapter:238     raiz.put("to", envio.telefoneDestino())
```

A E111 normalizou `lead.telefone` para 13 dígitos, e isso **está certo**: é chave de identidade,
resolve o cliente partido em dois cadastros. O erro foi reaproveitar essa mesma string como
**endereço de envio**. São duas coisas diferentes — uma o CRM decide, a outra quem decide é o
provedor.

Tamanho do risco medido nas últimas 24h: **1.825 payloads de entrada com `"from"` de 12 dígitos
contra 64 com 13**. Quase toda a base tem `wa_id` sem o nono dígito e telefone gravado com ele. A
Meta resolve na maioria das vezes — por isso o CRM funciona o dia todo — e quando não resolve o
cliente fica permanentemente inalcançável, sem ninguém entender por quê.

## A decisão, já tomada — não a reabra

**O CRM passa a guardar o endereço que o provedor usa e a enviar para ele.** `lead.telefone`
continua exatamente como está: canônico, chave de busca, de exibição e do casamento de entrada.
Nada da E111 muda.

**Quando não houver endereço conhecido, envia no canônico**, como hoje. Contato que nunca escreveu
não tem `wa_id`, e o CRM **não deve adivinhar** removendo o nono dígito "porque 96% da base é
assim" — adivinhar foi o que produziu este bug. Se falhar, o `131026` já é a resposta honesta ao
atendente.

## Bloco 1 — Migration estrutural, e só

Uma coluna anulável em `lead`, algo como `telefone_provedor VARCHAR(30)`, com `COMMENT` explicando
que é **o identificador do destinatário no provedor** — não um segundo telefone, não algo para
buscar, não algo para exibir na ficha.

**Nada de `UPDATE` nesta migration.** Sem backfill, sem `DELETE`, sem `CREATE TABLE`. Só
`ADD COLUMN` e o comentário. O preenchimento retroativo é o Bloco 4, fora do deploy.

Sem `UNIQUE`: dois leads com o mesmo `wa_id` não deveriam existir depois da fusão da V50, mas uma
constraint nova que derruba o deploy por causa de dado legado é pior que a duplicata. Se você achar
que precisa, **pare e explique**.

## Bloco 2 — Gravar o endereço a cada mensagem recebida

O valor certo é o `"from"` **cru** do payload — o que chega em
`TradutorDeCanal.MensagemRecebidaDoCanal.telefoneRemetente`, antes de qualquer normalização.

Grave-o no lead no mesmo caminho que já resolve o lead pela mensagem de entrada
(`LeadNoCaminhoDeMensagemJdbc`, junto de `visivelPorTelefone`/`criarPorTelefone`), na mesma
transação. Sobrescreva sempre: se o cliente migrar de número no provedor, a última mensagem manda.

Lead criado por mensagem de entrada nasce com os dois campos preenchidos.

## Bloco 3 — Enviar para o endereço, com fallback

`ContatoParaEnvio` (`LeadNoCaminhoDeMensagem:115`) ganha o endereço de envio, e
`contatoParaEnvio` (`LeadNoCaminhoDeMensagemJdbc:240`) passa a resolvê-lo **no SQL**, com
`COALESCE(telefone_provedor, telefone)` — uma definição só, no banco, em vez de um `if` em Java que
alguém duplica depois.

`EnviarMensagemUseCase:254` e `:264` passam a usar esse campo. **Não mexa** no `Outbox`, no
`CanalGateway` nem no `MetaCloudApiAdapter`: eles já recebem `telefoneDestino` e continuam iguais —
muda só o que é colocado ali dentro.

## Bloco 4 — Backfill, fora do deploy

O `webhook_entrada.payload` guarda o `"from"` real de **todo** lead que já escreveu. Dá para
preencher a coluna retroativamente sem chutar nada.

Escreva isso como script em `docker/provisionamento/`, no padrão do
`simular-fusao-nono-digito.sql` — **não** como migration. O `app_telefone_com_ddi` e a regra do
nono dígito já existem como função SQL desde a V50: use-as para canonizar cada `"from"` e casar com
`lead.telefone`, em vez de reescrever a regra.

O script precisa ser conferível antes de escrever: uma consulta que mostra quantos leads seriam
preenchidos e quantos `"from"` distintos casam com o mesmo lead, antes do `UPDATE`. Se um lead
casar com dois `wa_id` diferentes, **não atualize esse lead** — registre e siga.

## Testes obrigatórios

1. **O caso do Humberto, de ponta a ponta:** webhook de entrada com `"from":"556182736306"`, lead
   gravado com `telefone = '5561982736306'`; depois um envio, e o `telefoneDestino` que chega na
   outbox é **`556182736306`**.
2. Mensagem de entrada grava `telefone_provedor` e **não altera** `lead.telefone`.
3. Segunda mensagem com `"from"` diferente sobrescreve o `telefone_provedor`.
4. Lead sem `telefone_provedor` (nunca escreveu) envia no canônico — comportamento de hoje,
   sem regressão.
5. Busca, filtro da agenda e casamento de entrada por telefone continuam usando `lead.telefone` e
   não enxergam a coluna nova.
6. `NonoDigitoMigrationIT` e `TelefoneNonoDigitoIT` continuam verdes sem alteração.

## Fora do escopo

- Mudar `TelefoneCanonico`, a V50, a regra do nono dígito ou o índice `ux_lead_telefone`.
- Exibir o `telefone_provedor` em qualquer tela.
- Adivinhar endereço para quem nunca escreveu.
- O motivo da falha no balão — é a E140.
- Qualidade dos números importados sem DDD — não é código.

## Definição de pronto

- Lead que já escreveu recebe mensagem no endereço que a Meta usa.
- `lead.telefone` intocado; busca e casamento inalterados.
- Migration só estrutural; backfill em script separado, com conferência antes do `UPDATE`.
- Existe teste que reproduz o caso do Humberto e reprova se o envio voltar a usar o canônico.
- `./mvnw -pl crm-app -am verify` verde; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
