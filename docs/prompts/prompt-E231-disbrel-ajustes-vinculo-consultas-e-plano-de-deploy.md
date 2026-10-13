Prompt E231 — Disbrel: ajustes na #300 (vínculo), consultas novas com tudo desligado e correções no plano de deploy

> Texto recebido em 12/10/2026, salvo como chegou. A mensagem terminou cortada em "o que ficou sem prov" (§4); o relatório entregue cobre
> também o que ficou sem prova.

Número E231 provisório: confirmar o próximo livre em `docs/prompts/`. Executor: Claude Code (Sonnet 5.5, esforço alto). Produção. Sem deploy, sem aplicar script, sem secret, sem mesclar nem marcar PR como pronta. Vale o protocolo anti-alucinação do E229 §0 (toda afirmação rotulada `verificado (arquivo:linha)`/`inferido`/`não verificado`; nome só se achado por `git grep`; CI verde só com número de run). Não edite teste existente para fazê-lo passar.

## 1. Decisões do responsável (12/10/2026) sobre as divergências do E230

* (a) Aplicar. Com `setores` ligado, o atendente vinculado só vale se pertencer a um setor que recebe da triagem. Usuário sem setor, com `setores` ligado, não é elegível ao vínculo direto (cai no rodízio do setor). Com `setores` desligado, nada muda.
* (b) Aplicar. O vínculo também é gravado quando o dono do atendimento envia mensagem sem ser participante, e quando um atendente abre contato novo pela Agenda e fica como responsável (`IniciarNovoContato` ou equivalente: ache o caso de uso real por busca). Abrir a ficha sem assumir o atendimento não grava vínculo.
* (c) Não fazer. Sem backfill. O vínculo vale a partir da V104. Registre no `docs/71` que um backfill opt-in a partir de `atendimento.atendente_id` é possível e só deve ser considerado se o cliente pedir.

## 2. O que fazer

1. Implementar (a) e (b) na #300, com testes novos, incluindo negativos: usuário sem setor com `setores` ligado; vinculado em setor que não recebe da triagem; dono que envia mensagem sem ser participante; abrir ficha sem assumir; nenhum caso concede visibilidade de lead a outro atendente. Com tudo desligado, os ITs existentes passam sem edição (prove com `git diff --stat` filtrando-os).
2. "2 buscas novas que passam a rodar mesmo com tudo desligado" (do seu relatório E230 §7). Isso contradiz a premissa "com tudo desligado nada muda". Identifique: (i) quais são as duas consultas (texto SQL, arquivo:linha); (ii) em que caminho rodam (por mensagem recebida, por transferência, por abertura de ficha?) e com que frequência estimada; (iii) o que elas fazem quando as chaves estão desligadas. Correção esperada: com `setores` e `vinculado` desligados, essas consultas não executam (guarda no código antes de ir ao banco, lendo a configuração já em cache se existir; se a leitura da chave custa uma ida ao banco, diga). Se for inevitável rodar, entregue o EXPLAIN real no padrão `docker/operacoes/e224-*` (como `synapse_app`, `BEGIN/ROLLBACK`, 3 repetições, menor tempo) e o comando exato para eu rodar na VPS. Sem número, não declare custo desprezível. A VPS está saturada (CPU compartilhada, steal ~30%): nenhuma consulta nova no caminho de mensagem sem necessidade.
3. Corrigir o `docs/72`: (i) não existe "homologação" separada: o stack `erp-matriz-hml` é a produção da Estrutural. Troque "smoke na homologação" por "smoke no primeiro stack da sequência, fora do horário de atendimento, com o comando de reversão copiado"; (ii) a EXPLAIN do rodízio passa a ser obrigatória antes de mesclar a #300 se o item 2 mostrar que alguma consulta nova roda com tudo desligado e não puder ser guardada; caso contrário, obrigatória antes de ligar as chaves; (iii) confirme que a imagem anterior funciona contra o esquema V102–V104 (rollback por SHA): as novas tabelas/colunas são aditivas? as `CHECK` da V102 em `atendimento` poderiam rejeitar escrita do código antigo? cite o teste ou diga "não verificado".
4. V102 (#295): descreva o que a migration faz (as duas `CHECK` em `atendimento`), se usa `NOT VALID`/`VALIDATE`, e o que acontece se existir linha em produção que viole a constraint (a migration falha no boot? o deploy trava?). Sugira uma consulta de pré-checagem somente leitura que eu rode nos três bancos antes do deploy, para provar que nenhuma linha viola.
5. Atualizar os corpos da #300 e da #294 com os novos heads e runs, e o `docs/71` com as decisões (a)(b)(c).

## 3. Não-objetivos

Nada de funcionalidade nova além de (a) e (b); sem D3d; sem RLS; sem mudança em papéis, ficha da Agenda ou rótulos; sem backfill; sem heartbeat/backoff/STOMP/pool/`statement_timeout`/agendadores; sem mesclar.

## 4. Relatório final exigido

Diff real; testes locais e CI (separados) com números de run; resposta rotulada a cada item da §2; o texto das duas consultas do item 2 e o resultado da guarda (ou o EXPLAIN); a consulta de pré-checagem da V102; o que ficou sem prov[a] (texto cortado na origem).
