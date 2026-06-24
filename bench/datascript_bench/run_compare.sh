#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DATALEVIN_BENCH="${DATALEVIN_BENCH:-/Users/andreas/Projects/datalevin/benchmarks/datascript-bench}"

if [[ ! -d "$DATALEVIN_BENCH" ]]; then
  echo "Datalevin datascript-bench not found: $DATALEVIN_BENCH" >&2
  exit 1
fi

queries=("$@")
if [[ ${#queries[@]} -eq 0 ]]; then
  queries=(q1 q2 q2-switch q3 q4 qpred1 qpred2)
fi

versions=()
if [[ -n "${VEV_COMPARE_BASELINES:-}" ]]; then
  # shellcheck disable=SC2206
  versions=(${VEV_COMPARE_BASELINES})
else
  versions=(datascript datalevin)
fi

if [[ "${VEV_COMPARE_DATOMIC:-0}" == "1" ]]; then
  versions=(datomic "${versions[@]}")
fi

if [[ "${VEV_COMPARE_SKIP_BASELINES:-0}" != "1" ]]; then
  if [[ "${VEV_COMPARE_USE_UPSTREAM_SCRIPT:-0}" == "1" ]]; then
    (
      cd "$DATALEVIN_BENCH"
      ./bench.clj "${versions[@]}" "${queries[@]}"
    )
  else
    printf "version\t"
    printf "%s\t" "${queries[@]}"
    printf "\n"

    for version in "${versions[@]}"; do
      case "$version" in
        datascript)
          (
            cd "$ROOT"
            clojure -Srepro -Sdeps "{:paths [\"$DATALEVIN_BENCH/src-datascript\"] :deps {datascript/datascript {:mvn/version \"1.7.4\"}}}" \
              -M -m datascript-bench.datascript "${queries[@]}"
          )
          ;;
        datalevin)
          (
            cd "$ROOT"
            clojure -Srepro \
              -J--add-opens=java.base/java.nio=ALL-UNNAMED \
              -J--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
              -Sdeps "{:paths [\"$DATALEVIN_BENCH/src\"] :deps {datalevin/datalevin {:mvn/version \"0.10.5\"}}}" \
              -M -m datalevin-bench.datalevin "${queries[@]}"
          )
          ;;
        datomic)
          (
            cd "$ROOT"
            clojure -Srepro -Sdeps "{:paths [\"$DATALEVIN_BENCH/src\" \"$DATALEVIN_BENCH/src-datomic\"] :deps {com.datomic/peer {:mvn/version \"1.0.7277\"}}}" \
              -M -m datalevin-bench.datomic "${queries[@]}"
          )
          ;;
        *)
          echo "unsupported baseline: $version" >&2
          exit 1
          ;;
      esac
    done
  fi
fi

(
  cd "$ROOT"
  bench/datascript_bench/run_vev.sh "${queries[@]}"
)
