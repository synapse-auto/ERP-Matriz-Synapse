# Prompt E109 — TTL da URL assinada de mídia

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/ttl-url-assinada-de-midia`) e PR.
> **Sem merge, sem deploy.** Etapa mínima: config e, no máximo, uma linha de código.
> Verificação: `./mvnw -pl crm-atendimento -am verify`. Se o Bloco 2 acabar tocando `crm-equipe`,
> suba para `./mvnw verify` no reator. **Sem migration, sem frontend.**

---

## O problema

A mídia que aparece dentro das bolhas da conversa é assinada **no momento em que a lista de mensagens
carrega**, e a assinatura vale **5 minutos** (`synapse.midia.expiracao-leitura`, default `5m`).

Um atendente deixa a conversa aberta a manhã inteira. Passados os 5 minutos, qualquer arquivo que
precise carregar falha: rolar para cima e ver um PDF antigo, clicar em baixar um anexo, uma imagem
que ainda não tinha sido renderizada. O objeto continua no MinIO — só a assinatura morreu.

Isto **não** é regressão da E101. Já era assim; o 401 do painel de mídias era mais barulhento e
escondia. Com o painel corrigido, esta passou a ser a falha que sobra.

## Bloco 1 — A mudança

`expiracao-leitura` passa de **5 minutos para 1 hora**.

Mude nos **dois** lugares, senão instância sem a variável de ambiente continua com 5 minutos:

- `backend/crm-app/src/main/resources/application.yml` — `${MIDIA_S3_EXPIRACAO_LEITURA:5m}`
- `MidiaProperties` — o default em código, hoje `Duration.ofMinutes(5)`

Confirme lendo que são só esses dois. Se houver um terceiro, relate.

**Escreva o porquê no `application.yml`**, no estilo do comentário que já existe ali sobre
`access-key`. O que precisa estar registrado:

- a URL dá acesso a **um objeto só**, não é adivinhável, e vai para o navegador de um usuário que
  já foi autenticado e que a RLS já autorizou a ver aquele lead;
- 5 minutos não comprava segurança real, e custava tela quebrada no uso normal;
- 1 hora é o mesmo valor que o chat interno já usa há tempos, sem incidente.

Sem esse comentário, alguém baixa o valor de novo daqui a seis meses "por segurança" e o bug volta
sem ninguém entender.

## Bloco 2 — A divergência que originou isso

`ChatInternoController` (linha ~193) chama `armazenamento.urlAssinada(..., Duration.ofHours(1))`,
com o valor **cravado no código**, enquanto a mídia do lead usa a propriedade configurável. Dois
valores para a mesma decisão, um deles invisível para quem configura a instância.

Faça o chat interno usar a **mesma propriedade**, para existir um valor só.

**Mas confira antes:** `MidiaProperties` vive em `crm-atendimento` e o `ChatInternoController` em
`crm-equipe`. Se isso criar dependência entre módulos que o ArchUnit recusa, **não force** — deixe o
`ofHours(1)` como está, escreva um comentário de uma linha ali dizendo que o valor espelha
`synapse.midia.expiracao-leitura`, e **relate** que a unificação não coube. Um valor duplicado e
documentado é melhor que uma dependência arquitetural criada às pressas.

## Bloco 3 — O que não muda

- Nenhuma outra propriedade de mídia.
- `AnexoMidiaIT` sobrescreve o TTL para 500ms dentro da suíte, para testar expiração. Isso tem que
  continuar funcionando — se o teste passar a depender do default, você acoplou o teste à config.
- Nada no frontend. A emissão sob demanda no visualizador é a E102 e é independente desta.
- Nenhum endpoint, contrato ou política.

## Bloco 4 — Testes

- O default em código é 1 hora.
- A sobrescrita por variável de ambiente continua valendo (o `AnexoMidiaIT` com 500ms é a prova).
- Se você unificou o chat interno: um teste de que ele usa a propriedade, não um literal.

## Verificação

```
./mvnw -pl crm-atendimento -am verify      # na raiz de backend/
```

## Relatório

1. Os lugares que você mudou, e a confirmação de que não há um terceiro default escondido.
2. O texto do comentário que ficou no `application.yml`.
3. Se a unificação do Bloco 2 coube ou não, e o que o ArchUnit disse.
4. Confirmação de que o `AnexoMidiaIT` continua controlando o TTL pela própria suíte.

## Depois do deploy

Não é preciso limpar nada: as URLs já emitidas continuam com a validade antiga, e as novas nascem
com uma hora. Basta o atendente recarregar a conversa uma vez.
