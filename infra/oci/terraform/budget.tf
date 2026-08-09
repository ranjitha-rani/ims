locals {
  create_budget_alert = var.enable_budget_alert && var.budget_alert_email != ""
}

resource "oci_budget_budget" "ims" {
  count = local.create_budget_alert ? 1 : 0

  compartment_id        = var.compartment_ocid
  amount                = var.budget_amount
  reset_period          = "MONTHLY"
  display_name          = "${var.name_prefix}-monthly-budget"
  description           = "Alert on unexpected spend in the IMS compartment."
  target_compartment_id = var.compartment_ocid
  target_type           = "COMPARTMENT"
  freeform_tags         = var.freeform_tags
}

resource "oci_budget_alert_rule" "actual_80" {
  count = local.create_budget_alert ? 1 : 0

  budget_id      = oci_budget_budget.ims[0].id
  display_name   = "${var.name_prefix}-budget-actual-80"
  threshold      = 80
  threshold_type = "PERCENTAGE"
  type           = "ACTUAL"
  recipients     = var.budget_alert_email
  message        = "IMS compartment actual spend reached 80% of the ${var.budget_amount} USD monthly budget."
}

resource "oci_budget_alert_rule" "actual_100" {
  count = local.create_budget_alert ? 1 : 0

  budget_id      = oci_budget_budget.ims[0].id
  display_name   = "${var.name_prefix}-budget-actual-100"
  threshold      = 100
  threshold_type = "PERCENTAGE"
  type           = "ACTUAL"
  recipients     = var.budget_alert_email
  message        = "IMS compartment actual spend reached 100% of the ${var.budget_amount} USD monthly budget."
}

resource "oci_budget_alert_rule" "forecast_80" {
  count = local.create_budget_alert ? 1 : 0

  budget_id      = oci_budget_budget.ims[0].id
  display_name   = "${var.name_prefix}-budget-forecast-80"
  threshold      = 80
  threshold_type = "PERCENTAGE"
  type           = "FORECAST"
  recipients     = var.budget_alert_email
  message        = "IMS compartment forecast spend is projected to reach 80% of the ${var.budget_amount} USD monthly budget."
}

resource "oci_budget_alert_rule" "forecast_100" {
  count = local.create_budget_alert ? 1 : 0

  budget_id      = oci_budget_budget.ims[0].id
  display_name   = "${var.name_prefix}-budget-forecast-100"
  threshold      = 100
  threshold_type = "PERCENTAGE"
  type           = "FORECAST"
  recipients     = var.budget_alert_email
  message        = "IMS compartment forecast spend is projected to reach 100% of the ${var.budget_amount} USD monthly budget."
}
