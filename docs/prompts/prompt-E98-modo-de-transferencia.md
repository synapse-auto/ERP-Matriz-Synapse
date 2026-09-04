# Prompt E98 — Parâmetro de modo de transferência para a Automação

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/modo-de-transferencia`) e PR. **Sem merge, sem deploy.**
> Toca migration → verificação no degrau da migration: `./mvnw verify` no reator. **Não** roda a suíte
> do frontend, porque esta etapa não muda uma linha de `frontend/` (confirme isso, veja o Bloco 3).

---

## O pedido

O integrador precisa de um parâmetro no CRM que diga qual modo de transferência a Automação deve
usar: o **padrão** de hoje, ou um modo **por lista**. Quem executa a transferência é o n8n — o CRM só
publica a escolha. É a RN-CRM-07 funcionando como desenhada: o CRM configura, não executa.

Uma chave nova em `configuracao_automacao`, tipo `BOOLEAN`. Nada além disso.

## Bloco 1 — A migration

`origin/main` está em **V48** (`V48__foto_de_perfil_do_lead.sql`, mergeada no PR #33). Faça
`git fetch` e confirme antes de numerar: a próxima é a **V49**.

Copie a forma da `V38__comando_reset_da_automacao.sql` — é o precedente exato de "adicionar uma chave
de configuração da automação", inclusive no `ON CONFLICT (chave) DO NOTHING`:

```sql
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('transferencia.por_lista', 'false', NULL, 'BOOLEAN', NULL, NULL,
     '<descricao>')
ON CONFLICT (chave) DO NOTHING;
```

Três coisas que **não** são detalhe:

**O valor inicial é `false`, e não é negociável.** `false` é o comportamento de hoje. Uma chave que
nasce ligada muda o comportamento da instância no instante do deploy, sem ninguém ter pedido — foi
exatamente o que aconteceu na E52, quando "Novo follow-up" nasceu ativo e criou regra viva que o n8n
mandaria para cliente real. Quem liga é a gestão, pela tela, quando o fluxo do outro lado estiver
pronto.

**`BOOLEAN`, não `TEXT`.** Existem chaves `TEXT` na tabela (`automacao.comando_reset`,
`alerta.horario_cliente.inicio`), mas todas são texto que é mesmo livre. Aqui são dois estados
fechados, e `TEXT` não tem validação nenhuma — `case TEXT -> // texto livre` em
`ConfiguracaoAutomacao.validar` — e a tela renderiza `<Textarea>`. Um `Lista ` com espaço sobrando
quebraria a transferência em produção sem erro nenhum. `BOOLEAN` vira caixa de seleção e é
impossível digitar errado.

**A `descricao` é o rótulo da tela.** `LinhaParametro` renderiza `parametro.descricao ?? parametro.chave`.
Escreva uma frase que um gestor entenda sozinho, dizendo o que acontece quando está ligado e o que
acontece quando está desligado. Sem acento, como as outras migrations do projeto.

## Bloco 2 — O seed de desenvolvimento

`backend/crm-app/src/main/resources/db/seed/R__seed_dev.sql` também lista as chaves. A `V38` entrou
lá; siga o mesmo caminho, com o mesmo valor inicial `false`.

**Não** mexa em `docker/provisionamento/automacao-padrao.sql` nem em
`docker/provisionamento/instancias/instancia.exemplo.env.example`. Confirme você mesmo o precedente
antes de aceitar isto: nenhuma das chaves adicionadas por migration (V23, V27, V33, V38) está nesses
arquivos, porque a migration roda em toda instância e o JSON de provisionamento é só o conjunto
inicial. Se a sua leitura do repositório contradisser isso, **relate em vez de decidir sozinho.**

## Bloco 3 — O que esperamos que NÃO mude (confirme, não assuma)

Cheque cada um destes e diga no relatório o que encontrou:

- **Frontend:** `CampoValor` em `pagina-automacao.tsx` já trata `tipo === "BOOLEAN"` com checkbox.
  A expectativa é zero mudança em `frontend/`. Se algo lá assume uma lista fixa de chaves, ou se
  `pagina-automacao.test.tsx` afirma a quantidade/conjunto exato de parâmetros, isso aparece como
  teste vermelho — nesse caso conserte o teste, não o comportamento.
- **Contrato interno:** a chave é **dado**, não schema. `ContratoInternalV1IT` e
  `internal-v1-snapshot.json` não podem mudar. Se mudarem, algo saiu do lugar.
