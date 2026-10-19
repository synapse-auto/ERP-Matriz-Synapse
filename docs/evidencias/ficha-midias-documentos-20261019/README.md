# Revalidação da separação de Mídias e Documentos

## Estado encontrado

A implementação já foi entregue pela [PR #310](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/310),
mergeada em 17/10/2026, commit de merge `c23ca850746d49c6bd887a3263e8c11b633f1897`.
O último SHA da implementação foi `7efa9fabd9f291af3ae89972c6298d1b1ab1fc67`.
As runs [38739145943](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/38739145943)
e [38739139181](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/38739139181)
terminaram com sucesso nesse SHA. São evidências históricas, não o CI desta revalidação.

A revisão atual parte de `origin/main 9204d5f`: reutiliza a mesma implementação e a fixture
opt-in `MidiasFichaLocal`, sem duplicar endpoint, consulta, componente, tabela ou migration.
Nenhum arquivo de Chat Interno é alterado.

## O que já existe no produto

- Dois accordions independentes na ficha lateral, com ícone, contador e vazio próprios.
- Classificação por `mensagem.tipo`: IMAGEM/VIDEO/AUDIO em Mídias; DOCUMENTO em Documentos,
  inclusive quando nome e MIME são JPG/PNG.
- Totais por agregação autenticada de metadados, independentes das páginas de 20 itens.
- Categorias filtradas antes de LIMIT/OFFSET; ordenação `enviado_em DESC, id DESC`.
- Preview/player e download autenticado existentes; indisponível não recebe ação enganosa.
- Invalidação por mídia recebida/enviada e reconexão; chaves por lead/categoria e cache por sessão.
- Testes negativos de autorização e preservação da compatibilidade do contrato.

## Ambiente de conferência

Aplicação Next/Spring reais, PostgreSQL/Redis/MinIO descartáveis, provedor de saída fake e
schedulers desativados. Dados sintéticos do helper existente: três mídias, 25 documentos
(incluindo JPG, PNG e um documento indisponível), além de um segundo lead vazio.
Não é reprodução HTML, conexão com provedor real ou acesso a dados de clientes.

O complemento `frontend/e2e/fixtures/ficha-midias-categorias-isoladas.sql` adiciona dois
leads sintéticos: apenas um PNG como DOCUMENTO, e apenas 21 mensagens IMAGEM, reutilizando
os bytes de imagem já presentes no storage local. A guarda de banco foi violada de propósito:
o script recusou o banco `postgres`, antes de qualquer INSERT. No `midias_ficha`, aplicou
dois leads/dois atendimentos e 1+21 mensagens. Não é migration nem configuração de produção.

## Evidências atuais na aplicação real

Fonte computada `Inter, "Inter Fallback", Inter, sans-serif`, `document.fonts.ready` concluído,
zoom/DPR 1. Nenhuma alteração de CSS/fonte/layout para produzir as capturas. Não houve
overflow horizontal nas larguras 1920, 1280 e 390 px. As capturas incluem a interface real
do modo de desenvolvimento, não prometem ausência de avisos no console.

| Cenário | Evidência |
|---|---|
| Mídias: imagem, vídeo e áudio | [Desktop](midias-desktop.png), [celular](midias-mobile.png) |
| Dois accordions abertos independentemente | [Desktop](secoes-independentes-desktop.png) |
| JPG/PNG permanecem DOCUMENTO; indisponível sem ação | [Desktop](documentos-desktop.png), [celular](documentos-mobile.png) |
| Documentos: 25 registros em páginas 20+5 | [Página seguinte](documentos-pagina-2.png) |
| Preview de JPG enviado como documento | [Visualizador](jpg-documento-preview.png) |
| Reprodução real de vídeo | [Visualizador](video-preview.png) |
| Troca de lead: contadores 0/0 e vazios distintos | [Desktop](lead-vazio-desktop.png) |
| Mídias vazia, PNG em Documentos | [1280px](midias-vazias-documento-png.png) |
| Documentos vazia, Mídias com total 21 | [Desktop](documentos-vazios-midias-21.png), [celular](documentos-vazios-mobile.png) |
| Mídias: 21 registros em páginas 20+1 | [Página seguinte](midias-pagina-2.png) |
| Envio local atualiza contador 3→4 sem F5, Documentos continua 25 | [Após envio](envio-atualiza-sem-f5.png) |

Asserções no navegador:

- Abertura normal: uma consulta de contagem, nenhuma lista enquanto ambas estão fechadas.
- Cada página adicional fez uma consulta da sua categoria e zero novas contagens; os totais
  permaneceram 25 e 21. Categorias vêm filtradas da API antes da paginação, sem duplicidade.
- Ordenação decrescente de horário verificada nas páginas retornadas. Nenhum item de outro
  lead foi mantido após a troca; vazios também foram conferidos com a outra categoria preenchida.
- WAV e WebM sintéticos reproduziram com avanço de tempo, não apenas renderizaram controles.
- O download do JPG preservou `imagem-como-documento.jpg` e SHA-256
  `968479dc1fef3e5f6cb0a678b7b652733c2ad00057607bf3fd4e703d35c66b6a`, igual ao arquivo no
  MinIO local. Nenhum token ou URL assinada é versionado nesta evidência.
- Enter abriu o preview e Escape fechou; os dois controles de cada accordion mantêm o
  comportamento existente. Nenhum controle ou regra do Chat Interno foi alterado.
- O envio adicional foi somente na fixture, pelo composer real e com canal fake; não foi
  mensagem enviada a cliente/provedor. A atualização do contador ocorreu sem reload.

## Testes e limites

Frontend completo no Node 22: **166 arquivos / 1.351 testes aprovados**. Dirigidos: **113/113**,
incluindo painel, seção de mídias, hooks/paginação/cache, tempo real, visualizador e os quatro
testes da seção de mídias do Chat Interno. Typecheck, lint e build aprovados; quatro avisos
preexistentes de lint fora do escopo. A primeira tentativa dirigida no Windows não iniciou
workers do Vitest (timeout de inicialização); não foi tratada como teste aprovado. Os mesmos
cenários foram executados no Node 22/Linux, como no CI, sem aumentar timeout ou mudar asserções.

O `clean verify` completo Java 21 e o CI do SHA final são informados no relatório da PR
de revalidação. Não confundir os resultados históricos da #310 com esse CI.

Permanecem os avisos de chaves duplicadas na inbox e logo sem configuração já registrados
na implementação anterior; não são apresentados como corrigidos. Nesta sessão longa local
também houve falhas de conexão WebSocket, sem diagnóstico causal concluído. Os cenários de
categorias, paginação, download e envio descritos acima passaram, mas isso não comprova
recebimento/reconexão ponta a ponta do provedor. Não houve homologação em Meta/Uzapi reais,
benchmark da HML, auditoria WCAG completa ou uso de dados de clientes.

**Ação necessária no Dokploy: nenhuma. Sem deploy.**
