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
