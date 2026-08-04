# Prompt E14 — Preparo do deploy (sem credenciais)

> Roda **antes** da E14a. Nada aqui depende de acesso ao VPS, domínio ou Meta.
> Leia `AGENTS.md` por inteiro antes de começar.

---

Três blocos independentes, todos versionáveis. Commit e push ao fim de cada um.

---

## Bloco 1 — Corrigir a verificação do webhook

**Bug bloqueante encontrado na auditoria pré-deploy.**

A Meta usa dois mecanismos completamente diferentes no mesmo endpoint:

| Método | Mecanismo | Segredo |
|---|---|---|
| `GET` | Desafio: responde `hub.challenge` se `hub.verify_token` bater | **Verify token** |
| `POST` | Assinatura HMAC em `X-Hub-Signature-256` | **App Secret** |

Hoje o `GET` reusa o validador de HMAC do `POST`, então o desafio sempre falha e o webhook nunca é configurado — o CRM não recebe mensagem nenhuma.

Corrija separando os dois:

- `GET` valida `hub.verify_token` contra o token de verificação configurado e devolve `hub.challenge` cru, com `200`. Token errado ⇒ `403`
- `POST` continua validando HMAC com o App Secret
- **Comparação em tempo constante** nos dois, não `equals`
- Os dois segredos são de configuração da instância, distintos entre si

**Teste os dois caminhos**, incluindo os negativos: verify token errado dá `403`, assinatura inválida dá `403`, e o desafio correto devolve exatamente o valor de `hub.challenge`.

Só apareceria no dia do deploy. É a oitava vez neste projeto que um teste negativo é o que separa "parece certo" de "está certo".

## Bloco 2 — Dockerfiles de produção

O repositório não tem imagem de produção. O Dokploy em modo Docker Stack exige imagem pré-compilada num registry para aplicar `start-first` — que é o que garante deploy sem downtime, e portanto a regra de precedência.

**Registry decidido: GitHub Container Registry (`ghcr.io`).** Gratuito para repositório privado da organização, sem serviço novo.

**Backend:**
- Multi-stage: build com Maven, runtime com JRE 21 (`eclipse-temurin:21-jre`)
- Usuário não-root
- `HEALTHCHECK` apontando para `/health/liveness` — **nunca** para `/health/critical`
- Camadas do Spring Boot (`layertools`) para cache eficiente

**Frontend:**
- Multi-stage com `output: 'standalone'` do Next.js
- Usuário não-root
- Só o necessário no runtime, sem `node_modules` de build

**No CI:** passo que builda e publica as duas imagens no GHCR quando `main` avança, com tag pelo SHA curto **e** `latest`. A tag por SHA é o que permite rollback para uma versão específica.

## Bloco 3 — Stack do Dokploy

Arquivo de stack versionado no repositório, com os seis serviços: Postgres, Redis, RabbitMQ, backend, frontend, MinIO.

**Três configurações que não são padrão:**

```yaml
deploy:
  update_config:
    order: start-first        # o padrão stop-first é downtime
    failure_action: rollback
  resources:
    limits:
      memory: ...             # Bulkhead no nível de infra
```

E o healthcheck do orquestrador apontando para `/health/liveness`. Se apontar para o `critical`, o container entra em loop de restart durante um incidente de banco — exatamente quando você precisa dele de pé para diagnosticar.

**Nenhum segredo no arquivo.** Só referências a variáveis de ambiente, injetadas pelo Dokploy.

Documente no `README.md` quais variáveis a instância precisa receber, com descrição de cada uma. Esse documento vira o roteiro de provisionamento do segundo filho.

## Definição de pronto

- [ ] `GET` e `POST` do webhook com mecanismos separados e testes negativos dos dois
- [ ] Dockerfiles de backend e frontend, multi-stage, não-root, healthcheck no `liveness`
- [ ] CI publicando imagens no GHCR com tag por SHA e `latest`
- [ ] Stack do Dokploy versionado, com `start-first`, limites de recurso e zero segredo
- [ ] Variáveis de ambiente documentadas no `README.md`
- [ ] Build local das duas imagens funcionando (`docker build`)
- [ ] CI verde
- [ ] Commit e push confirmados

Commits sugeridos: `fix: separa verificacao GET da assinatura POST no webhook`, `build: imagens de producao e publicacao no ghcr`, `chore: stack do dokploy`.

Ao terminar, me diga o tamanho final das duas imagens — imagem grande é deploy lento, e deploy lento no meio do expediente é tempo de exposição da janela.
