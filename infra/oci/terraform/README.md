# IMS on OCI Always Free

This Terraform stack targets the OCI CLI/API `DEFAULT` profile in `us-phoenix-1`. It creates a VCN, internet gateway, public subnet and route, network security group, Ubuntu 24.04 ARM64 instance, reserved public IPv4 address, private Object Storage backup bucket, and optional compartment budget alerts.

The instance is fixed at `VM.Standard.A1.Flex` with exactly 2 OCPUs and 12 GB RAM. Its 100 GB boot volume is within the Always Free aggregate allowance of 200 GB. OCI free-tier eligibility, regional A1 capacity, reserved-IP quota, and any other resources in the tenancy remain account-specific; review the plan and OCI cost estimate before applying.

## Configure and plan

Terraform authenticates through `~/.oci/config` using `config_file_profile = "DEFAULT"`. It reads only the public SSH key path supplied by `ssh_public_key_path` (default `~/.ssh/id_ed25519.pub`). Never provide a private-key path.

Copy the example and set:

- `compartment_ocid`: the target compartment OCID. A tenancy OCID may be used to deploy in the root compartment; no separate tenancy or user OCID Terraform variable is required for compute networking.
- `tenancy_ocid`: tenancy OCID used for IAM dynamic groups and policies that grant the VM instance principal access to the backup bucket.
- `ssh_allowed_cidr`: your public IPv4 address with `/32`.
- `budget_alert_email`: optional email for forecast/actual budget alerts at 80% and 100% of `budget_amount` (default `$1`). When empty, `oci_budget_budget` and `oci_budget_alert_rule` are skipped with `count = 0` because OCI requires alert recipients.

```sh
cp infra/oci/terraform/terraform.tfvars.example infra/oci/terraform/terraform.tfvars
terraform -chdir=infra/oci/terraform init
terraform -chdir=infra/oci/terraform validate
terraform -chdir=infra/oci/terraform plan
```

Do not commit `.tfvars`, state, plan files, or `.terraform/`. The generated database, JWT, Grafana, and bootstrap secrets are included in base64-encoded instance metadata and in Terraform state. Base64 is not encryption. Although secret outputs are marked sensitive, state must be stored with encryption and tightly restricted access.

Cloud-init installs Docker Engine and Compose v2, enables Docker, prepares `/opt/ims`, creates persistent-data directories under `/opt/ims/data`, and writes generated secrets to root-only `/opt/ims/.env`. It also writes `OCI_BACKUP_URI`, `OCI_NAMESPACE`, and `OCI_CLI_AUTH=instance_principal` from Terraform outputs. It intentionally does not assume a source repository URL or launch the development Compose file. Deploy an audited production Compose bundle separately and terminate TLS on ports 80/443.

After apply, use the `public_ip`, `sslip_hostname`, `api_url`, `oci_backup_uri`, `backup_bucket_namespace`, and `ssh_command` outputs. View a generated secret only when necessary, for example:

```sh
terraform -chdir=infra/oci/terraform output -raw grafana_admin_password
terraform -chdir=infra/oci/terraform output -raw oci_backup_uri
```

The backup bucket is private (`NoPublicAccess`). The VM uploads through instance principal when the dynamic group policy is created with a valid `tenancy_ocid`.
