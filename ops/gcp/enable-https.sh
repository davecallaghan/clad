#!/usr/bin/env bash
#
# Put an external HTTPS load balancer + Cloud CDN + Google-managed TLS cert in
# front of the landing bucket, so the site is served over HTTPS on your own
# domain (with HTTP -> HTTPS redirect).
#
# Run ./gcp/deploy-landing.sh FIRST (the bucket must exist and be public).
#
# Usage:
#   export GCP_PROJECT_ID=...
#   export GCS_BUCKET_NAME=...
#   export DOMAIN=trustbydesign.example.com
#   ./gcp/enable-https.sh
#
# After it runs: point your domain's DNS A record at the printed IP. The managed
# certificate only becomes ACTIVE once DNS resolves to that IP — allow up to
# ~60 minutes. Re-run this script any time to re-check cert status (idempotent).

set -euo pipefail
cd "$(dirname "$0")/.."

for var in GCP_PROJECT_ID GCS_BUCKET_NAME DOMAIN; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set (DOMAIN e.g. trustbydesign.example.com)." >&2
    exit 1
  fi
done

# Base name for all load-balancer resources; override with LB_NAME if you like.
LB="${LB_NAME:-clad-landing}"
PROJECT="${GCP_PROJECT_ID}"
G=(gcloud --project "${PROJECT}" --quiet)

# Create a resource only if it doesn't already exist (makes re-runs safe).
# usage: ensure <describe-args...> -- <create-args...>
ensure() {
  local describe=() create=() seen=0
  for a in "$@"; do
    if [[ "$a" == "--" ]]; then seen=1; continue; fi
    if [[ $seen -eq 0 ]]; then describe+=("$a"); else create+=("$a"); fi
  done
  if "${G[@]}" compute "${describe[@]}" >/dev/null 2>&1; then
    echo "  exists: ${create[2]:-${describe[2]}}"
  else
    echo "  create: ${create[2]:-${describe[2]}}"
    "${G[@]}" compute "${create[@]}"
  fi
}

echo "Provisioning HTTPS load balancer '${LB}' for ${DOMAIN} -> gs://${GCS_BUCKET_NAME}"

# 1. Global static IP.
ensure addresses describe "${LB}-ip" --global \
    -- addresses create "${LB}-ip" --global --ip-version=IPV4

# 2. Backend bucket with Cloud CDN enabled.
ensure backend-buckets describe "${LB}-backend" \
    -- backend-buckets create "${LB}-backend" \
         --gcs-bucket-name="${GCS_BUCKET_NAME}" --enable-cdn

# 3. URL map (serve everything from the backend bucket).
ensure url-maps describe "${LB}-urlmap" --global \
    -- url-maps create "${LB}-urlmap" --default-backend-bucket="${LB}-backend" --global

# 4. Google-managed TLS certificate for the domain.
ensure ssl-certificates describe "${LB}-cert" --global \
    -- ssl-certificates create "${LB}-cert" --domains="${DOMAIN}" --global

# 5. Target HTTPS proxy tying the cert to the URL map.
ensure target-https-proxies describe "${LB}-https-proxy" --global \
    -- target-https-proxies create "${LB}-https-proxy" \
         --url-map="${LB}-urlmap" --ssl-certificates="${LB}-cert" --global

# 6. Forwarding rule: :443 -> HTTPS proxy.
ensure forwarding-rules describe "${LB}-https-fr" --global \
    -- forwarding-rules create "${LB}-https-fr" --global \
         --address="${LB}-ip" --target-https-proxy="${LB}-https-proxy" --ports=443

# 7. HTTP -> HTTPS redirect (so http:// visitors are bounced to https://).
if ! "${G[@]}" compute url-maps describe "${LB}-redirect" --global >/dev/null 2>&1; then
  echo "  create: ${LB}-redirect (http->https)"
  tmp="$(mktemp -t clad-redirect-XXXX).yaml"
  cat > "$tmp" <<YAML
kind: compute#urlMap
name: ${LB}-redirect
defaultUrlRedirect:
  redirectResponseCode: MOVED_PERMANENTLY_DEFAULT
  httpsRedirect: true
YAML
  "${G[@]}" compute url-maps import "${LB}-redirect" --global --source="$tmp"
  rm -f "$tmp"
else
  echo "  exists: ${LB}-redirect"
fi
ensure target-http-proxies describe "${LB}-http-proxy" --global \
    -- target-http-proxies create "${LB}-http-proxy" --url-map="${LB}-redirect" --global
ensure forwarding-rules describe "${LB}-http-fr" --global \
    -- forwarding-rules create "${LB}-http-fr" --global \
         --address="${LB}-ip" --target-http-proxy="${LB}-http-proxy" --ports=80

# ---- Report -----------------------------------------------------------------
IP="$("${G[@]}" compute addresses describe "${LB}-ip" --global --format='value(address)')"
CERT_STATUS="$("${G[@]}" compute ssl-certificates describe "${LB}-cert" --global \
  --format='value(managed.status)' 2>/dev/null || echo UNKNOWN)"

echo
echo "Load balancer ready. Static IP: ${IP}"
echo
echo "NEXT: create this DNS record at your domain registrar / DNS provider:"
echo "    ${DOMAIN}.   A   ${IP}"
echo
echo "Managed certificate status: ${CERT_STATUS}"
echo "  (Becomes ACTIVE only after DNS points at the IP above — up to ~60 min."
echo "   Re-run this script to re-check, or:"
echo "   gcloud compute ssl-certificates describe ${LB}-cert --global --format='value(managed.status)')"
echo
echo "Once ACTIVE: https://${DOMAIN}/"
