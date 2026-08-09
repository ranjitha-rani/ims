output "public_ip" {
  description = "Reserved public IPv4 address assigned to the IMS instance."
  value       = oci_core_public_ip.reserved.ip_address
}

output "sslip_hostname" {
  description = "DNS hostname backed by sslip.io for the reserved public IP."
  value       = "ims.${oci_core_public_ip.reserved.ip_address}.sslip.io"
}

output "api_url" {
  description = "HTTPS API base URL served by Caddy after the runtime stack is deployed."
  value       = "https://ims.${oci_core_public_ip.reserved.ip_address}.sslip.io/api"
}

output "ssh_command" {
  description = "Command for connecting with the default Ubuntu account."
  value       = "ssh ubuntu@${oci_core_public_ip.reserved.ip_address}"
}

output "database_password" {
  description = "Generated PostgreSQL password written to /opt/ims/.env by cloud-init."
  value       = random_password.database.result
  sensitive   = true
}

output "jwt_secret" {
  description = "Generated JWT signing secret written to /opt/ims/.env by cloud-init."
  value       = random_password.jwt.result
  sensitive   = true
}

output "grafana_admin_password" {
  description = "Generated Grafana administrator password written to /opt/ims/.env by cloud-init."
  value       = random_password.grafana.result
  sensitive   = true
}

output "bootstrap_password" {
  description = "Generated application bootstrap password written to /opt/ims/.env by cloud-init."
  value       = random_password.bootstrap.result
  sensitive   = true
}

output "demo_customer_password" {
  description = "Generated shared password for seeded demo customer accounts."
  value       = random_password.demo_customer.result
  sensitive   = true
}

output "backup_bucket_namespace" {
  description = "Object Storage namespace for the private IMS backup bucket."
  value       = data.oci_objectstorage_namespace.ims.namespace
}

output "backup_bucket_name" {
  description = "Private Object Storage bucket name for PostgreSQL backups."
  value       = oci_objectstorage_bucket.backups.name
}

output "oci_backup_uri" {
  description = "Suggested OCI_BACKUP_URI value for the postgres-backup sidecar."
  value       = "oci://${oci_objectstorage_bucket.backups.name}/postgres"
}

output "budget_alert_enabled" {
  description = "Whether a budget and alert rules were created for the compartment."
  value       = local.create_budget_alert
}