- **Provisionamento:** `provisionar-instancia.sql` exige as quatro chaves de mídia e aborta se
  faltarem. Uma chave a mais não entra nessa contagem — confirme lendo, não por dedução.

## Bloco 4 — Onde o integrador lê

Nada a fazer aqui, é só para você entender o destino: a chave aparece sozinha em
`GET /internal/v1/automation-config` e em `GET /internal/v1/automation-config/{chave}`, porque o
controller devolve a tabela inteira. Não escreva endpoint novo.

## Verificação

Migration → degrau da migration:

```
./mvnw verify        # no reator, na raiz de backend/
```

Sem `npm` nesta etapa, a menos que o Bloco 3 revele que o frontend realmente precisa mudar — e se
precisar, rode a suíte do frontend e diga por que precisou.

## Relatório

1. O texto exato da `descricao` que você escreveu, e por quê.
2. O que você encontrou nos três itens do Bloco 3.
3. Confirmação de que a chave nasce `false` na migration **e** no seed.
4. Qualquer arquivo que lista chaves de configuração e que você decidiu **não** tocar, com o motivo.

---

# E98 — Continuação: ajustes antes do push

> O commit `727ae1d` já existe na branch `feat/modo-de-transferencia` e **ainda não foi enviado**.
> Nada aqui exige migration nova: é `--amend` no commit que já está aí.

## 1. Descrição neutra nos dois arquivos

A descrição atual afirma que ligado *"transfere o lead percorrendo a lista de atendentes"*. Essa
mecânica vive no fluxo do n8n, ninguém aqui a verificou, e ela pode mudar sem passar por nós — mas o
texto vai para o banco e o CRUD só atualiza `valor`, nunca `descricao`. Rótulo errado gravado vira
migration nova para corrigir.

Troque, **idêntico** nos dois lugares (`V49__modo_de_transferencia.sql` e `R__seed_dev.sql`), por:

```
Modo de transferencia usado pela Automacao: ligado, ela usa o modo por lista; desligado, mantem o modo padrao de hoje. Quem executa a transferencia e a Automacao; o CRM apenas publica a escolha.
```

Não mexa em mais nada da linha: chave, `'false'`, `BOOLEAN` e os `NULL` continuam como estão. E os
comentários no topo da V49 continuam podendo explicar o raciocínio — a restrição é só sobre o texto
que a tela mostra.

## 2. O assunto do commit

A primeira linha da mensagem é um `@` solto, e há outro `@` no fim. O assunto real está na segunda
linha. Isso vira o título do PR. No `--amend`, apague os dois e deixe
`feat: parametro de modo de transferencia para a Automacao` como primeira linha.

Aproveite para corrigir uma imprecisão no corpo: o travessão em `false — o comportamento de hoje`
é o único caractere não-ASCII da mensagem; troque por hífen, como o resto do projeto.

## 3. Push e PR

Isto faltou no relatório anterior e é o que trava o merge:

```
git commit --amend
git push -u origin feat/modo-de-transferencia
```

Abra o PR. **Sem merge, sem deploy.** Confirme que os jobs de backend e stack ficaram verdes e
relate os links — a publicação de imagens é pulada em branch, isso é o esperado.

## 4. Limpeza herdada da E97

`stash@{0}` (`On feat/foto-de-perfil-do-lead: E97: ressalva do limite de 5MB no contrato do
integrador`) guarda um texto **incorreto** e não deve voltar para lugar nenhum: ele afirma que
`anexo.tamanho_maximo_imagem_mb` só existiria pelo seed de desenvolvimento e que o 413 poderia não
existir em produção. `docker/provisionamento/provisionar-instancia.sql:212-225` conta as quatro
chaves de mídia, essa inclusa, e **aborta o provisionamento com `RAISE EXCEPTION`** se faltar
alguma. Instância provisionada sempre tem a chave e sempre tem o limite de 5 MB.

Rode `git stash drop stash@{0}` e confirme no relatório. Não reaplique, não reescreva o
`docs/23-contrato-foto-de-perfil-do-lead.md`.

## Verificação

O conteúdo mudou só em texto de `descricao`, mas ainda é migration: `./mvnw verify` no reator.
Sem `npm`.

## Relatório

1. O texto final da `descricao`, copiado dos dois arquivos, para eu conferir que são idênticos.
2. A mensagem de commit depois do `--amend`.
3. Link do PR e dos runs de CI.
4. Confirmação de que `stash@{0}` foi removido.
