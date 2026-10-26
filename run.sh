#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

: "${LLM_API_KEY:=${DEEPSEEK_API_KEY:-}}"
if [[ -z "${LLM_API_KEY}" ]]; then
  echo "Set LLM_API_KEY (or DEEPSEEK_API_KEY) before running. See .env.example." >&2
  exit 2
fi
export LLM_API_KEY

if [[ ! -f target/mini-agent.jar ]]; then
  mvn -q -DskipTests package
fi

USER_NAME="${1:-alice}"
shift || true
exec java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
  -jar target/mini-agent.jar --user "$USER_NAME" "$@"
