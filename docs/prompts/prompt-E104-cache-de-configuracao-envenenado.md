# Prompt E104 — Cache de configuração da automação envenenado com lista vazia

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/cache-configuracao-automacao`) e PR.
> **Sem merge, sem deploy.** Um módulo do backend: `./mvnw -pl crm-automacao-config -am verify`.
> Sem migration, sem frontend.

---

## O que aconteceu em produção

A aba **Automação → Parâmetros avançados** ficou mostrando *"Nenhum parâmetro cadastrado"* com a
tabela `configuracao_automacao` cheia. Diagnóstico fechado no Redis de produção:

```
> GET synapse:config-automacao:todas
"[]"
> TTL synapse:config-automacao:todas
(integer) -1
```

Lista vazia gravada, **sem expiração**. Resolvido na hora com `DEL`, mas o defeito que permitiu isso
continua no código.

## Bloco 1 — Por que isso trava sozinho

Três decisões que, isoladas, parecem razoáveis, e juntas formam um poço sem saída. Leia
`ConfiguracaoAutomacaoRepositorioJpa` e confirme cada uma:

1. `listarTodas()` devolve o cache quando ele **não é null**. Um `"[]"` desserializa numa lista
   vazia, que não é null — então o banco **nunca é consultado**.
2. `escreverNoCache` faz `redis.opsForValue().set(chave, json)` **sem TTL**. A entrada fica para
   sempre.
3. A única invalidação (`CacheDeConfiguracaoAutomacaoListener`) reage a
   `ConfiguracaoAutomacaoAtualizada`, publicado quando alguém **edita um parâmetro pela tela**. Mas a
   tela não mostra parâmetro nenhum — não há o que editar.

Basta uma única leitura em um instante em que a tabela estivesse vazia (instância recém-criada,
banco recriado, provisionamento ainda por rodar) para o `[]` ficar gravado em definitivo.

Repare que `porChave()` **não** tem o problema: ele só escreve no cache com `doBanco.ifPresent(...)`,
ou seja, nunca cacheia ausência. Confirme isso e diga no relatório — a assimetria entre os dois
métodos é a prova de que o cache negativo em `listarTodas` foi descuido, não decisão.

## Bloco 2 — A correção

Duas mudanças, e as duas são necessárias. Uma sem a outra deixa o buraco aberto.

**Não cachear ausência.** Se `findAll()` voltar vazio, **não escreva no Redis**. Devolva a lista
vazia e deixe a próxima leitura tentar o banco de novo. Um cache existe para poupar leitura de dado
que existe; guardar "não existe nada" é guardar a ausência de informação, e é o que criou o
impasse.

**TTL em toda escrita.** Mesmo cache correto precisa de teto: é a diferença entre "desatualizado por
alguns minutos" e "errado até alguém descobrir". Escolha um valor, deixe **configurável** com default
no `application.yml` (o projeto já faz assim com `OUTBOX_RESERVA_EXPIRACAO` e outros), e diga no
relatório qual escolheu e por quê. Esses parâmetros mudam raramente e são lidos o tempo todo — o TTL
é rede de segurança, não estratégia de atualização; a invalidação por evento continua sendo o
caminho normal.

**Conserte o comentário que mente.** O javadoc do `CacheDeConfiguracaoAutomacaoListener` fala em
*"cache desatualizado ate expirar/ser reescrito"* — hoje nada expira. Com o TTL, a frase passa a ser
verdade; confira que ela ficou correta em vez de deixá-la como estava.

## Bloco 3 — Varra a mesma classe de erro

Isto não é uma linha, é um padrão. Procure **todo** `opsForValue().set(` do backend e relate cada
ocorrência:

- tem TTL?
- pode gravar ausência (lista vazia, `Optional` vazio, valor nulo)?
- o que invalida, e esse caminho é alcançável quando o cache está errado?

A lista completa é entregável, mesmo para os casos que já estiverem certos. Se encontrar outro cache
capaz de se envenenar do mesmo jeito, **relate antes de corrigir** — pode ser etapa própria, e eu
prefiro saber a receber um diff grande de surpresa.

## Bloco 4 — O que esta etapa NÃO conserta

Deixe isto explícito no relatório, porque é operação e não código: **chaves já envenenadas não são
curadas pelo deploy.** O TTL novo vale para escritas novas; uma entrada existente com `TTL -1`
continua eterna. Qualquer instância que esteja hoje com `"[]"` gravado precisa de um `DEL` manual
uma vez, depois do deploy.

Não escreva rotina de limpeza no boot para resolver isso. Código que apaga cache no start é o tipo
de coisa que fica no repositório para sempre por causa de um incidente de um dia.

## Bloco 5 — Testes

- `listarTodas` com banco vazio: devolve vazio e **não escreve no Redis**. É o teste central desta
  etapa; se ele não existir, a correção não está travada.
- `listarTodas` com banco populado: escreve no cache **com** expiração, e a segunda chamada não vai
  ao banco.
- Cache contendo `"[]"` (o estado exato da produção): a leitura **não** pode devolver vazio para
  sempre — descreva no relatório qual comportamento você escolheu aqui e por quê.
- Redis fora do ar continua caindo para o banco sem quebrar a tela: o `catch` já existe, não deixe a
  mudança regredir isso.
- `porChave` continua sem cachear ausência.

## Verificação

```
./mvnw -pl crm-automacao-config -am verify      # na raiz de backend/
```

Se acabar tocando outro módulo, suba um degrau e rode `./mvnw verify` no reator, dizendo por quê.

## Relatório

1. Confirmação das três causas do Bloco 1, com arquivo e linha.
2. O TTL escolhido, onde ficou configurável, e o raciocínio.
3. A lista completa de `opsForValue().set(` do backend, com o veredito de cada um.
4. O que acontece hoje ao ler um cache que já contém `"[]"`.
5. Confirmação de que nenhuma rotina de limpeza automática foi adicionada.
