#!/usr/bin/env bash
#
# Deploy the Trust by Design landing page to a public Google Cloud Storage
# bucket configured for static website hosting.
#
# Usage:
#   export GCP_PROJECT_ID=...            # your project
#   export GCS_BUCKET_NAME=...           # globally-unique, e.g. clad-landing-$GCP_PROJECT_ID
#   export GCP_REGION=us-central1        # bucket location (used only on creation)
#   ./gcp/deploy-landing.sh
#
# Prereqs: gcloud + gsutil installed and authenticated (`gcloud auth login`),
# billing enabled on the project. See gcp/README.md.

set -euo pipefail

# Run from the repo root regardless of where the script is invoked.
cd "$(dirname "$0")/.."

# ---- 1. Validate required environment variables -----------------------------
missing=0
for var in GCP_PROJECT_ID GCS_BUCKET_NAME GCP_REGION; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set." >&2
    missing=1
  fi
done
if [[ "$missing" -eq 1 ]]; then
  echo "Set the variables above (see gcp/config.example.env) and re-run." >&2
  exit 1
fi

BUCKET="gs://${GCS_BUCKET_NAME}"
echo "Project : ${GCP_PROJECT_ID}"
echo "Bucket  : ${BUCKET}"
echo "Region  : ${GCP_REGION}"
echo

# ---- 2. Create the bucket if it doesn't already exist ------------------------
# `gsutil ls -b` returns non-zero if the bucket is missing; we create it then.
if gsutil ls -b -p "${GCP_PROJECT_ID}" "${BUCKET}" >/dev/null 2>&1; then
  echo "Bucket exists — reusing it."
else
  echo "Creating bucket ${BUCKET} ..."
  # Uniform bucket-level access is the modern default and is required for the
  # allUsers IAM grant below to behave predictably.
  gsutil mb -p "${GCP_PROJECT_ID}" -c STANDARD -l "${GCP_REGION}" -b on "${BUCKET}"
fi

# ---- 3. Configure static website hosting -------------------------------------
# Serves index.html at "/" and 404.html for missing objects.
echo "Configuring website hosting (index.html / 404.html) ..."
gsutil web set -m index.html -e 404.html "${BUCKET}"

# ---- 4. Upload the site ------------------------------------------------------
# rsync mirrors landing/ into the bucket root. -R = recursive.
# NOTE: add -d to also DELETE bucket objects that no longer exist locally
#       (clean mirror). Left off here so a stray manual upload isn't nuked.
echo "Uploading landing/ ..."
gsutil -m rsync -R landing "${BUCKET}"

# Short cache on HTML so content updates show up quickly; long cache on assets.
gsutil -m setmeta -h "Cache-Control:public, max-age=300" "${BUCKET}/index.html" "${BUCKET}/404.html" >/dev/null 2>&1 || true

# ---- 5. Make the objects publicly readable -----------------------------------
# SECURITY: this grants anyone on the internet read access to every object in
# the bucket. That is intended for a public marketing site — do NOT put private
# data in this bucket. If your org enforces "public access prevention", this
# command will fail; you'll need an org-policy exception or a load-balancer + CDN
# fronting a private bucket (see gcp/README.md).
echo "Granting public read (allUsers:objectViewer) ..."
gsutil iam ch allUsers:objectViewer "${BUCKET}"

# ---- 6. Done -----------------------------------------------------------------
echo
echo "Deployed. Public URLs:"
echo "  https://storage.googleapis.com/${GCS_BUCKET_NAME}/index.html"
echo "  (website endpoint) http://${GCS_BUCKET_NAME}.storage.googleapis.com/"
echo
echo "Note: the *.storage.googleapis.com endpoints are HTTP-friendly but do not"
echo "serve HTTPS on a custom domain. For HTTPS + a custom domain, front this"
echo "bucket with an HTTPS load balancer + Cloud CDN (see gcp/README.md)."
