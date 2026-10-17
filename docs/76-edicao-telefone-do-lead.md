# Hotfix — edição de telefone do lead

## Evidência e limite do diagnóstico

O print apresenta mensagem genérica com um telefone de formato aceito pelo domínio. Ele não contém status HTTP, ID técnico ou versão implantada; isoladamente, não confirma a causa da ocorrência real.

Reprodução local pelo endpoint real, com PostgreSQL/Testcontainers e dados sintéticos:

- número livre: HTTP 200, telefone persistido;
- número ocupado por um lead não visível ao atendente: SQLSTATE 23505 / `ux_lead_telefone`, HTTP 500 antes da correção;
- a interface apresentava a mesma mensagem genérica para conflito, falta de permissão e outras falhas.

O teste negativo foi executado antes da implementação e falhou com `expected 409, actual 500`. A confirmação da ocorrência do print ainda depende de status/código sanitizado do ambiente afetado. Não houve acesso ou alteração em produção.

## Correção e contrato

`LeadRepositorioJpa.salvar` mantém a Specification/RLS e faz `saveAndFlush` para traduzir exclusivamente SQLSTATE 23505 no índice `ux_lead_telefone` em `TelefoneIndisponivelException`. Não faz consulta prévia ao outro cadastro, nem usa contexto privilegiado para encontrá-lo. O índice arbitra inclusive requisições concorrentes. Outras violações continuam propagadas, cobertas por testes negativos.

As rotas existentes mantêm payload e resposta de sucesso:

- PUT `/api/v1/leads/{id}/telefone`;
- PUT `/api/v1/leads/{id}/agenda/telefone`;
- PUT `/api/v1/leads/{id}` quando inclui telefone.

Conflito passa a HTTP 409 / RFC 7807, `codigo: TELEFONE_INDISPONIVEL`, sem outro ID, nome, responsável, número ou detalhes SQL. Telefone inválido mantém HTTP 400 e ganha código aditivo `TELEFONE_INVALIDO`. Nenhum cadastro é fundido, reatribuído ou sobrescrito para contornar duplicidade. O endereço anterior do provedor só é invalidado quando uma alteração válida é confirmada; rollback preserva ambos os cadastros no conflito.

O modal traduz apenas códigos/status conhecidos para o catálogo, nunca exibe `detail` técnico arbitrário. Preserva o valor digitado e permite nova tentativa após erro. Uma trava local evita segundo envio por Enter/clique antes da atualização do estado pendente. Não há retry automático de mutation, consulta ao provedor, migration ou dependência nova.

Textos novos têm defaults no schema para catálogos antigos. Fontes, cores, layout, regras de telefone, permissões, n8n e outbox não foram alterados.

## Verificação

- `LeadFichaIT`: número livre, conflito nas três rotas, isolamento, propriedade e preservação de dados/endereço do provedor. A rodada inicial corrigida passou 18/18; a parametrização posterior das três rotas é executada no ciclo completo.
- `LeadRepositorioJpaTest`: 3/3, incluindo outra constraint e outro SQLSTATE que não podem virar conflito de telefone.
- Frontend dirigido: 72/72; suíte completa Node 22/Linux: 161 arquivos, 1.304 testes. Typecheck/lint aprovados; quatro avisos anteriores fora dos arquivos alterados.
- Catálogo antigo sem os três textos novos mantém mensagens válidas.
- Playwright CLI headed, aplicação real e PostgreSQL/Redis descartáveis: 409 com mensagem sanitizada em desktop 1440×1000 e celular 390×844; modal móvel x=16, largura=358, totalmente contido na viewport; nova tentativa por Enter retorna 200, fecha o modal e mantém o telefone após reload. Fonte computada `Inter, "Inter Fallback", Inter, sans-serif`, aguardando `document.fonts.ready`.
- Quatro screenshots sintéticos em `docs/evidencias/edicao-telefone/`; não são reprodução HTML nem dados de clientes.

O resultado do `clean verify` Java 21, build e CI do último SHA é registrado na PR/relatório final; não inferir CI verde a partir dos testes dirigidos.

## Custos e fora do escopo

Sem consulta adicional por telefone, carregamento integral, polling ou chamada externa. O flush antecipa a escrita já existente da ficha dentro da mesma transação; não altera o caminho JDBC de envio/recebimento de mensagens.

Não se remove o índice único nem se fundem contatos. Não foram enviados WhatsApps reais, reprocessadas filas ou alterados dados de produção. Confirmar a causa exata do print requer evidência técnica sanitizada do ambiente afetado.

Achado para revisão separada: o UPDATE JPA da ficha inclui colunas além das editáveis, apesar de `aplicar` não atribuir os contadores. Os testes desta correção preservam dados no fluxo normal; não comprovam ausência de sobrescrita sob edição e recebimento simultâneos. O hotfix não reescreve esse mecanismo nem afirma ter corrigido essa condição concorrente preexistente.

Skills: Playwright carregada antes da validação; clean-code, architecture-patterns, api-design-principles e supabaseboaspraticas não estavam disponíveis no catálogo da sessão. Regras canônicas aplicadas diretamente.

Dokploy: nenhuma variável ou procedimento novo; publicação normal de backend e frontend após aprovação. Sem merge/deploy automático.
