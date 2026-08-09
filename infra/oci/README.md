# OCI Always Free deployment

This path is the deployment boundary for the single-VM OCI option. Account signup, payment-method verification, tenancy creation, and email verification are assumed to be complete. Never put OCIDs, public IPs, API keys, SSH keys, or production `.env` values in this repository.

## Provision

1. Install the OCI CLI and Terraform, then run `oci setup config`. Use the `[DEFAULT]` profile, select `us-phoenix-1`, and keep the generated private key outside the repository.
2. Confirm access with `oci iam availability-domain list --profile DEFAULT`.
3. Review the module inputs, then initialize and apply:

   ```sh
   terraform -chdir=infra/oci/terraform init
   terraform -chdir=infra/oci/terraform fmt -check
   terraform -chdir=infra/oci/terraform validate
   terraform -chdir=infra/oci/terraform plan
   terraform -chdir=infra/oci/terraform apply
   ```

Oracle may return `Out of host capacity` for an Always Free Ampere shape even when quota is available. Retry in another availability domain (AD), then another fault domain; do not repeatedly recreate resources in a tight loop. Capacity changes over time, so wait and retry later if all ADs in `us-phoenix-1` are full. Moving regions requires deliberate state/configuration changes.

Keep the allocation inside current Always Free limits: one eligible Ampere A1 VM allocation, no paid boot-volume excess, no paid load balancer, and only eligible networking/storage resources. Oracle can change eligibility and pricing; verify the cost estimator shows `$0` before applying and set budget alerts. Terraform cannot guarantee a `$0` bill.

Set `tenancy_ocid` in `terraform.tfvars` so Terraform can create the instance-principal dynamic group and Object Storage policy for backups. Set `budget_alert_email` to receive forecast and actual alerts at 80% and 100% of the configured monthly budget (default `$1`). When `budget_alert_email` is empty, budget resources are skipped because OCI alert rules require recipients.

After apply, copy the backup outputs into `/opt/ims/.env` on the VM if the instance was created before these values were wired through cloud-init:

```sh
terraform -chdir=infra/oci/terraform output -raw oci_backup_uri
terraform -chdir=infra/oci/terraform output -raw backup_bucket_namespace
```

## VM and HTTPS

Cloud-init installs Docker Engine with Compose v2 and writes `/opt/ims/.env` with restrictive permissions; deployment never uploads this file. The API's Docker/Maven build runs natively on the ARM VM. Allow inbound TCP 22, 80, and 443 in both the OCI network security rules and the VM firewall. Restrict SSH to your administrator CIDR only; do not open SSH to `0.0.0.0/0`.

The runtime proxy obtains TLS automatically. Terraform outputs the canonical hostname, such as `ims.203.0.113.10.sslip.io`; this is documentation-only, not a real server. Set `OCI_PUBLIC_HOST` to the exact `sslip_hostname` output. Port 80 must remain reachable for ACME HTTP validation. Public monitoring endpoints:

```text
https://ims.<public-ip>.sslip.io/health
https://ims.<public-ip>.sslip.io/status
```

`/health` exposes only the aggregate actuator health check. `/status` exposes the public API status payload and does not proxy Grafana, Prometheus, metrics, or admin routes.

## GitHub deployment

Create protected environments named `oci-production` and `oci-infrastructure`, preferably with required reviewers. Configure:

- Repository secrets: `OCI_VM_HOST`, `OCI_SSH_PRIVATE_KEY`.
- Repository variable: `OCI_SSH_USER` (optional; defaults to `ubuntu`).
- Repository variable: `OCI_PUBLIC_HOST` (recommended; otherwise the workflow uses `<OCI_VM_HOST>.sslip.io`).
- For the manually requested Terraform plan only: secrets `OCI_TENANCY_OCID`, `OCI_USER_OCID`, `OCI_FINGERPRINT`, `OCI_API_PRIVATE_KEY`, `OCI_COMPARTMENT_OCID`, and `OCI_SSH_PUBLIC_KEY`; variables `OCI_SSH_ALLOWED_CIDR` and `OCI_REGION` (which defaults to `us-phoenix-1`).

Prefer a self-hosted GitHub Actions runner on the OCI VM. GitHub-hosted runners cannot reach an SSH endpoint restricted to your administrator CIDR by design. Install the runner on the VM as `ubuntu`:

```sh
export REPO_URL='https://github.com/<owner>/<repo>'
export RUNNER_TOKEN='<short-lived registration token from GitHub>'
bash infra/oci/scripts/install-github-runner.sh
```

Never commit `RUNNER_TOKEN`. After installation, run **Actions → Deploy to OCI VM → Run workflow** and choose `self-hosted` (default). Use `ubuntu-latest` only when you have temporarily opened SSH to GitHub-hosted runner IP ranges, which is discouraged.

The workflow uploads only backend and runtime assets, builds the Maven artifact and API image on the ARM VM, starts Compose, and verifies HTTPS. The first SSH connection records the host key with `ssh-keyscan`; independently compare the VM host fingerprint before the first production run.

## GitHub Pages integration

In **Settings → Pages → Build and deployment → Source**, select **GitHub Actions**. In **Settings → Secrets and variables → Actions → Variables**, set `PUBLIC_API_BASE_URL` to the exact HTTPS API base expected by the frontend, for example `https://<public-ip-with-dashes>.sslip.io/api`. Set the VM's `CORS_ALLOWED_ORIGINS` to the exact Pages origin:

```text
https://<owner>.github.io
```

Do not append the repository path to the CORS origin. The Pages workflow already sets `VITE_APP_BASE` to `/<repository>/`. Re-run **Deploy frontend to GitHub Pages** after changing `PUBLIC_API_BASE_URL`.

## Operations

PostgreSQL backups run in the `postgres-backup` sidecar. Local copies are retained under `${DATA_ROOT}/backups`, and uploads to the private Object Storage bucket occur when `OCI_BACKUP_URI` and `OCI_NAMESPACE` are set. The sidecar authenticates with `OCI_CLI_AUTH=instance_principal` on the VM.

Manual backup and restore examples:

```sh
docker compose --env-file /opt/ims/.env -f compose.oci.yaml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "ims-$(date +%F).dump"
cat ims-YYYY-MM-DD.dump | docker compose --env-file /opt/ims/.env \
  -f compose.oci.yaml exec -T postgres \
  sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Stop API writes and take a fresh backup before a destructive restore. Copy backups to encrypted object storage, retain multiple generations, and never commit them.

Keep Kafka/Redpanda and the Prometheus/Grafana stack disabled initially. The A1 memory budget is limited; enable Kafka only after durable event delivery is required, then add observability after measuring remaining CPU, memory, and disk. External monitoring of the HTTPS `/health` and `/status` endpoints should come first. Do not expose Grafana, Prometheus, or raw metrics publicly.
