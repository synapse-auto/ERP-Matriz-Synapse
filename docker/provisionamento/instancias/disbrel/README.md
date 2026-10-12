# Marca da Disbrel: tema, nome e logo (D1)

Aplica a identidade visual da Disbrel **sem deploy, sem variável nova e sem imagem própria**, pelo mecanismo que já
existe desde a E31b: `PUT /api/v1/config/marca/{tema,identidade,logo}`, que grava a linha única `marca_da_instancia`.
A leitura seguinte já usa o valor novo, sem reiniciar. Contexto e decisões em
[`docs/71`](../../../../docs/71-disbrel-requisitos-e-plano-de-implementacao.md).

| Arquivo | O que é |
|---|---|
| `tema-a-sidebar-vinho.json` | Conteúdo branco, sidebar em vinho escuro derivado de `#6B0504` |
| `tema-b-sidebar-branca.json` | Conteúdo branco, sidebar clara, destaques em `#6B0504` |
| `../../aplicar-marca-da-instancia.mjs` | Aplica e reverte (com backup, `--dry-run` e conferência por leitura) |
| `../../marca/contraste-tema.mjs` | Validação estrutural e razão de contraste (WCAG 2.x), usada pelo script e pelos testes |

Não existe protótipo: **quem escolhe o A ou o B é o responsável**, olhando as capturas em
[`docs/evidencias/disbrel-d1/`](../../../../docs/evidencias/disbrel-d1/) (`tema-a-sidebar-vinho/`, `tema-b-sidebar-branca/`
e `padrao/` para comparar).

## O que o responsável precisa fornecer

1. **A escolha:** tema A ou B.
2. **Nome exibido e subtítulo** pós-login (sugestão: `Disbrel` e `CRM · Atendimento`).
3. **A logo em PNG** (só PNG; a API recusa outros tipos). Sem o arquivo, a logo **não** é aplicada e a instância
   continua com a do classpath.
4. **Um access token de gestor** (`GESTOR`, `SUBGESTOR` ou `ADMINISTRADOR`), válido por 15 minutos, em variável de ambiente.
5. **Node 18 ou mais novo** na máquina que roda o script (o do frontend já serve).

## Obter o token sem deixar rastro

A senha não vai para argumento de comando nem para o histórico do shell:

```bash
export SYNAPSE_URL=https://<dominio-do-crm-da-disbrel>
read -rp 'E-mail do gestor: ' EMAIL; read -rsp 'Senha: ' SENHA; echo
export SYNAPSE_TOKEN="$(EMAIL="$EMAIL" SENHA="$SENHA" python3 -c 'import os,json,urllib.request as u; r=u.Request(os.environ["SYNAPSE_URL"]+"/api/v1/auth/login", json.dumps({"email":os.environ["EMAIL"],"senha":os.environ["SENHA"]}).encode(), {"Content-Type":"application/json"}); print(json.load(u.urlopen(r))["accessToken"])')"
unset SENHA EMAIL
```

Não existe `--token`: o script recusa. O token nunca é impresso nem gravado (o backup guarda só tema, identidade e logo).

## Aplicar

Primeiro só validar (não precisa de token nem de rede; mostra a tabela de contraste):

```bash
node docker/provisionamento/aplicar-marca-da-instancia.mjs \
  --url "$SYNAPSE_URL" \
  --tema docker/provisionamento/instancias/disbrel/tema-b-sidebar-branca.json \
  --logo /caminho/seguro/logo-disbrel.png --dry-run
```

Depois, de verdade (troque o tema pelo escolhido; `MARCA_*` são opcionais e, sem elas, vale a sugestão):

```bash
MARCA_NOME='Disbrel' MARCA_SUBTITULO='CRM · Atendimento' \
node docker/provisionamento/aplicar-marca-da-instancia.mjs \
  --url "$SYNAPSE_URL" \
  --tema docker/provisionamento/instancias/disbrel/tema-b-sidebar-branca.json \
  --logo /caminho/seguro/logo-disbrel.png
```

O que o script faz, nesta ordem, e **para no primeiro erro**:

