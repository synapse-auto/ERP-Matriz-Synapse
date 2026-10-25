# Recuperação do CI e integração sequencial das PRs

## Autorização e limites

O solicitante autorizou corrigir todas as PRs vermelhas e fazer merge uma por vez.
Inventário inicial: #320, #321, #322, #323 e #324. Nenhum deploy, configuração
de produção, mensagem real ou habilitação da Z-API integra esta execução.
Merge de código experimental não representa homologação do provedor.

## Causa comum comprovada

As cinco PRs falhavam em `npm audit --omit=dev`, antes do lint/testes/build:
Zod 3.25.76, [GHSA-mrhx-c7xc-m3w5](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5).
A autorização atual permite a migração necessária para a versão corrigida 4.6.7.
Não foi removida a auditoria, usado `audit fix --force`, reduzida severidade ou
mantida a implementação antiga via importação `zod/v3`.

Zod 4 altera `.default()`: o valor passa a ser uma saída pronta, sem preencher
defaults internos. Os objetos/arrays foram migrados para `.prefault()`, preservando
a semântica de entrada da versão anterior, conforme o
[guia oficial de migração](https://zod.dev/v4/changelog). O gerador do catálogo de
Gestão acompanha o schema gerado. Strings, fontes, cores e textos não mudam.
Testes adicionais cobrem catálogos antigos, parses independentes e configuração
inválida sem vazamento do conteúdo no log.

## Ordem e dependências

1. #320: corrigir Zod, validar o último SHA, integrar por merge commit.
2. #321: incorporar main corrigida; validar e integrar a proteção da outbox.
3. #322: preservar a ancestralidade da #321, trocar a base para main após o merge.
4. #323: repetir após #322, sem ativar envio de mídia.
5. #324: repetir após #323 e renumerar a migration ainda não integrada de
   correlação, pois a V111 da #320 ocupa esse número na main.

Cada integração exige Backend, Frontend e Stack aprovados no SHA enviado. Imagens
GHCR são ignoradas nas PRs por regra do workflow e só publicadas no push de main.
O histórico remoto/run e os SHAs efetivos serão registrados nas respectivas PRs.
Não usar o resultado de um SHA anterior como aprovação do próximo.

A primeira run desta correção (#320, 40169534280) expôs um defeito adicional:
o npm local removeu entradas opcionais @emnapi do lockfile. `npm ci` no Linux
recusou a instalação por falta de @emnapi/core e @emnapi/runtime 1.11.3.
Restauradas as quatro entradas opcionais preexistentes, inclusive as empacotadas
do Tailwind, sem mudar versões nem ignorar a verificação de consistência do npm.
Essa run reprovada não conta como aprovação da correção seguinte.

## Ambiente e limites das evidências locais

- Java 21; Testcontainers exige Docker Desktop disponível.
- Node local 24.12.0 apresentou encerramento nativo de processos filhos no teste
  de provisionamento da marca. A execução foi repetida com o runtime disponível
  24.22.1, sem alterar a instalação global. O CI usa Node 22.
- A asserção de permissões POSIX do backup agora só verifica bits owner/group/others
  nas plataformas que os representam. Todas as demais asserções continuam no
  Windows. O CI Linux mantém a verificação estrita; não se afirma validação de ACL
  Windows. Essa limitação está documentada pelo
  [Node.js](https://nodejs.org/api/fs.html#file-modes).
- A auditoria de produção corrigida não equivale à ausência de alertas na árvore
  de desenvolvimento. Não ampliar atualizações para dependências sem relação.

## Operação

Nenhuma variável nova decorre desta correção de CI. A V111 de acesso a clientes
exige troca coordenada do backend em todas as réplicas em um deploy futuro:
backend antigo com schema novo falha fechado por não publicar o novo contexto RLS.
Não aplicar migration manualmente nem misturar versões incompatíveis.
Z-API continua OFF, sem autenticação inventada para callbacks, sem entrada pública
liberada, sem mudança no n8n e sem reprocessamento de mensagens em produção.

## Integrações confirmadas e validação combinada

| PR | SHA validado/enviado | Run PR / push (SUCCESS) | Merge em main |
|---|---|---|---|
| #320 | b68e73c | 40169686971 / 40169684615 | ba381ab |
| #321 | ad45d65 | 40170618025 / 40170615484 | 5ceccf6 |
| #322 | f6c8e74 | 40171435859 / 40171431231 | ecec625 |
| #323 | bb5ec82 | 40172391861 / 40172388439 | 832847d |

Todas foram integradas por merge commit, sem force push ou supressão de check.
As PRs filhas só passaram a ter main como base depois da integração da predecessora.
Resultados finais da #324 e da publicação de imagens da main têm como fonte os
[checks/comentários da PR](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/324).
Runs intermediárias de main podem ser canceladas pelo próximo push, conforme
concurrency do workflow; isso não equivale a publicação de imagem confirmada.

O ciclo local completo Java 21 da #320 terminou BUILD SUCCESS em 19m35s:
390 relatórios, 2.464 casos, zero falhas/erros e três ignorados por ausência de FFmpeg.
Os ignorados preexistentes são dois casos de `FfmpegConversorDeAudioTest` e
`MinioArmazenamentoDeMidiaIT.gravaRecuperaEValidaComFfprobeAmesmaNotaDeVozDoComposer`.
Frontend: 168 arquivos / 1.400 testes, typecheck/lint/build/audit aprovados; quatro
avisos preexistentes de lint. Dirigidos adicionais após incorporar main: #322 54/54;
#323 mídia 32/32 e saída integrada 9/9, sem ignorados.

Antes da validação combinada da #324, a checagem local reprovou com duas V111.
Somente a migration de correlação ainda não integrada foi renomeada para V112;
a V111 de acesso a clientes permaneceu intacta. O Git identifica rename 100%,
com SHA-256 do SQL preservado:
`879986c1b928e68cd2a172c0c742b7619637926d51dca527ef61556249aacb73`.
Após o rename não há versões duplicadas. O teste de inventário continua com
igualdade exata; não foi reduzido para acomodar a combinação.

Após combinar #320–#323 e renumerar a migration, dirigidos da #324 passaram:
43 unidades (tradutor 30, identidade 7, caso de uso 3, seleção OFF 3) e 71
integrações (schema 21, entrada local 41, saída 9), zero falhas/erros/ignorados,
Java 21, BUILD SUCCESS em 2m54s. O frontend é idêntico ao SHA b68e73c já validado
localmente; cada CI executa novamente toda a suíte. O ciclo local completo final
e CI da #324 são comprovados separadamente na PR, não inferidos desses dirigidos.
