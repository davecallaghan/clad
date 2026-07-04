#!/usr/bin/env bash
#
# Tear down the landing-page bucket created by deploy-landing.sh.
# Deletes ALL objects in the bucket and then the bucket itself.
#
# Usage:
#   export GCS_BUCKET_NAME=...
#   ./gcp/destroy-landing.sh

set -euo pipefail

if [[ -z "${GCS_BUCKET_NAME:-}" ]]; then
  echo "ERROR: GCS_BUCKET_NAME is not set." >&2
  exit 1
fi

BUCKET="gs://${GCS_BUCKET_NAME}"

if ! gsutil ls -b "${BUCKET}" >/dev/null 2>&1; then
  echo "Bucket ${BUCKET} does not exist. Nothing to do."
  exit 0
fi

echo "This will PERMANENTLY DELETE ${BUCKET} and every object in it."
read -r -p "Type the bucket name to confirm: " confirm
if [[ "${confirm}" != "${GCS_BUCKET_NAME}" ]]; then
  echo "Confirmation did not match. Aborting."
  exit 1
fi

echo "Removing bucket and contents ..."
# `rm -r` on the bucket deletes all objects and the bucket in one call.
gsutil -m rm -r "${BUCKET}"
echo "Done."
