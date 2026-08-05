#!/usr/bin/env bash
set -euo pipefail

# Resolve o Postgres da stack Synapse pelo nome da task Swarm. Nao use
# `docker ps -qf name=postgres`: o proprio Dokploy possui outro Postgres.
resolver_postgres_container() {
  local -a encontrados=()
  local id nome

  while IFS=' ' read -r id nome; do
    [[ -z "${id}" ]] && continue
    if [[ "${nome}" =~ _postgres\.1\. ]]; then
      encontrados+=("${id}:${nome}")
    fi
  done < <(docker ps --filter status=running --format '{{.ID}} {{.Names}}')

  if [[ ${#encontrados[@]} -ne 1 ]]; then
    printf 'ERRO: esperado exatamente um container da task Swarm _postgres.1., encontrei %s.\n' "${#encontrados[@]}" >&2
    if [[ ${#encontrados[@]} -gt 0 ]]; then
      printf 'Candidatos:\n' >&2
      printf '  %s\n' "${encontrados[@]}" >&2
    fi
    printf 'Verifique se a stack esta com Postgres 1/1 antes de executar esta operacao.\n' >&2
    return 1
  fi

  printf '%s\n' "${encontrados[0]%%:*}"
}
