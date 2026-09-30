#!/usr/bin/env bash
set -euo pipefail

# Testa a resolucao de alvos e os modos de falha de
# docker/operacoes/diagnostico-automacao-duplicidade.sh com um `docker` FALSO no PATH.
# Nao fala com Docker real, Postgres nem n8n: prova que o script escolhe a instancia certa numa
# VPS com mais de uma stack, que para antes de consultar quando o alvo e ambiguo ou alheio, e que
# toda consulta sai em sessao somente leitura. As consultas SQL em si nao sao validadas aqui.
#
# Uso: bash docker/verificacao/testar-diagnostico-automacao.sh

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="${RAIZ}/docker/operacoes/diagnostico-automacao-duplicidade.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

mkdir -p "${TMP}/bin"
cat > "${TMP}/bin/docker" <<'FALSO'
#!/usr/bin/env bash
# docker falso: containers em $FAKE/containers ("id stack servico nome"), ambiente em
# $FAKE/env/<id>/<VAR>, bancos existentes em $FAKE/bancos. Registra cada psql em $FAKE/psql.log.
set -euo pipefail
cmd="$1"; shift
case "${cmd}" in
  ps)
    stack=""; servico=""; formato=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --filter) case "$2" in
            label=com.docker.stack.namespace=*) stack="${2#label=com.docker.stack.namespace=}" ;;
            label=com.docker.swarm.service.name=*) servico="${2#label=com.docker.swarm.service.name=}" ;;
          esac; shift 2 ;;
        --format) formato="$2"; shift 2 ;;
        *) shift ;;
      esac
    done
    while read -r id st sv nome; do
      [[ -z "${id}" ]] && continue
      if [[ "${formato}" == *stack.namespace* ]]; then echo "${st}"; continue; fi
      [[ -n "${stack}" && "${st}" != "${stack}" ]] && continue
      [[ -n "${servico}" && "${st}_${sv}" != "${servico}" ]] && continue
      echo "${id}"
    done < "${FAKE}/containers" ;;
  inspect)
    id="${*: -1}"; awk -v i="${id}" '$1 == i {print "/" $4}' "${FAKE}/containers" ;;
  logs) : ;;
  exec)
    pgoptions=""
    while [[ "$1" == -* ]]; do
      if [[ "$1" == "-e" ]]; then pgoptions="$2"; shift 2; else shift; fi
    done
    id="$1"; shift
    if [[ "$1" == "printenv" ]]; then
      [[ -f "${FAKE}/env/${id}/$2" ]] || exit 1
      cat "${FAKE}/env/${id}/$2"; echo; exit 0
    fi
    # psql ... -U <usuario> -d <banco> ... -c <sql>
    banco=""; sql=""; usuario=""
    while [[ $# -gt 0 ]]; do
      case "$1" in -d) banco="$2"; shift 2 ;; -U) usuario="$2"; shift 2 ;; -c) sql="$2"; shift 2 ;; *) shift ;; esac
    done
    printf '%s|%s|%s|%s\n' "${id}" "${usuario}" "${banco}" "${pgoptions}" >> "${FAKE}/psql.log"
    if [[ "${sql}" == *pg_database* ]]; then
      alvo="$(sed -E "s/.*datname = '([^']+)'.*/\1/" <<<"${sql}")"
      if grep -qx "${alvo}" "${FAKE}/bancos"; then echo 1; else echo 0; fi
    else
      echo "(0 rows)"
    fi ;;
  *) echo "docker falso: comando nao suportado: ${cmd}" >&2; exit 2 ;;
esac
FALSO
chmod +x "${TMP}/bin/docker"

definir() { mkdir -p "${FAKE}/env/$1"; printf '%s' "$3" > "${FAKE}/env/$1/$2"; }

# Cenario base: duas stacks na mesma VPS. A alvo e "fmna"; "estrutural" nao pode ser tocada.
montar_cenario() {
  export FAKE="${TMP}/cenario"
  rm -rf "${FAKE}"; mkdir -p "${FAKE}/env"
  cat > "${FAKE}/containers" <<'LISTA'
p1 fmna postgres fmna_postgres.1.aaa
b1 fmna backend fmna_backend.1.bbb
n1 fmna n8n fmna_n8n.1.ccc
p2 estrutural postgres estrutural_postgres.1.ddd
b2 estrutural backend estrutural_backend.1.eee
n2 estrutural n8n estrutural_n8n.1.fff
LISTA
  definir p1 POSTGRES_USER fmna_admin
  definir p1 POSTGRES_DB fmna_crm
  definir b1 SYNAPSE_DB_URL "jdbc:postgresql://postgres:5432/fmna_crm?sslmode=disable"
  definir b1 SYNAPSE_DB_USER fmna_admin
  definir b1 SYNAPSE_TOKEN_INTERNO "segredo-que-nao-pode-aparecer"
  definir n1 DB_POSTGRESDB_HOST postgres
  definir n1 DB_POSTGRESDB_DATABASE fmna_n8n
  definir p2 POSTGRES_USER synapse
  definir p2 POSTGRES_DB synapse_crm
  definir b2 SYNAPSE_DB_URL "jdbc:postgresql://postgres:5432/synapse_crm"
  definir b2 SYNAPSE_DB_USER synapse
  definir n2 DB_POSTGRESDB_HOST postgres
  definir n2 DB_POSTGRESDB_DATABASE n8n
  printf 'postgres\nfmna_crm\nfmna_n8n\nsynapse_crm\nn8n\n' > "${FAKE}/bancos"
  : > "${FAKE}/psql.log"
}