1. Valida o tema localmente: mesmas chaves do `tema.json` padrão (mais os dois tokens opcionais da D1), cores legíveis e os 30 pares de contraste acima do mínimo. Reprovado, **nada é enviado**.
2. Grava o backup do estado atual em `./backup-marca-<data>/` (tema, nome/subtítulo, logo e um manifesto), com permissão só do dono.
3. `PUT` do tema e **leitura de volta** (a API devolve 200 para qualquer JSON; a conferência é por leitura).
4. `PUT` da identidade e leitura de volta.
5. `PUT` da logo, se o arquivo existe e é PNG, e leitura de volta byte a byte.

Guarde a pasta de backup fora do Git (o `.gitignore` já ignora `backup-marca-*/`).

## Reverter

```bash
node docker/provisionamento/aplicar-marca-da-instancia.mjs --url "$SYNAPSE_URL" --reverter ./backup-marca-<data>
```

Restaura tema e identidade do backup e confere por leitura. **A API não tem rota para voltar a "usar o classpath"** (a linha
`NULL`): quem restaura pela API grava uma *cópia* do que estava valendo. Duas consequências:

- **Logo:** se a instância usava a logo do classpath (o caso normal), a reversão **não** refaz o upload e imprime o SQL abaixo. A API não remove logo.
- **Para voltar exatamente a `NULL/NULL`**, rode no banco da instância, com o mesmo usuário do provisionamento (`SYNAPSE_DB_USER`, via o `psql` do container `_postgres.1.`, como o `executar-provisionamento.sh`):

```sql
UPDATE marca_da_instancia
   SET tema = NULL, logo_referencia_storage = NULL, nome_da_marca = NULL, subtitulo = NULL,
       atualizado_por_id = NULL, atualizado_em = NULL
 WHERE id = 1;
```

Só a logo: `UPDATE marca_da_instancia SET logo_referencia_storage = NULL WHERE id = 1;`. O arquivo antigo fica órfão no
storage de mídia (inofensivo; limpeza manual opcional). Validado em banco real: depois do SQL, `GET /api/v1/config/tema` e
`/textos` voltam a servir o classpath, byte a byte.

**Teste a reversão antes de aplicar no stack da Disbrel.** Não existe homologação separada: `erp-matriz-hml` é a produção da Estrutural. Use o
primeiro stack da sequência, **fora do horário de atendimento**, com o comando de reversão já copiado: aplique, confira, reverta, rode o SQL, confira de novo
(ver `docs/72` §3).

## Contraste calculado (WCAG 2.x)

Razão calculada pela fórmula de luminância relativa (não aproximada), com sobreposições translúcidas compostas sobre o fundo.
Mínimos: **4,5** para texto normal e **3** para texto grande e componentes de interface (ícone, barra do item ativo). Os 30
pares são os que a interface usa; a lista está em `marca/contraste-tema.mjs` e os dois temas passam em todos (**30/30**).

