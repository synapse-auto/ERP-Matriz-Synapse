# Prompt E111 — Nono dígito: leads duplicados pelo mesmo telefone

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/telefone-nono-digito`) e PR.
> **Sem merge, sem deploy.** Migration + mais de um módulo → `./mvnw verify` no reator.
>
> **Isto altera dados de clientes reais em produção, de forma irreversível.** Leia o Bloco 5 antes de
> escrever a migration. Nada é executado em produção nesta etapa: a entrega é o código, a migration e
> o **relatório de simulação** que o Marcondes vai conferir antes de autorizar o deploy.

---

## O problema, já diagnosticado em produção

23 clientes estão em **dois cadastros cada**, sempre o mesmo número com e sem o nono dígito:

```
{5561981536371, 556181536371}   "Jair real 1814" / "Adjair"
{5561992729612, 556192729612}   "Comercial Vidros" / "JCA VENDAS 2860"
{5561998430401, 556198430401}   "Lucas Rezende" / "Lucas Rezende"
```

**Causa:** a Meta entrega o `wa_id` de boa parte dos números brasileiros **sem o nono dígito**. A
mensagem que chega cria/casa o lead com 12 dígitos. O lead cadastrado à mão ou importado usa o
formato de discagem, com 13. `TelefoneCanonico` só tira máscara e completa DDI (V24/V26) — **não
toca no nono dígito** —, então para o banco são dois clientes e o índice único não vê duplicata.

Consequências reais, todas confirmadas hoje:

- **"não consigo puxar o cliente"** — o número existe no outro cadastro, o índice único barra a
  criação e o atendente recebe 404 (é a Parte 1 da E105, agora com causa provada);
- **histórico partido** — o Gustavo abriu um cadastro com 2 mensagens enquanto as 147 do cliente
  estavam no outro;
- **template não recebido** — enviado do cadastro sem janela aberta.

## Bloco 0 — A armadilha que define a ordem de tudo

**Normalizar na entrada, sozinho, piora o problema.**

Se o código passar a normalizar para 13 dígitos e os leads existentes continuarem com 12,
`visivelPorTelefone("5561981536371")` deixa de achar `556181536371`, o código cria um lead novo, e o
cliente passa a ter **três** cadastros.

Portanto: **a mudança de código e a migração de dados têm que subir juntas, no mesmo deploy.** Nada
de entregar a normalização "e depois rodar um script". A migration é o veículo certo justamente
porque roda no start, antes de a aplicação atender.

## Bloco 1 — A regra de canonicalização

Depois de tirar máscara e garantir o DDI (o que a V24/V26 já fazem), olhe o que sobra depois de
`55` + DDD:

- **8 dígitos começando em 6, 7, 8 ou 9** → é celular que perdeu o nono dígito. **Prefixe `9`.**
- **8 dígitos começando em 2, 3, 4 ou 5** → é telefone fixo. **Não mexa.**
- **9 dígitos** → já está canônico. Não mexa.
- Qualquer outro tamanho → **não mexa** e não invente. Se aparecer algo fora desses casos na
  migração, o Bloco 5 manda abortar.

A regra é confiável porque no Brasil fixo nunca começa com 8 ou 9, e desde 2016 todo celular tem 9
dígitos começando com 9. Confirme se `TelefoneCanonico` é mesmo o único ponto de normalização —
se houver um segundo, **relate**, porque dois lugares normalizando é como este bug nasceu.

Escreva a regra **uma vez** e reutilize: o código Java e a migration precisam produzir exatamente o
mesmo resultado. Se a migration reescrever a regra em SQL por conta própria, elas vão divergir. Diga
no relatório como garantiu isso.

## Bloco 2 — Antes de escrever a migration: descubra o que aponta para `lead`

Não liste as tabelas de memória. Levante do catálogo:

```sql
SELECT conrelid::regclass AS tabela, conname
  FROM pg_constraint
 WHERE confrelid = 'lead'::regclass AND contype = 'f';
