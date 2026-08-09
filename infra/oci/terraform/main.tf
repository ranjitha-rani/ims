data "oci_identity_availability_domains" "available" {
  compartment_id = var.compartment_ocid
}

locals {
  availability_domain = coalesce(var.availability_domain, data.oci_identity_availability_domains.available.availability_domains[0].name)
}

data "oci_core_images" "ubuntu_arm64" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
  state                    = "AVAILABLE"
}

resource "random_password" "database" {
  length  = 32
  special = false
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "grafana" {
  length  = 32
  special = false
}

resource "random_password" "bootstrap" {
  length  = 32
  special = false
}

resource "random_password" "demo_customer" {
  length  = 24
  special = false
}

resource "oci_core_vcn" "ims" {
  compartment_id = var.compartment_ocid
  cidr_blocks    = [var.vcn_cidr]
  display_name   = "${var.name_prefix}-vcn"
  dns_label      = "imsvcn"
  freeform_tags  = var.freeform_tags
}

resource "oci_core_internet_gateway" "ims" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.ims.id
  display_name   = "${var.name_prefix}-internet-gateway"
  enabled        = true
  freeform_tags  = var.freeform_tags
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.ims.id
  display_name   = "${var.name_prefix}-public-routes"
  freeform_tags  = var.freeform_tags

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.ims.id
  }
}

resource "oci_core_subnet" "public" {
  compartment_id             = var.compartment_ocid
  vcn_id                     = oci_core_vcn.ims.id
  cidr_block                 = var.public_subnet_cidr
  display_name               = "${var.name_prefix}-public-subnet"
  dns_label                  = "public"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_default_security_list.restricted.id]
  prohibit_public_ip_on_vnic = false
  freeform_tags              = var.freeform_tags
}

resource "oci_core_default_security_list" "restricted" {
  manage_default_resource_id = oci_core_vcn.ims.default_security_list_id

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }
}

resource "oci_core_network_security_group" "instance" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.ims.id
  display_name   = "${var.name_prefix}-instance-nsg"
  freeform_tags  = var.freeform_tags
}

resource "oci_core_network_security_group_security_rule" "ingress_ssh" {
  network_security_group_id = oci_core_network_security_group.instance.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = var.ssh_allowed_cidr
  source_type               = "CIDR_BLOCK"
  description               = "SSH from the configured administrator CIDR"

  tcp_options {
    destination_port_range {
      min = 22
      max = 22
    }
  }
}

resource "oci_core_network_security_group_security_rule" "ingress_web" {
  for_each = toset(["80", "443"])

  network_security_group_id = oci_core_network_security_group.instance.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  description               = "Public web traffic on TCP ${each.value}"

  tcp_options {
    destination_port_range {
      min = tonumber(each.value)
      max = tonumber(each.value)
    }
  }
}

resource "oci_core_network_security_group_security_rule" "egress_all" {
  network_security_group_id = oci_core_network_security_group.instance.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
  description               = "Required outbound access for updates and container images"
}

resource "oci_core_instance" "ims" {
  availability_domain = local.availability_domain
  compartment_id      = var.compartment_ocid
  display_name        = "${var.name_prefix}-arm"
  shape               = "VM.Standard.A1.Flex"
  freeform_tags       = var.freeform_tags

  shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }

  create_vnic_details {
    assign_public_ip = false
    display_name     = "${var.name_prefix}-primary-vnic"
    hostname_label   = "ims"
    nsg_ids          = [oci_core_network_security_group.instance.id]
    subnet_id        = oci_core_subnet.public.id
  }

  source_details {
    source_id               = data.oci_core_images.ubuntu_arm64.images[0].id
    source_type             = "image"
    boot_volume_size_in_gbs = var.boot_volume_size_gbs
  }

  metadata = {
    ssh_authorized_keys = trimspace(file(pathexpand(var.ssh_public_key_path)))
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml.tftpl", {
      database_password      = random_password.database.result
      jwt_secret             = random_password.jwt.result
      grafana_password       = random_password.grafana.result
      bootstrap_password     = random_password.bootstrap.result
      bootstrap_email        = var.bootstrap_admin_email
      cors_origin            = var.cors_allowed_origin
      demo_customer_password = random_password.demo_customer.result
      oci_backup_uri         = "oci://${oci_objectstorage_bucket.backups.name}/postgres"
      oci_namespace          = data.oci_objectstorage_namespace.ims.namespace
    }))
  }

  lifecycle {
    ignore_changes = [metadata]
    precondition {
      condition     = try(length(data.oci_core_images.ubuntu_arm64.images), 0) > 0
      error_message = "No available Ubuntu 24.04 ARM64 image supports VM.Standard.A1.Flex in the selected compartment/region."
    }
  }
}

data "oci_core_vnic_attachments" "ims" {
  compartment_id = var.compartment_ocid
  instance_id    = oci_core_instance.ims.id
}

data "oci_core_vnic" "primary" {
  vnic_id = data.oci_core_vnic_attachments.ims.vnic_attachments[0].vnic_id
}

data "oci_core_private_ips" "primary" {
  vnic_id = data.oci_core_vnic.primary.id
}

resource "oci_core_public_ip" "reserved" {
  compartment_id = var.compartment_ocid
  display_name   = "${var.name_prefix}-reserved-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.primary.private_ips[0].id
  freeform_tags  = var.freeform_tags
}
