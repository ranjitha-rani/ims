variable "compartment_ocid" {
  description = "OCID of the compartment in which to create all resources. The tenancy OCID is valid when deploying to the root compartment."
  type        = string

  validation {
    condition     = can(regex("^ocid1\\.(compartment|tenancy)\\.", var.compartment_ocid))
    error_message = "compartment_ocid must be a compartment or tenancy OCID."
  }
}

variable "region" {
  description = "OCI region. This stack intentionally targets Phoenix."
  type        = string
  default     = "us-phoenix-1"

  validation {
    condition     = var.region == "us-phoenix-1"
    error_message = "region must remain us-phoenix-1 for this stack."
  }
}

variable "availability_domain" {
  description = "Optional availability-domain name. Leave null to use the first AD returned for the compartment."
  type        = string
  default     = null
}

variable "ssh_allowed_cidr" {
  description = "IPv4 CIDR allowed to reach SSH. Set this to your public IP with /32; do not leave SSH open globally."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.ssh_allowed_cidr))
    error_message = "ssh_allowed_cidr must be a valid IPv4 CIDR."
  }
}

variable "ssh_public_key_path" {
  description = "Path to the SSH public key only. Private keys must never be supplied."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "cors_allowed_origin" {
  description = "Exact GitHub Pages browser origin allowed to call the API."
  type        = string
  default     = "https://ranjitha-rani.github.io"

  validation {
    condition     = can(regex("^https://[^/]+$", var.cors_allowed_origin))
    error_message = "cors_allowed_origin must be a single HTTPS origin without a path or trailing slash."
  }
}

variable "bootstrap_admin_email" {
  description = "Optional email for the first administrator account. Leave empty to disable bootstrap."
  type        = string
  default     = ""
}

variable "name_prefix" {
  description = "Prefix for OCI resource display names."
  type        = string
  default     = "ims"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.name_prefix))
    error_message = "name_prefix must be 2-21 lowercase letters, numbers, or hyphens, beginning with a letter."
  }
}

variable "vcn_cidr" {
  description = "CIDR for the VCN."
  type        = string
  default     = "10.42.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR for the public subnet."
  type        = string
  default     = "10.42.1.0/24"
}

variable "boot_volume_size_gbs" {
  description = "Boot volume size. Default 100 GB leaves 100 GB of the Always Free 200 GB block-volume allowance available."
  type        = number
  default     = 100

  validation {
    condition     = var.boot_volume_size_gbs >= 50 && var.boot_volume_size_gbs <= 200
    error_message = "boot_volume_size_gbs must be between 50 and 200 GB."
  }
}

variable "freeform_tags" {
  description = "Optional free-form tags applied to resources that support them."
  type        = map(string)
  default = {
    managed-by = "terraform"
    project    = "ims"
  }
}

variable "tenancy_ocid" {
  description = "Tenancy OCID. Required for IAM dynamic groups and policies that grant the VM instance principal access to the backup bucket."
  type        = string
  default     = ""

  validation {
    condition     = var.tenancy_ocid == "" || can(regex("^ocid1\\.tenancy\\.", var.tenancy_ocid))
    error_message = "tenancy_ocid must be empty or a tenancy OCID."
  }
}

variable "enable_budget_alert" {
  description = "Create a compartment budget and alert rules when budget_alert_email is set."
  type        = bool
  default     = true
}

variable "budget_amount" {
  description = "Monthly budget amount in USD used for unexpected-spend alerts."
  type        = number
  default     = 1

  validation {
    condition     = var.budget_amount > 0
    error_message = "budget_amount must be greater than zero."
  }
}

variable "budget_alert_email" {
  description = "Email address for budget alert notifications. Budget resources are skipped when empty because OCI requires recipients."
  type        = string
  default     = ""

  validation {
    condition     = var.budget_alert_email == "" || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_alert_email))
    error_message = "budget_alert_email must be empty or a valid email address."
  }
}
