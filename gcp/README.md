# Deploying the landing page to Google Cloud

The [`landing/`](../landing) site is pure static HTML/CSS/JS — no build step. We
host it as a **static website on a public Cloud Storage bucket**. No backend, no
container, minimal moving parts.

## Why Cloud Storage (not Cloud Run)

The site is static, so a bucket is the simplest, cheapest option: no server to
run or scale, and `gsutil rsync` is a one-command deploy. Cloud Run would only
make sense if we needed server-side logic, which we don't. If you later want
HTTPS on a custom domain, keep the bucket and put an **HTTPS load balancer +
Cloud CDN** in front of it (see [below](#optional-https--custom-domain--cdn)).

## Architecture

```
landing/  --gsutil rsync-->  gs://clad-landing-<project>  (public, website hosting)
                                     |
                             https://storage.googleapis.com/<bucket>/index.html
```

One public bucket, configured with `index.html` as the main page and
`404.html` as the error page.

## Prerequisites

- [`gcloud` + `gsutil`](https://cloud.google.com/sdk/docs/install) installed.
- Authenticated: `gcloud auth login` (and `gcloud config set project <id>`).
- A GCP project with **billing enabled**.
- Permission to create buckets and set IAM (e.g. Owner, or Storage Admin).
- Your org must not **enforce** "public access prevention" on the project, since
  a public marketing site needs `allUsers:objectViewer`. If it's enforced,
  you'll need an exception or the load-balancer approach.

## Deploy

```bash
# One-time: copy the example config and fill it in.
cp gcp/config.example.env gcp/config.env
$EDITOR gcp/config.env          # set GCP_PROJECT_ID, GCS_BUCKET_NAME, GCP_REGION

# Each deploy:
source gcp/config.env
./gcp/deploy-landing.sh
```

`gcp/config.env` is gitignored. The script creates the bucket if needed,
enables website hosting, uploads `landing/`, and grants public read. It prints
the public URL when done:

```
https://storage.googleapis.com/<GCS_BUCKET_NAME>/index.html
```

## Update the content

Edit anything under [`landing/`](../landing) and re-run:

```bash
source gcp/config.env
./gcp/deploy-landing.sh
```

`rsync` only uploads changed files. (To also remove files you deleted locally,
add `-d` to the `rsync` line in `deploy-landing.sh` for a clean mirror.)

## Tear down

```bash
source gcp/config.env
./gcp/destroy-landing.sh        # asks you to type the bucket name to confirm
```

## HTTPS + custom domain + CDN

The `*.storage.googleapis.com` URL works but isn't HTTPS on your own domain. To
serve `https://your-domain/` with a Google-managed cert and Cloud CDN, front the
bucket with an external HTTPS load balancer. This is scripted:

```bash
source gcp/config.env            # must also set DOMAIN=your-domain
./gcp/deploy-landing.sh          # bucket must exist & be public first
./gcp/enable-https.sh
```

`enable-https.sh` is idempotent and creates: a global static IP, a **backend
bucket with Cloud CDN**, a URL map, a **Google-managed TLS certificate**, the
HTTPS target proxy + `:443` forwarding rule, and an **HTTP→HTTPS redirect** on
`:80`. It then prints the static IP.

**Then, at your DNS provider**, create an `A` record pointing your domain at that
IP:

```
your-domain.   A   <printed-ip>
```

The managed certificate provisions **only after** DNS resolves to the IP — this
can take up to ~60 minutes. Re-run `./gcp/enable-https.sh` (safe) to re-check, or:

```bash
gcloud compute ssl-certificates describe clad-landing-cert --global \
  --format='value(managed.status)'   # want: ACTIVE
```

Tear the load balancer down (leaves the bucket intact) with:

```bash
./gcp/disable-https.sh
```

> Cost note: an HTTPS load balancer has a small always-on hourly charge (unlike
> the bucket, which is pay-per-use). `disable-https.sh` removes it.

## Optional: deploy from CI

[`.github/workflows/deploy-landing.yml`](../.github/workflows/deploy-landing.yml)
is a ready GitHub Actions workflow that runs the same deploy. It is
**manual-trigger only** (`workflow_dispatch`) and does nothing until you add
auth. Recommended: [Workload Identity Federation](https://github.com/google-github-actions/auth#preferred-direct-workload-identity-federation)
(keyless). Fill in the repo variables/secrets noted at the top of that file.
