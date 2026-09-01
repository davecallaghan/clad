#!/usr/bin/env bash
#
# One-time setup: keyless GitHub Actions -> Google Cloud auth via Workload
# Identity Federation (WIF). Creates a WIF pool + GitHub OIDC provider (locked to
# THIS repo), a dedicated deploy service account, and the impersonation binding.
#
# No service-account keys are created or downloaded — that's the whole point.
#
# Usage:
#   export GCP_PROJECT_ID=...
#   export GITHUB_REPO=owner/repo          # e.g. davecallaghan/clad
#   ./gcp/setup-wif.sh
#
# Prereqs: gcloud authenticated as a project Owner (or with IAM + Storage admin),
# billing enabled. Run once; safe to re-run (idempotent).

set -euo pipefail

for var in GCP_PROJECT_ID GITHUB_REPO; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set (GITHUB_REPO looks like owner/repo)." >&2
    exit 1
  fi
done

PROJECT="${GCP_PROJECT_ID}"
POOL="${WIF_POOL:-github-pool}"
PROVIDER="${WIF_PROVIDER:-github-provider}"
SA_NAME="${WIF_SA:-clad-landing-deployer}"
SA_EMAIL="${SA_NAME}@${PROJECT}.iam.gserviceaccount.com"
G=(gcloud --project "${PROJECT}" --quiet)

PROJECT_NUMBER="$("${G[@]}" projects describe "${PROJECT}" --format='value(projectNumber)')"

echo "Project        : ${PROJECT} (#${PROJECT_NUMBER})"
echo "GitHub repo    : ${GITHUB_REPO}"
echo "WIF pool       : ${POOL}"
echo "Service account: ${SA_EMAIL}"
echo

# 0. Enable the APIs WIF + deploy need.
echo "Enabling APIs ..."
"${G[@]}" services enable \
  iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  storage.googleapis.com

# 1. Workload Identity Pool.
if ! "${G[@]}" iam workload-identity-pools describe "${POOL}" --location=global >/dev/null 2>&1; then
  echo "Creating WIF pool ${POOL} ..."
  "${G[@]}" iam workload-identity-pools create "${POOL}" --location=global \
    --display-name="GitHub Actions"
else
  echo "WIF pool exists."
fi

# 2. GitHub OIDC provider — the attribute-condition LOCKS federation to this one
#    repo, so no other repository can mint tokens that impersonate the SA.
if ! "${G[@]}" iam workload-identity-pools providers describe "${PROVIDER}" \
      --location=global --workload-identity-pool="${POOL}" >/dev/null 2>&1; then
  echo "Creating OIDC provider ${PROVIDER} (locked to ${GITHUB_REPO}) ..."
  "${G[@]}" iam workload-identity-pools providers create-oidc "${PROVIDER}" \
    --location=global --workload-identity-pool="${POOL}" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
    --attribute-condition="assertion.repository == '${GITHUB_REPO}'"
else
  echo "OIDC provider exists."
fi

# 3. Dedicated deploy service account.
if ! "${G[@]}" iam service-accounts describe "${SA_EMAIL}" >/dev/null 2>&1; then
  echo "Creating service account ${SA_NAME} ..."
  "${G[@]}" iam service-accounts create "${SA_NAME}" \
    --display-name="Clad landing deployer (GitHub Actions)"
else
  echo "Service account exists."
fi

# 4. Grant the SA permission to deploy the site.
#    storage.admin covers: create bucket, set website config, set bucket IAM
#    (the allUsers public-read grant), and upload objects. Scoped to one SA.
echo "Granting roles/storage.admin to the deploy SA ..."
"${G[@]}" projects add-iam-policy-binding "${PROJECT}" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/storage.admin" --condition=None >/dev/null

# 5. Let GitHub Actions for THIS repo impersonate the SA.
echo "Binding ${GITHUB_REPO} -> impersonate ${SA_NAME} ..."
"${G[@]}" iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/attribute.repository/${GITHUB_REPO}" \
  >/dev/null

PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/providers/${PROVIDER}"

echo
echo "WIF is set up. Add these GitHub Actions repository VARIABLES"
echo "(Settings > Secrets and variables > Actions > Variables), or run the gh commands:"
echo
echo "  gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER -b '${PROVIDER_RESOURCE}'"
echo "  gh variable set GCP_SERVICE_ACCOUNT            -b '${SA_EMAIL}'"
echo "  gh variable set GCP_PROJECT_ID                 -b '${PROJECT}'"
echo "  gh variable set GCS_BUCKET_NAME                -b '<your-bucket>'"
echo "  gh variable set GCP_REGION                     -b '<your-region>'"
echo
echo "Then pushes to master that touch landing/ will deploy automatically"
echo "(or run the 'Deploy landing page' workflow manually)."