| Par | Uso | Mínimo | Tema A | Tema B | Padrão (referência) |
|---|---|---:|---:|---:|---:|
| C1 | Texto forte sobre o cartão | 4,5 | 17,73 | 17,73 | 15,79 |
| C2 | Texto forte sobre o fundo do app | 4,5 | 16,46 | 16,46 | 14,70 |
| C3 | Texto padrão sobre o cartão | 4,5 | 12,99 | 12,99 | 9,58 |
| C4 | Texto padrão sobre o fundo do app | 4,5 | 12,07 | 12,07 | 8,91 |
| C5 | Texto padrão sobre fundo sutil | 4,5 | 11,39 | 11,39 | 8,58 |
| C6 | Texto suave sobre o cartão | 4,5 | 6,37 | 6,37 | 5,49 |
| C7 | Texto suave sobre o fundo do app | 4,5 | 5,91 | 5,91 | 5,11 |
| C8 | Texto suave sobre fundo sutil | 4,5 | 5,58 | 5,58 | 4,92 |
| C9 | Texto fraco (placeholder, auxiliar) sobre o cartão | 3 | 4,28 | 4,28 | 2,85 (abaixo) |
| P1 | Texto do botão primário | 4,5 | 12,74 | 12,74 | 4,53 |
| P2 | Texto do botão primário em hover | 4,5 | 15,25 | 15,25 | 6,00 |
| P3 | Link e destaque primário sobre o cartão | 4,5 | 12,74 | 12,74 | 4,53 |
| P4 | Primário sobre o fundo suave (accent) | 4,5 | 11,30 | 11,30 | 4,02 (abaixo) |
| P5 | Primário sobre o fundo do app | 4,5 | 11,83 | 11,83 | 4,22 (abaixo) |
| M1 | Falha de entrega sobre o balão de saída | 4,5 | 7,48 | 7,48 | 1,82 (abaixo) |
| M2 | Marca de lida (ícone) sobre o balão de saída | 3 | 9,02 | 9,02 | 3,21 |
| E1 | Texto de erro sobre o cartão | 4,5 | 5,60 | 5,60 | 5,44 |
| E2 | Texto de erro sobre o fundo de erro suave | 4,5 | 4,93 | 4,93 | 4,67 |
| S1 | Item em repouso | 4,5 | 13,20 | 10,61 | 9,88 |
| S2 | Item em hover | 4,5 | 14,81 | 16,06 | 11,36 |
| S3 | Item ativo (texto) | 4,5 | 12,09 | 14,80 | 11,00 |
| S4 | Item ativo (ícone) | 3 | 8,29 | 10,63 | 5,39 |
| S5 | Item ativo (barra lateral) | 3 | 8,56 | 12,74 | 5,48 |
| S6 | Título de grupo do menu | 4,5 | 8,36 | 5,13 | 3,46 (abaixo) |
| S7 | Subtítulo da marca | 4,5 | 9,61 | 6,37 | 4,51 |
| S8 | Nome da marca e do usuário | 4,5 | 17,25 | 17,73 | 14,71 |
| S9 | Item de perigo (Sair), repouso | 4,5 | 10,13 | 5,60 | 5,90 |
| S10 | Item de perigo (Sair), hover | 4,5 | 7,67 | 4,96 | 5,28 |
| S11 | Menu do usuário (item em repouso) | 4,5 | 11,20 | 9,28 | 7,90 |
| S12 | Menu do usuário (item ativo) | 4,5 | 10,34 | 13,00 | 8,98 |

O tema padrão fica abaixo do mínimo em 5 pares (C9, P4, P5, M1 e S6). Não foi alterado: é só referência, e o M1 (falha de
entrega sobre o balão azul) é um achado para decisão (docs/71).

`#6B0504` sobre branco: **12,74:1**.

### Erro e perigo não se confundem com a ação principal

O vinho e o `corErro` padrão (`#C0392B`) são parentes. Nos temas da Disbrel o erro e o perigo passam a ser um vermelho-alaranjado
(`corErro` `#BF360C`, fundo `#FFEDE5`; perigo na sidebar `#FFB59A` no tema A e `#BF360C` no B), e o fundo suave da cor primária deixa de ser
rosado (`#F6F0EF`, quase neutro), porque o rosa do primário era indistinguível do rosa do erro (distância de cor 1,9 contra 5,7).
Distância CIE76 entre primário e erro: 33,9 (era 27,9 com o `#C0392B`). Sucesso e atenção não mudaram. O tema padrão não foi tocado.

## O que mudou no frontend para o tema B (e por quê)

A sidebar era escura por construção: texto `text-white` e divisórias `bg-white/N` fixos. Sem mudar isso, o tema B mostra o nome da marca, o item
ativo e o nome do usuário em branco sobre branco (capturas em `docs/evidencias/disbrel-d1/antes-da-correcao-tema-b/`). Entraram dois tokens
**opcionais**, com padrão que reproduz o comportamento antigo (por isso o tema padrão não mudou):

| Token | Para quê | Padrão quando ausente |
|---|---|---|
| `sidebarTextoForte` | nome da marca, item ativo e usuário na sidebar; divisórias derivadas dele | `#FFFFFF` |
| `mensagemFalhaTexto` | texto de falha de entrega sobre o balão de saída (cor primária) | `sidebarItemTextoPerigo` |
