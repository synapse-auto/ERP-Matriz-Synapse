# Prompt E35b — os testes que a E35 não escreveu

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — proteção escrita, proteção não exercitada

A E35 (`c087342`) entregou o CRUD de follow-up e fidelização e a validação de placeholders. O
relatório marcou como pronto:

> ✅ Placeholder validado no domínio; apenas `{nome}` é aceito.
> ✅ Mensagens vazias, tempos inválidos e placeholders desconhecidos retornam `422`.

E, três linhas abaixo:

> ⚠️ Não adicionei novos testes específicos de CRUD/placeholder.

As duas primeiras linhas são **afirmação sem evidência**. Os 296 testes que passaram no CI já
passavam antes — nenhum deles entra no caminho novo.

Este é o caso número 1 da lista do `docs/13`: proteção que existe, passa no teste, e não protege
nada. A RLS estava escrita e não protegia; só o teste negativo expôs. Aqui a situação é a mesma com
uma agravante — a validação de placeholder é a única coisa entre o usuário e uma mensagem com
`{telefone}` literal chegando no WhatsApp do cliente.

**Esta etapa não muda comportamento.** Se um teste falhar, você encontrou um defeito real da E35 —
conserte e relate em item próprio.

### O ponto que decide se esta etapa vale alguma coisa

O relatório diz que a validação está *"centralizada em uma classe de domínio pura"*. Uma classe pura
com teste unitário próprio passa **mesmo que nenhum controller a chame**. É exatamente assim que
este projeto colecionou quinze casos.

> **Teste a rota HTTP, não a classe.** Todo teste desta etapa entra pelo controller, como o runtime
> entra. Teste que chama o validador direto não conta como feito.

---

## Bloco 1 — CRUD pelo controller

Para **follow-up** e **fidelização**, nos casos de uso já existentes em
`com.synapse.crm.automacaoconfig.application.regras`:

- Criar, listar, atualizar, alternar ativo e excluir — cada operação pela rota real.
- **Autorização, com o negativo:** papel sem permissão recebe `403` **e nada é gravado**. Verificar
  o status sem verificar o banco deixa passar o caso em que a escrita acontece e o erro é cosmético.
  Os casos de uso administrativos são `hasAnyRole('GESTOR','SUBGESTOR','ADMINISTRADOR')` — teste com
  ATENDENTE.
- **Ordenação:** crie os registros fora de ordem e confirme que a listagem sai ordenada por
  `tempo_minutos` (follow-up) e `dias_sem_contato` (fidelização). Sem isto, a ordem muda a cada
  `UPDATE` e ninguém percebe até a tela embaralhar sozinha.
- **Conversão de unidade:** um follow-up gravado com múltiplo de 1440 minutos volta como Dias; um
  que não é múltiplo volta como Horas. Inclua o valor de fronteira (1440 exato).
- `nome` derivado do tempo: confirme que a gravação preenche o campo `NOT NULL` sem o usuário
  informar nada.

## Bloco 2 — A validação, pela rota

Cada caso abaixo entra por HTTP e verifica **duas** coisas: o status e a ausência de escrita.

- Mensagem com `{telefone}` → `422`, e o corpo do Problem Details **nomeia** o placeholder recusado
  e lista os válidos. Mensagem que só diz "inválido" obriga o usuário a adivinhar.
- Mensagem com `{nome}` → aceita.
- Mensagem vazia e mensagem só com espaço → `422`.
- `tempo_minutos = 0` e negativo → `422`, **sem** chegar no `CHECK` do banco. O `CHECK` é a última
  linha de defesa, não a primeira; se o `422` vier de violação de constraint, o teste passa pelo
  motivo errado — verifique que a mensagem de erro é da aplicação.
- `dias_sem_contato = 0` e negativo → `422`.
- Atualização com valor inválido → `422` **e o registro permanece com o valor antigo**. Este é o
  caso que uma validação só de criação deixa passar.

## Bloco 3 — O que o n8n enxerga

- Regra ativa aparece na leitura de `/internal/v1`; regra inativa **não** aparece.
- Alternar para inativo remove da leitura interna **e mantém** na listagem administrativa. Desligar
  não pode ser sinônimo de excluir.
- Rota interna sem token e com token inválido → `401`.

## Bloco 4 — A aba persiste na recarga

Requisito do prompt da E35 que voltou como pergunta no relatório. A resposta é sim.

- A aba selecionada (Geral · IA, Follow-up, Fidelização) sobrevive a recarregar a página.
- Use o mecanismo que o resto do frontend já usa para estado navegável. Não invente um segundo.
- Teste de frontend cobrindo: selecionar a aba, recarregar, continuar na mesma.

---

## Definição de pronto

- [ ] CRUD das duas entidades testado pelo controller, incluindo o `403` com verificação de banco
- [ ] Ordenação testada com registros criados fora de ordem
- [ ] Conversão Horas/Dias testada, com o valor de fronteira
- [ ] Os seis casos de validação do Bloco 2, cada um verificando status **e** ausência de escrita
- [ ] Ativo/inativo refletido na leitura interna e preservado na administrativa
- [ ] `401` nas rotas internas sem token e com token inválido
- [ ] Aba persistente, com teste
- [ ] Nenhum teste novo chamando validador ou caso de uso direto
- [ ] CI verde com **número da run**

## No relatório

1. **Os nomes dos testes novos, um por linha** — classe e método. Não informe o total da suíte:
   "296 testes passaram" não distingue teste novo de teste que já passava, e foi exatamente isso que
   escondeu o buraco da E35.
2. Se algum teste falhou de primeira: qual, por quê, e o que você consertou. **Teste que passa de
   primeira sobre código não testado merece desconfiança** — confirme que ele falha quando você
   quebra a validação de propósito.
3. Se o `422` de tempo inválido vinha do `CHECK` do banco em vez da aplicação.
4. Variável nova no Dokploy: expectativa **nenhuma**.
5. SHA final e o **SHA curto** — `SYNAPSE_IMAGE_TAG` usa a tag curta que o
   `type=sha,format=short` publica, não o hash de 40 caracteres.

---

## Fora desta etapa

Mudar comportamento, endpoint ou contrato. Mexer nos casos de uso de leitura que hoje são
`isAuthenticated()` — é dívida anterior à E35, já registrada, e não se mexe a seis dias da entrega.
E36 (aba Geral · IA) segue esperando as quatro decisões.
