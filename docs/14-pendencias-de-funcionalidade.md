# 14. Pendências de funcionalidade

Tudo que está no protótipo aprovado pelo cliente e **não** está no sistema. Ordenado por custo/benefício, não por ordem de descoberta.

Distinto do `docs/09`, que define o recorte da primeira entrega. Este documento existe para que nenhuma dessas pendências dependa de alguém lembrar.

Atualizado em 12/08/2026, depois da E21b.

---

## Prioridade 2 — mudança pequena de schema

| Item | O que falta | Nota |
|---|---|---|
| **Mensagens rápidas compartilhadas ("Geral")** | `atendente_id` passa a aceitar nulo, com o significado de "da equipe" | hoje toda mensagem rápida é pessoal. Numa operação com vários atendentes, resposta pronta compartilhada é das coisas mais úteis que existem — e o protótipo mostra o grupo |
| **Modal de tag com a paleta do protótipo** | 7 tons e 22 ícones do modelo, no lugar dos 7/14 atuais | decisão de produto, não de fidelidade: confirme o conjunto com o cliente antes |

## Prioridade 3 — módulo ou endpoint novo

| Item | Tamanho | Nota |
|---|---|---|
| **Regras de automação** — follow-up, fidelização, mensagem festiva, resumo por IA | grande | tabelas existem, zero caso de uso. Hoje ninguém consegue configurar nada disso. O produto se chama "CRM integrado com IA"; é a lacuna mais visível quando alguém for olhar essa parte |
| **Horários de trabalho** | 1,5 a 2 dias | módulo inteiro: `horario_trabalho` e `rotina_disponibilidade` só existem no schema. Consequência atual: **disponibilidade do atendente é manual** — ninguém entra em expediente sozinho |
| **Kanban na Agenda** | médio | precisa de endpoint de agrupamento por etapa com contagem; sem ele vira N+1 |
| **Importar/exportar CSV de leads** | médio | não existe motor CSV em nenhum dos dois lados |
| **Avaliação por atendimento na Automação** | médio | sem endpoint |
| **Troca de credencial do canal pela gestão** | médio | o cadastro de canal existe, mas não há API para consultar ou rotacionar a credencial com validação e histórico; os contratos antes descritos no `docs/04` eram apenas planejados |

## Fora da primeira entrega por decisão (`docs/09`)

Dashboard nas abas Operacional, Comercial e IA & Automação; Relatórios; Campanhas; Banco de Arquivos; Chat interno. Não são pendências — são escopo de fase 2, com o cliente ciente.

---

## Duas decisões de produto em aberto

**Escala do CSAT.** O banco guarda 0–5; o protótipo aprovado mostra "9,4 / 10" e a tela de Equipe diz "avaliações na escala 0-10". A pergunta que decide: **o que a Automação vai perguntar ao cliente final?** Se for 1 a 5 estrelas, o protótipo é que está errado. Se for 0 a 10, o banco precisa mudar — e é muito melhor mudar agora, com o sistema vazio, do que depois com avaliação real dentro.

**Conjunto de cores e ícones de tag.** O protótipo tem 7 tons e 22 ícones; o construído tem 7 e 14. Trocar é decisão de produto.

---

## O que não é pendência de funcionalidade, mas bloqueia a entrega

Registrado aqui porque some de vista com facilidade:

- **Smoke RLS nunca executado** no ambiente real — atendente pode estar vendo lead de colega e ninguém saberia
- **Seed de demonstração nunca executado** — as telas estão vazias
- **Nenhuma mensagem real** jamais entrou ou saiu do ambiente hospedado
- **Backup nunca restaurado** de verdade
- **Watchdog externo da E22 ainda não provisionado/testado** — o endpoint e o runbook existem; falta configurar o Kuma fora do provedor do CRM e executar o teste destrutivo de `docs/15`
- **Subdomínios reais** — o `sslip.io` compartilha cota do Let's Encrypt com o mundo; na renovação, o certificado pode não sair, e aí a Meta para de entregar webhook
- **PITR** antes do go-live de produção (`docs/10` §1.1b)