APROVADOS=0
REPROVADOS=0
rodar() { PATH="${TMP}/bin:${PATH}" bash "${SCRIPT}" "$@" </dev/null > "${TMP}/saida" 2>&1; }
# Consultas a um banco de dados que nao seja o "postgres" (a checagem de existencia).
consultas_de_dados() { grep -vc '|postgres|' "${FAKE}/psql.log" || true; }
verificar() {  # verificar <nome> <condicao>
  if eval "$2"; then
    APROVADOS=$((APROVADOS + 1)); printf '  ok    %s\n' "$1"
  else
    REPROVADOS=$((REPROVADOS + 1)); printf '  FALHA %s\n' "$1"; sed 's/^/        | /' "${TMP}/saida"
  fi
}
falha_com() {  # falha_com <nome> <trecho esperado na saida> <args...>
  local nome="$1" trecho="$2" codigo=0
  shift 2
  rodar "$@" || codigo=$?
  verificar "${nome}" \
    "[[ ${codigo} -ne 0 ]] && grep -qF -- \"${trecho}\" \"${TMP}/saida\" && [[ \$(consultas_de_dados) -eq 0 ]]"
}

echo "Modos de falha (nenhuma consulta de dados pode ser executada):"
montar_cenario
falha_com "sem --stack: recusa" "informe a instancia com --stack"
verificar "  ...e lista as duas stacks" "grep -q '  estrutural' '${TMP}/saida' && grep -q '  fmna' '${TMP}/saida'"
montar_cenario
falha_com "stack inexistente" "ausente ou ambiguo" --stack outra --sim
montar_cenario
falha_com "--dias invalido" "--dias deve ser inteiro" --stack fmna --dias 0 --sim
montar_cenario
falha_com "nome de stack com caracteres invalidos" "nome de stack invalido" --stack 'fmna;rm' --sim
montar_cenario
echo "p9 fmna postgres fmna_postgres.2.zzz" >> "${FAKE}/containers"
falha_com "dois Postgres na stack: ambiguo" "alvo postgres ausente ou ambiguo" --stack fmna --sim
montar_cenario
sed -i '/^n1 /d' "${FAKE}/containers"
falha_com "stack sem n8n" "alvo n8n ausente ou ambiguo" --stack fmna --sim
montar_cenario
definir b1 SYNAPSE_DB_URL "jdbc:postgresql://estrutural_postgres:5432/fmna_crm"
falha_com "backend apontando para o Postgres de outra stack" "nao o Postgres da stack fmna" --stack fmna --sim
montar_cenario
definir n1 DB_POSTGRESDB_HOST "10.0.0.9"
falha_com "n8n apontando para outro host" "n8n usa o banco em '10.0.0.9'" --stack fmna --sim
montar_cenario
definir b1 SYNAPSE_DB_URL "jdbc:postgresql://postgres:5432/synapse_crm"
falha_com "banco do backend diferente do POSTGRES_DB da stack" "foi criado com 'fmna_crm'" --stack fmna --sim
montar_cenario
definir b1 SYNAPSE_DB_USER outro
falha_com "usuario do backend diferente do da stack" "diferente do usuario do Postgres da stack" --stack fmna --sim
montar_cenario
sed -i '/^fmna_n8n$/d' "${FAKE}/bancos"
falha_com "banco do n8n inexistente" "banco 'fmna_n8n' nao existe" --stack fmna --sim
montar_cenario
rm "${FAKE}/env/n1/DB_POSTGRESDB_DATABASE"
falha_com "variavel de banco ausente no n8n" "DB_POSTGRESDB_DATABASE ausente" --stack fmna --sim
montar_cenario
falha_com "sem terminal e sem --sim: mostra os alvos e para" "sem terminal para confirmar" --stack fmna
verificar "  ...depois de exibir os alvos" "grep -q 'Alvos resolvidos para a stack fmna' '${TMP}/saida'"

echo "Caminho feliz com duas stacks na VPS:"
montar_cenario
codigo=0
rodar --stack fmna --dias 7 --sim || codigo=$?
verificar "termina com sucesso" "[[ ${codigo} -eq 0 ]] && grep -q 'Nada foi alterado' '${TMP}/saida'"
verificar "exibe os tres containers e os dois bancos da stack alvo" \
  "grep -q 'postgres  p1  fmna_postgres.1.aaa' '${TMP}/saida' && grep -q 'backend   b1  fmna_backend' '${TMP}/saida' && grep -q 'n8n       n1  fmna_n8n' '${TMP}/saida' && grep -q 'banco CRM fmna_crm' '${TMP}/saida' && grep -q 'banco n8n fmna_n8n' '${TMP}/saida'"
verificar "so consulta o Postgres da stack alvo, com o usuario dela" "! grep -qv '^p1|fmna_admin|' '${FAKE}/psql.log'"
verificar "consultas de dados: 6 em fmna_crm e 2 em fmna_n8n" \
  "[[ \$(grep -c '|fmna_crm|' '${FAKE}/psql.log') -eq 6 && \$(grep -c '|fmna_n8n|' '${FAKE}/psql.log') -eq 2 ]]"
verificar "toda sessao abre somente leitura" "[[ \$(grep -vc 'default_transaction_read_only=on' '${FAKE}/psql.log') -eq 0 ]]"
verificar "nao imprime token do ambiente" "! grep -q 'segredo-que-nao-pode-aparecer' '${TMP}/saida'"
verificar "nao toca na outra stack" "! grep -q -e '^p2|' -e 'synapse_crm' '${FAKE}/psql.log'"

printf '\n%s aprovado(s), %s reprovado(s)\n' "${APROVADOS}" "${REPROVADOS}"
[[ "${REPROVADOS}" -eq 0 ]]
