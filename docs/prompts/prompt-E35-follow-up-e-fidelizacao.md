# Prompt E35 — Automação: abas Follow-up e Fidelização

> Leia `AGENTS.md`, `frontend/AGENTS.md` e `design/TOKENS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — o schema existe desde a V7 e nunca teve caso de uso

A tela de Automação hoje tem só a aba **Geral · IA**, e mesmo essa parcial: os quatro cards de
telemetria e a lista de parâmetros chave/valor. O protótipo aprovado tem **três abas**. Esta etapa
entrega as duas que estão prontas por baixo.

As tabelas nasceram na `V7__automacao_config.sql` e nunca foram lidas por nenhum caso de uso:

```sql
CREATE TABLE regra_follow_up (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome          VARCHAR(100) NOT NULL,
    tempo_minutos INT NOT NULL CHECK (tempo_minutos > 0),
    texto         TEXT NOT NULL,
    ativo         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE regra_fidelizacao (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dias_sem_contato INT NOT NULL CHECK (dias_sem_contato > 0),
    mensagem         TEXT NOT NULL,
    ativo            BOOLEAN NOT NULL DEFAULT TRUE
);
```

**A primeira linha do arquivo decide o tamanho desta etapa:**

```sql
-- O CRM configura a automacao; nao a executa (RN-CRM-07).
```

> **Não construa agendador, `@Scheduled`, job de varredura ou disparo.** Quem varre lead sem
> resposta e envia é o n8n. Esta etapa entrega **cadastro** e **leitura**. O projeto já quebrou uma
> vez com `@Scheduled` (auto-invocação, mensagens quebradas nos dois sentidos com build verde) —
> não é o lugar de tentar de novo.

### Leia isto antes de criar qualquer endpoint

```text
backend/crm-automacao-config/src/main/java/com/synapse/crm/automacaoconfig/interfaces/
    ConfiguracaoAutomacaoController.java          <- padrão de CRUD e autorização a espelhar
    StatusAutomacaoTelemetriaController.java
    internal/AutomationConfigInternalController.java   <- 8,5 KB, JÁ EXISTE
```

**`AutomationConfigInternalController` pode já expor essas regras ao n8n.** Abra e diga no relatório
o que ele expõe hoje. Se as regras já saem por ali, **não crie rota nova**: estenda o que existe.
Criar um segundo contrato para o mesmo dado é como o Dylan acaba com dois endpoints e nenhuma
certeza de qual é o certo.

### Padrão do frontend a seguir

```text
frontend/src/lib/automacao/{api.ts,types.ts,use-automacao.ts}
frontend/src/components/automacao/pagina-automacao.tsx
```

Os hooks já usam TanStack Query com `queryKey` nomeada e `invalidateQueries` no `onSuccess`. Siga o
mesmo desenho; não introduza um segundo jeito de buscar dado nesta tela.

---

## Bloco 1 — Follow-up: cadastro e leitura

Cada regra: tempo sem resposta, mensagem, ativo/inativo.

- CRUD completo em `/api/v1/automacao/follow-ups` — listar, criar, atualizar, alternar ativo,
  excluir.
- **Autorização espelhando `ConfiguracaoAutomacaoController`.** Não invente papel novo nem afrouxe:
  leia o `@PreAuthorize` de lá e use o mesmo.
- Listagem **ordenada por `tempo_minutos`**. A tabela não tem coluna de ordem, e a tela mostra os
  cards em ordem crescente de tempo. Sem `ORDER BY`, a ordem muda a cada `UPDATE`.
- `tempo_minutos` é a unidade de armazenamento. A tela oferece **Horas** ou **Dias** — a conversão
  é da borda, não do banco. Na leitura, derive a unidade do valor (múltiplo de 1440 → Dias; caso
  contrário Horas) e diga no relatório se concorda com essa regra.

**Divergência entre a tabela e a tela, resolva e relate.** `regra_follow_up.nome` é `NOT NULL`, e o
protótipo **não tem campo de nome** — o rótulo do card ("2 horas sem resposta") é derivado do tempo.
Gere o `nome` a partir do tempo na gravação, em vez de pedir ao usuário um dado que a tela não
coleta. Se você concluir que `nome` deveria virar campo visível, **pare e me avise** em vez de
inventar um formulário que o protótipo não tem.

## Bloco 2 — Fidelização: cadastro e leitura

Mesma forma, sobre `regra_fidelizacao`: `dias_sem_contato`, `mensagem`, `ativo`.

- CRUD em `/api/v1/automacao/fidelizacao`, mesma autorização, ordenado por `dias_sem_contato`.
- Sem conversão de unidade: a tela fala em dias e a coluna guarda dias.

## Bloco 3 — Placeholders: conjunto fechado, validado na gravação

As duas telas dizem, abaixo do campo de mensagem:

> *Use `{nome}` para inserir o nome do cliente automaticamente.*

Quem substitui é o n8n, na hora do envio. Consequência: **se o usuário digitar `{telefone}`, o
cliente recebe `{telefone}` literal no WhatsApp** — e ninguém descobre até alguém reclamar.

- Defina o conjunto suportado em **um lugar só**, no domínio. Nesta etapa é `{nome}`; a lista tem
  que ser extensível sem caçar string por arquivo.
- **Valide na gravação**, não no envio. No envio quem manda é o n8n e o erro já saiu.
- Placeholder desconhecido → `422` com Problem Details dizendo **qual** placeholder e **quais** são
  válidos. Mensagem que só diz "inválido" obriga o usuário a adivinhar.
- Mensagem vazia ou só espaço → `422`.
- Documente o conjunto em `docs/16-acesso-da-automacao.md`, porque quem consome é o Dylan.

## Bloco 4 — A tela: três abas, cards e prévia

Abas **Geral · IA** (a que existe) · **Follow-up** · **Fidelização**. A aba ativa precisa
sobreviver a recarregar a página.

Cada card: rótulo derivado do tempo, alternador Ativo/Inativo, botão de excluir, campo numérico,
seletor Horas/Dias (só no Follow-up), textarea da mensagem e a linha de ajuda do placeholder.
Contador no topo ("6 follow-ups") e botão de adicionar.

**Excluir pede confirmação.** A E31 já criou o diálogo de confirmação ao desativar usuário —
reutilize aquele componente, não faça um segundo.

### A prévia do WhatsApp

Painel à direita renderizando a mensagem como o cliente vai ver, com o placeholder já substituído,
e o rodapé explicando o gatilho ("Enviado após 2 horas sem resposta").

> **Nenhum literal de cliente no código.** O protótipo mostra "Estrutural Vidros" no cabeçalho da
> prévia e "Marcos" como nome de exemplo. Os dois vêm do catálogo de textos — igual ao resto da
> aplicação. Isto é Base PAI: o próximo filho troca `textos.json` e a prévia acompanha. Literal em
> `.tsx` quebra a regra mais cara do projeto.

> **Nenhuma cor hardcoded.** O verde do cabeçalho e o bege do fundo da conversa são cor de marca de
> terceiro. Entram como token nomeado no tema, não como `#075E54` no JSX.

Estados de carregamento, erro e vazio como o resto da tela já faz — a página atual usa
`ErroDeCarregamento` com `onTentarNovamente`, e some com os cards em vez de mostrar zero. Mantenha
a coerência: **zero parece dado, ausência precisa parecer ausência.**

## Bloco 5 — O que o n8n lê

Depois de responder o que `AutomationConfigInternalController` já expõe:

- As regras **ativas** de follow-up e fidelização precisam estar legíveis em `/internal/v1`, com
  `X-Synapse-Token` e `ROLE_SERVICO`.
- Regra inativa não aparece para o n8n. Desligar o alternador tem que ter efeito **sem** exigir
  exclusão.
- Documente o formato em `docs/16`, com exemplo de resposta.

---

## Testes — a proteção nasce com um teste que a viola

- Criar, listar, atualizar, alternar e excluir follow-up e fidelização, pelo controller real.
- Autorização: papel sem permissão → `403`, **e nada gravado**.
- `tempo_minutos = 0` ou negativo → `422`, sem escrita. O `CHECK` do banco não pode ser a primeira
  linha de defesa.
- Mensagem com `{telefone}` → `422`, com o nome do placeholder na resposta.
- Mensagem vazia → `422`.
- Listagem devolve ordenado por tempo, com registros criados fora de ordem.
- Alternar para inativo → some da leitura de `/internal/v1` e **continua** na listagem do painel.
- `/internal/v1` sem token e com token inválido → `401`, em cada rota nova.
- Front: alternar aba, criar, editar, excluir com confirmação, e a prévia refletindo o texto
  digitado com o placeholder substituído.
- Front: nenhuma string literal de UI e nenhuma cor literal nos arquivos novos.

## Definição de pronto

- [ ] CRUD de follow-up e fidelização, com a autorização do controller existente
- [ ] Listagens ordenadas; conversão de unidade na borda
- [ ] `nome` derivado do tempo, com a divergência relatada
- [ ] Placeholders em conjunto fechado, validados na gravação, com `422` informativo
- [ ] Três abas, com a aba ativa sobrevivendo a recarregar
- [ ] Exclusão com o diálogo de confirmação **existente**
- [ ] Prévia do WhatsApp sem literal de cliente e sem cor hardcoded
- [ ] Regras ativas legíveis em `/internal/v1`, sem contrato duplicado
- [ ] `docs/16` com o formato e o conjunto de placeholders
- [ ] Os testes acima
- [ ] CI verde com **número da run**

## No relatório

1. **O que `AutomationConfigInternalController` já expunha** antes desta etapa, e o que você
   estendeu em vez de criar.
2. A regra de derivação Horas/Dias, e se concorda com ela.
3. Como resolveu o `nome` do follow-up.
4. Variável nova no Dokploy: expectativa **nenhuma**. Se precisou, item próprio.
5. SHA final — `SYNAPSE_IMAGE_TAG` é fixado por commit, nunca `latest`.

---

## Fora desta etapa

Tudo que é da aba **Geral · IA** e está na E36: atendentes disponíveis com alternador independente
da presença, rotinas pré-definidas, avaliação por atendimento, recursos de IA. Também fora:
`mensagem_festiva` (tabela existe, sem aba no protótipo — decisão pendente) e qualquer execução ou
agendamento de regra.
