data "oci_objectstorage_namespace" "ims" {
  compartment_id = var.compartment_ocid
}

resource "oci_objectstorage_bucket" "backups" {
  compartment_id = var.compartment_ocid
  namespace      = data.oci_objectstorage_namespace.ims.namespace
  name           = "${var.name_prefix}-backups"
  access_type    = "NoPublicAccess"
  auto_tiering   = "Disabled"
  versioning     = "Disabled"
  freeform_tags  = var.freeform_tags
}

locals {
  create_backup_instance_principal = var.tenancy_ocid != ""
}

resource "oci_identity_dynamic_group" "ims_backup" {
  count = local.create_backup_instance_principal ? 1 : 0

  compartment_id = var.tenancy_ocid
  name           = "${var.name_prefix}-backup-instance-principal"
  description    = "IMS compute instance authorized to upload PostgreSQL backups to Object Storage."
  matching_rule  = "ALL {instance.id = '${oci_core_instance.ims.id}'}"
}

resource "oci_identity_policy" "ims_backup" {
  count = local.create_backup_instance_principal ? 1 : 0

  compartment_id = var.tenancy_ocid
  name           = "${var.name_prefix}-backup-object-storage"
  description    = "Allow the IMS instance to read its backup bucket and manage objects within it."
  statements = [
    "Allow dynamic-group ${oci_identity_dynamic_group.ims_backup[0].name} to read buckets in compartment id ${var.compartment_ocid} where target.bucket.name='${oci_objectstorage_bucket.backups.name}'",
    "Allow dynamic-group ${oci_identity_dynamic_group.ims_backup[0].name} to manage objects in compartment id ${var.compartment_ocid} where target.bucket.name='${oci_objectstorage_bucket.backups.name}'",
  ]
}
