# Prompt — Fechar o CI (autônomo)

> Tarefa de manutenção, não bloqueante. Rode quando houver espaço.
> Os dois jobs falham por causas **independentes**. Resolva cada uma.

---

O CI do GitHub Actions está vermelho nos dois jobs no commit `5f8ddf7`. O build local passa 139/139. As causas já estão isoladas.

## Job 1 — Frontend

```
npm error `npm ci` can only install packages when your package.json and
package-lock.json are in sync.
npm error Missing: @emnapi/runtime@1.11.3 from lock file
npm error Missing: @emnapi/core@1.11.3 from lock file
npm error Invalid: lock file's @emnapi/wasi-threads@1.2.1 does not satisfy 1.2.3
```

Os pacotes `@emnapi/*` são dependências opcionais **específicas de plataforma** — entram no Linux e não no Windows. O `package-lock.json` foi gerado em Windows e não tem as entradas que o runner precisa.

Regenere o lock incluindo todas as plataformas:

```
cd frontend
rm -rf node_modules package-lock.json
npm install
npm ci     # tem que passar
npm run lint
npm run build
```

Se o `npm install` do Windows continuar omitindo as entradas de Linux, force a resolução multiplataforma — `npm install --os=linux --cpu=x64` para popular o lock, ou configure `supportedArchitectures` se o gerenciador permitir. O objetivo é um lock que satisfaça `npm ci` tanto no Windows quanto no `ubuntu-latest`.

Commite o lock novo.

## Job 2 — Backend

`Failed to execute goal maven-failsafe-plugin:verify: There are test failures.`

**Preciso que você descubra quais testes falharam no runner** — a saída disponível é só a cauda. O build local passa, então é diferença de ambiente.

Investigue, em ordem:

1. **Conflito de porta.** Na máquina local há um Postgres nativo na 5432 e o compose foi movido para 55432. Se algum teste ou configuração ainda assume porta fixa, no runner (Linux limpo, sem Postgres nativo) o comportamento difere. Testcontainers deveria usar porta aleatória em ambos — confirme que nada escapa disso.

2. **Ordem de execução.** Este projeto já foi mordido três vezes por isso: `BootSemParticaoIT` passando por sorte, `ignore-migration-patterns` dependendo de qual suíte rodava primeiro, e o `@Scheduled` de um contexto interferindo em outro. A ordem no runner raramente é a mesma da máquina local. Rode local com ordem aleatória e veja se reproduz.

3. **Locale e timezone.** Runner em UTC, máquina local em `America/Sao_Paulo`. Qualquer teste comparando data formatada ou faixa de partição por mês quebra na virada.

4. **Recursos.** O runner tem 2 vCPU e 7 GB. Postgres + Redis + aplicação + 139 testes pode estourar timeout que localmente não estoura. Se for isso, o sintoma é timeout, não asserção falha.

**Para ver a causa real:** o workflow já publica `relatorios-de-teste` como artefato. Baixe o artefato do run que falhou e leia os `failsafe-reports` — eles têm o nome do teste e o stack trace completo.

## Depois de verde

Prove que o CI reprova. Quebre de propósito, confirme que pega, reverta:

- Teste falhando ⇒ job vermelho
- Formatação errada ⇒ Spotless reprova
- Violação de arquitetura ⇒ ArchUnit reprova

Este projeto tem sete casos documentados de proteção que existia e não protegia nada. Um CI que nunca reprovou nada seria o oitavo, e o mais caro — é a última linha antes de produção.

## Definição de pronto

- [ ] `npm ci` passa no Linux
- [ ] Causa da falha do backend no runner identificada e corrigida
- [ ] Os dois jobs verdes no GitHub Actions
- [ ] CI provado por falha proposital nos três mecanismos

Commit: `ci: corrige lock multiplataforma e falha de ambiente no runner`.