```

Toda tabela que referencia `lead(id)` precisa ser tratada na fusão. Esquecer uma significa linha
órfã ou violação de chave estrangeira no meio do deploy. Liste todas no relatório, com o que você
fez para cada uma.

Cuidado com as indiretas: `mensagem` aponta para `atendimento`, não para `lead`. Se você mover o
`atendimento`, as mensagens vão junto — confirme, não presuma.

## Bloco 3 — Regra de fusão

Para cada par (um com 12 dígitos, outro com 13, mesmo DDI+DDD+final):

**Sobrevive o lead que tem a conversa.** Critério objetivo, nesta ordem: mais mensagens; empate, mais
atendimentos; empate, o mais antigo. É onde está o histórico e é com quem o cliente vem falando.

**O dono é o do sobrevivente.** É quem vem atendendo de verdade. Não invente merge de dono.

**O telefone do sobrevivente passa a ser o canônico** (13 dígitos).

**Campos vazios no sobrevivente podem ser preenchidos pelo outro** — e-mail, empresa, CPF,
localização, código. Campo preenchido nos dois: **fica o do sobrevivente**, e o valor descartado
entra no relatório. Nunca concatene.

**O nome não é fundido.** Fica o do sobrevivente. No caso do Jair, isso significa manter "Adjair" em
vez de "Jair real 1814" — pior de ler, e é de propósito: nome é escolha humana, não regra. O
relatório lista os pares para a operação corrigir depois pela tela.

**O lead perdedor é apagado** só depois de tudo movido.

## Bloco 4 — A ordem dentro da migration

1. Fundir os pares. Só depois disso o índice único deixa de colidir.
2. Normalizar o telefone de **todos** os leads restantes.
3. Se sobrar colisão depois de normalizar, é caso que a regra não previu → **abortar** (Bloco 5).

Inverter 1 e 2 estoura no `ux_lead_telefone`.

## Bloco 5 — Falhar alto, nunca adivinhar

A migration **aborta com `RAISE EXCEPTION`** se encontrar:

- um "par" com mais de dois leads no mesmo final;
- colisão de telefone depois da normalização que não seja um par tratado;
- telefone fora dos formatos do Bloco 1;
- qualquer FK nova apontando para `lead` que o Bloco 2 não previu.

Abortar derruba o deploy, e é o comportamento certo: melhor a aplicação não subir do que fundir o
cliente errado. É o mesmo princípio do `provisionar-instancia.sql`, que aborta quando falta chave.

## Bloco 6 — O relatório de simulação (entregável desta etapa)

Escreva também uma **consulta somente-leitura** que mostre exatamente o que a migration faria, para
rodar em produção **antes** do deploy:

- cada par, com telefone, nome, dono, nº de atendimentos e de mensagens dos dois lados;
- qual sobreviveria e por qual critério;
- quais campos seriam preenchidos e quais valores seriam descartados;
- quantos leads teriam o telefone normalizado sem fusão;
- qualquer caso que faria a migration abortar.

Deixe-a em `docker/provisionamento/` ou em `docs/`, com o comando pronto para colar. **O Marcondes
roda isso e aprova antes de qualquer deploy.**

## Bloco 7 — O que não muda

- Nenhuma política RLS.
- Nenhuma mudança em quem enxerga o quê.
- O índice único `ux_lead_telefone` continua como está.
- A importação do CSV (E105) **continua parada** — ela só pode rodar depois que esta subir, e o
  arquivo dela vai precisar passar pela regra nova. Diga isso no relatório.

## Bloco 8 — Testes

- `TelefoneCanonico`: 12 dígitos começando em 6–9 ganha o 9; começando em 2–5 não ganha; 13 dígitos
  não muda; fixo não muda; entrada inválida continua recusada como antes.
- **O caso que prova a etapa:** lead gravado com 12 dígitos, mensagem chegando com 12 dígitos, e
  atendente iniciando contato digitando 13 — tudo cai no **mesmo** lead, e nenhum lead novo é criado.
- IT da migration: par com conversa de um lado e cadastro vazio do outro funde no lado certo, com o
  dono certo, sem perder mensagem nem atendimento.
- IT da migration: caso fora da regra faz a migration **abortar**, e o teste prova isso.
- Regressão da E105 Parte 1: com a normalização no ar, "puxar" pelo telefone com 9 acha o lead que
  entrou com 12.

## Verificação

```
./mvnw verify        # no reator, na raiz de backend/
```

## Relatório

1. A regra do Bloco 1 e onde ela mora — e como código e migration compartilham a mesma definição.
2. A lista completa de FKs apontando para `lead`, e o tratamento de cada uma.
3. O número da migration (confirme com `git fetch`; `main` estava na V49).
4. O comando pronto do relatório de simulação (Bloco 6).
5. O que a migration faria hoje em produção, rodando a simulação contra os 23 pares conhecidos.
6. Qualquer caso que faria abortar.
