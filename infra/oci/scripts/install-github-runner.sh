#!/usr/bin/env bash
# Install a GitHub Actions self-hosted runner on the IMS OCI VM.
# Required environment variables (never commit RUNNER_TOKEN):
#   REPO_URL      - https://github.com/<owner>/<repo>
#   RUNNER_TOKEN  - short-lived registration token from GitHub
# Optional:
#   RUNNER_NAME   - defaults to ims-oci-$(hostname -s)
#   RUNNER_LABELS - defaults to self-hosted,Linux,ARM64,oci,ims
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
  echo "Run this script as the ubuntu user, not root." >&2
  exit 1
fi

: "${REPO_URL:?Set REPO_URL to the repository URL}"
: "${RUNNER_TOKEN:?Set RUNNER_TOKEN to a GitHub registration token}"

RUNNER_NAME="${RUNNER_NAME:-ims-oci-$(hostname -s)}"
RUNNER_LABELS="${RUNNER_LABELS:-self-hosted,Linux,ARM64,oci,ims}"
RUNNER_HOME="${HOME}/actions-runner"
RUNNER_VERSION="${RUNNER_VERSION:-2.321.0}"
ARCH="arm64"

mkdir -p "${RUNNER_HOME}"
cd "${RUNNER_HOME}"

if [[ ! -f ./config.sh ]]; then
  curl -fsSL \
    -o "actions-runner-linux-${ARCH}-${RUNNER_VERSION}.tar.gz" \
    "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-${ARCH}-${RUNNER_VERSION}.tar.gz"
  tar xzf "actions-runner-linux-${ARCH}-${RUNNER_VERSION}.tar.gz"
  rm -f "actions-runner-linux-${ARCH}-${RUNNER_VERSION}.tar.gz"
fi

./config.sh \
  --url "${REPO_URL}" \
  --token "${RUNNER_TOKEN}" \
  --name "${RUNNER_NAME}" \
  --labels "${RUNNER_LABELS}" \
  --unattended \
  --replace

sudo ./svc.sh install "${USER}"
sudo ./svc.sh start

echo "GitHub Actions runner installed for ${REPO_URL} as ${RUNNER_NAME}."
