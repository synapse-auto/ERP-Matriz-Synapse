# Prompt E228 — Disbrel D1: tema, logo e nome da instância (usando o que já existe)

Número E228 provisório: confirmar o próximo livre em `docs/prompts/`. Produção. A Disbrel (`disbrel-dbl`) já está no ar. Não faça deploy, não rode nada contra produção, não crie variável de ambiente nem mexa em `dokploy-stack.yml`. Um PR pequeno, a partir da `origin/main` atual (ou empilhado sobre o #294, o que for mais simples; diga qual).

> Confirmação feita na execução: `E228` não aparecia em nenhum arquivo do repositório. O PR foi empilhado sobre o #294 (mesma branch, `claude/nifty-mayer-ao637a`), porque a correção do `docs/71` pedida no item 1 só existe nele.

## Correção de premissa (verificada por mim no código do PR #294)

O `docs/71` diz que "falta override de marca por instância" e propõe uma variável nova (`SYNAPSE_INSTANCIA_DIR`). Isso já existe, desde o E31b:

* Leitura pública: `GET /api/v1/config/tema`, `/textos`, `/logo`.
* Escrita (papéis `GESTOR`, `SUBGESTOR`, `ADMINISTRADOR`): `PUT /api/v1/config/marca/tema` (JSON do tema), `PUT /api/v1/config/marca/logo` (multipart, só PNG), `PUT /api/v1/config/marca/identidade` (nome e subtítulo pós-login). Ver `ConfigInstanciaController`, `AtualizarTemaDaInstanciaUseCase`, `AtualizarLogoDaInstanciaUseCase`, `MarcaDaInstanciaRepositorioJdbc`.
* Vale na leitura seguinte, sem reiniciar. Linha `NULL/NULL` = usa o classpath (`MarcaDaInstanciaIT`).
* Risco real: `AtualizarTemaDaInstanciaUseCase` não valida o schema. Um tema incompleto ou errado, aplicado numa instância em produção, estraga o visual do cliente inteiro.

Portanto: D-1 está decidida: usar este mecanismo. Não criar diretório de instância, variável nova nem imagem por filho. A D1 vira (a) tema e marca como dados, (b) uma forma segura de aplicar e reverter, (c) limpar o que estiver fixo no frontend.

## Leitura obrigatória

`AGENTS.md`, `docs/13`, `docs/71` (do PR #294), `docker/provisionamento/README.md`, `ConfigInstanciaController` e os use cases citados, `MarcaDaInstanciaIT`, `backend/crm-app/src/main/resources/tema.json` e `textos.json`, `frontend/src/app/globals.css` (como os tokens viram CSS) e `identidade-synapse.css` (login).

## O que fazer

1. Corrigir o `docs/71`: fechar D-1 com o mecanismo existente; reescrever a D1 conforme este prompt; remover `SYNAPSE_INSTANCIA_DIR`; reclassificar a etapa de entrada no ar como já realizada (a Disbrel está em produção) e deixar só o que resta de homologação; atualizar a entrada do `docs/13`.
2. Dois temas candidatos da Disbrel (arquivos versionados em `docker/provisionamento/instancias/disbrel/`, por exemplo `tema-a-sidebar-vinho.json` e `tema-b-sidebar-branca.json`). Não temos protótipo, então o responsável escolhe olhando as capturas (item 6). Cor primária `#6b0504`.
   * A: conteúdo branco, sidebar em vinho escuro derivado de `#6b0504`.
   * B: conteúdo branco e sidebar clara.
   * Ambos com o mesmo conjunto de chaves do `tema.json` padrão (todas as `sidebar*`, `marcaIcone*`, `sombraPrimaria`, overlays etc.), com derivados coerentes de hover, suave, borda e texto sobre primário.
   * Contraste por script, com número: texto normal ≥ 4,5:1, texto grande e componentes de interface ≥ 3:1, para os pares que a interface usa (texto sobre fundo, texto sobre primário, itens da sidebar em repouso, hover e ativo). Entregar a tabela com valores calculados, não aproximados.
   * Erro e perigo não podem se confundir com a ação principal. Vinho e `corErro` (`#C0392B`) são parentes. Avaliar nas telas e, se preciso, ajustar `corErro`, `corErroSuave` e `sidebarItemTextoPerigo` apenas no tema da Disbrel, sem mudar sucesso, atenção nem o tema padrão.
3. Auditar o que está fixo no frontend: cores hexadecimais em `.tsx`/`.ts`/`.css` fora dos tokens, textos "Estrutural", "Synapse" ou outro nome de marca em telas pós-login, `<title>`, favicon, metadados. Relatar a lista (arquivo:linha). Corrigir só o que for pequeno e seguro; o resto vira lista para o responsável decidir. O login continua com a identidade Synapse (D-11); não mexer.
4. Script para aplicar e reverter com segurança (em `docker/provisionamento/`), sem nenhum segredo no repositório:
   * Recebe URL da instância e token de um usuário gestor por variável de ambiente; nunca em argumento, arquivo versionado ou log.
   * Antes de aplicar: valida o JSON localmente (é JSON; mesmo conjunto de chaves do `tema.json` padrão; cores no formato esperado) e salva o tema atual (`GET /config/tema`) em arquivo local para reversão.
   * Aplica `tema`, `identidade` (nome e subtítulo vindos de variáveis, com `Disbrel` e `CRM · Atendimento` como sugestão) e `logo` (caminho do PNG por parâmetro; se o PNG não existir, não aplica a logo, não inventa uma).
   * Modo de reversão: restaura o backup. Verificar também se existe jeito de voltar a `NULL/NULL` (classpath) pela API; se não houver, documentar o SQL de rollback exato.
   * Modo `--dry-run` que só valida e mostra o que enviaria.
5. Testes: (a) o conjunto de chaves de cada tema da Disbrel é igual ao do `tema.json` padrão; (b) `MarcaDaInstanciaIT` continua passando sem alteração (comportamento padrão intacto); (c) se você adicionar validação de schema no `PUT /marca/tema` para impedir tema incompleto, ela precisa ser retrocompatível com o que a tela de gestão já envia; se isso exigir mudança de comportamento, não faça no mesmo PR: relate como proposta.
6. Evidência visual: capturas das telas principais (painel de atendimentos, chat aberto, uma tela de gestão, um modal de perigo/confirmação, um estado de erro) com o tema A, o B e o padrão, interceptando `GET /api/v1/config/tema` (Playwright ou o que o projeto já usa). Se não conseguir gerar, diga que ficou sem prova; não descreva o visual sem ter visto.

## Não-objetivos

* Nada de variável nova, mudança em `dokploy-stack.yml`, imagem por filho ou fork.
* Não tocar em setores, distribuição, horário, funil, Agenda (D2 em diante), em RLS, nem em IA/n8n.
* Não rodar o script contra produção; não pedir nem registrar token. Não fazer deploy.
* Não mudar o tema padrão (Estrutural e Fêmina ficam exatamente como estão).

## Relatório final exigido

* Diff real; testes locais e CI (separados); se o job `imagens` passou.
* Tabela de contraste com os valores calculados; lista de cores/textos fixos encontrados.
* Capturas A, B e padrão, ou a declaração de que ficaram sem prova.
* Comando exato para eu aplicar e para reverter, com o que o responsável precisa fornecer.
* O que ficou sem prova.

## Decisões que continuam do responsável

* Tema A ou B (olhando as capturas); nome exibido e subtítulo; o PNG da logo.
* Quando aplicar na produção (fora do horário de atendimento, com a reversão testada antes).
