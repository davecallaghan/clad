#!/usr/bin/env bash
#
# Tear down the HTTPS load balancer created by enable-https.sh.
# Deletes resources in dependency order. Does NOT touch the GCS bucket
# (use destroy-landing.sh for that).
#
# Usage:
#   export GCP_PROJECT_ID=...
#   ./gcp/disable-https.sh

set -euo pipefail

if [[ -z "${GCP_PROJECT_ID:-}" ]]; then
  echo "ERROR: GCP_PROJECT_ID is not set." >&2
  exit 1
fi

LB="${LB_NAME:-clad-landing}"
G=(gcloud --project "${GCP_PROJECT_ID}" --quiet)

echo "This deletes the HTTPS load balancer '${LB}' (forwarding rules, proxies,"
echo "certificate, URL maps, backend bucket, and static IP)."
read -r -p "Type ${LB} to confirm: " confirm
if [[ "${confirm}" != "${LB}" ]]; then
  echo "Confirmation did not match. Aborting."
  exit 1
fi

# Delete in reverse dependency order; ignore already-gone resources.
del() {
  if "${G[@]}" compute "$@" >/dev/null 2>&1; then
    echo "  deleted: $2"
  else
    echo "  (skip)  $2"
  fi
}

del forwarding-rules delete "${LB}-http-fr" --global
del forwarding-rules delete "${LB}-https-fr" --global
del target-http-proxies delete "${LB}-http-proxy" --global
del target-https-proxies delete "${LB}-https-proxy" --global
del url-maps delete "${LB}-redirect" --global
del url-maps delete "${LB}-urlmap" --global
del ssl-certificates delete "${LB}-cert" --global
del backend-buckets delete "${LB}-backend"
del addresses delete "${LB}-ip" --global

echo "Done. (The GCS bucket was left intact.)"
