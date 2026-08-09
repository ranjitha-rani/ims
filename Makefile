SHELL := /bin/sh

.PHONY: help setup up app down reset logs config observability tf-fmt tf-validate tf-plan oci-fmt oci-validate oci-compose

help:
	@echo "setup          Copy the local environment template"
	@echo "up             Start data and observability services"
	@echo "app            Start all services, including API and frontend"
	@echo "down           Stop local services"
	@echo "reset          Stop services and delete local volumes"
	@echo "config         Validate and render Compose configuration"
	@echo "tf-fmt         Format all Terraform"
	@echo "tf-validate    Initialize locally and validate Terraform"
	@echo "tf-plan        Plan the application stack (BACKEND_CONFIG required)"
	@echo "oci-fmt        Check OCI Terraform formatting"
	@echo "oci-validate   Initialize and validate OCI Terraform"
	@echo "oci-compose    Validate the OCI runtime Compose file"

setup:
	@test -f .env || cp .env.example .env

up: setup
	docker compose up -d

app: setup
	docker compose --profile app up -d --build

down:
	docker compose --profile app down

reset:
	docker compose --profile app down --volumes

logs:
	docker compose --profile app logs -f

config:
	docker compose --profile app config --quiet

observability: up
	@echo "Grafana: http://localhost:$${GRAFANA_PORT:-3000}"
	@echo "Prometheus: http://localhost:$${PROMETHEUS_PORT:-9090}"

tf-fmt:
	terraform fmt -recursive infra

tf-validate:
	terraform -chdir=infra/bootstrap init -backend=false
	terraform -chdir=infra/bootstrap validate
	terraform -chdir=infra/terraform init -backend=false
	terraform -chdir=infra/terraform validate

tf-plan:
	@test -n "$(BACKEND_CONFIG)" || (echo "Set BACKEND_CONFIG to a backend .hcl file"; exit 1)
	terraform -chdir=infra/terraform init -backend-config="$(BACKEND_CONFIG)"
	terraform -chdir=infra/terraform plan

oci-fmt:
	@test -n "$$(ls infra/oci/terraform/*.tf 2>/dev/null)" || (echo "No OCI Terraform files found"; exit 1)
	terraform fmt -check -recursive infra/oci/terraform

oci-validate:
	@test -n "$$(ls infra/oci/terraform/*.tf 2>/dev/null)" || (echo "No OCI Terraform files found"; exit 1)
	terraform -chdir=infra/oci/terraform init -backend=false
	terraform -chdir=infra/oci/terraform validate

oci-compose:
	@test -f compose.oci.yaml || (echo "No OCI Compose file found"; exit 1)
	docker compose --env-file .env.oci.example -f compose.oci.yaml config --quiet
