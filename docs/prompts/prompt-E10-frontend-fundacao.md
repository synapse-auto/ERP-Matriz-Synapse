# Prompt E10 — Frontend: fundação e shell

> Pré-requisito: E07 commitada. **Sessão limpa.**
> Referência visual: `design/TOKENS.md` e `design/componentes/*.html`.

---

**Etapa E10 — Design tokens, shell da aplicação e login.**

## Regra que vale para todo o frontend, desta etapa em diante

**Nada de dados mockados.** Toda tela dentro do escopo da primeira entrega consome a API real desde o primeiro commit. Sem array de exemplo, sem `faker`, sem "por enquanto retorna fixo".

Se um endpoint ainda não existe, a tela mostra estado de carregamento ou vazio de verdade — não inventa conteúdo. Mock em frontend tem o mesmo problema das proteções silenciosas que já morderam este projeto três vezes: a tela parece pronta, ninguém percebe que não está ligada, e o defeito aparece na homologação com a subgestora.

## 1. Design tokens

Leia `design/TOKENS.md` — a paleta, tipografia, raios e sombras já foram extraídos do protótipo.

- Gerar `tema.json` na instância, servido por `GET /api/v1/config/tema` (E07)
- CSS variables aplicadas na raiz; shadcn/ui já é construído sobre CSS variables — sobrescreva os tokens, **não edite componentes**
- `textos.json` servido por `GET /api/v1/config/textos`; nenhuma string literal em componente

**Teste da Base PAI:** trocar `tema.json` muda toda a aparência sem tocar em nenhum `.tsx`. Se algum componente precisar mudar, a tradução falhou. Prove isso com um segundo tema de exemplo.

Dois pontos do `TOKENS.md` que exigem decisão sua:

- **`--cor-erro` não existe no protótipo.** A E05 criou o estado `FALHOU` e ele precisa ser visível. Proponha um valor coerente com a paleta.
- **Consolide os raios.** O protótipo tem 8, 9, 10, 11, 12, 13, 14, 16, 18, 20px — a variação de 1px é ruído de mockup, não intenção. Cinco tokens bastam.

## 2. Shell da aplicação

Estrutura do protótipo (`design/componentes/Sidebar.html`):

- Sidebar escura (`#0F2438`) com marca, grupos **MENU** e **GESTÃO**, badge de não lidas
- Rodapé com status de presença, trocar conta e sair
- Referência de UX do `claude.ai` para a estrutura lateral e o local de Configurações/Perfil/Ajuda

**Menu montado a partir de `GET /api/v1/config/features`.** Item cuja flag está `false` não é renderizado — não é escondido por `if`. As abas fora da primeira entrega (Banco de Arquivos, Campanhas, Relatórios) simplesmente não aparecem.

A tela **Admin** do protótipo não estava nos requisitos. Não construa nesta etapa — provavelmente é o mini front-end da Base PAI do roadmap interno, que é fase 2.

## 3. Login

- Consome `/api/v1/auth/login` real
- Access token em memória, refresh em cookie `httpOnly` — **não** coloque JWT em `localStorage`
- Refresh automático e transparente antes de expirar
- 401 leva ao login sem loop de redirecionamento

## 4. Camada de dados

- TanStack Query para estado de servidor, Zustand para estado de UI (filtros abertos, aba ativa)
- Cliente HTTP central tratando refresh, erros RFC 7807 e o cabeçalho de autorização em um lugar só
- **Erro visível ao usuário.** Um `catch` silencioso no frontend é a versão de UI do problema que este projeto já teve três vezes

## 5. Server Components onde couber

Fluidez é requisito explícito. Dados iniciais por Server Component; Client Component só onde há interatividade real. Reduz o JS enviado, que é o principal fator de fluidez percebida.

## 6. Testes

- Trocar `tema.json` muda a aparência sem alterar componente
- Flag desligada some do menu
- Login, refresh automático e logout
- 401 não gera loop
- Nenhuma string literal de UI nos componentes (lint ou teste)
- Nenhum dado mockado (grep por arrays de exemplo no CI, se der)

## Definição de pronto

- [ ] `tema.json` e `textos.json` dirigindo aparência e textos
- [ ] Shell com sidebar, menu por feature flag, rodapé de perfil
- [ ] Login funcionando contra a API real
- [ ] Zero cor, texto ou dado literal em componente
- [ ] `npm run build` e CI verdes

Commit: `feat: fundação do frontend com design tokens e shell`.

Ao terminar, me mande um screenshot do shell com o tema aplicado e me diga quais tokens do `TOKENS.md` você precisou acrescentar — o protótipo não cobre todos os estados que a aplicação real tem.
