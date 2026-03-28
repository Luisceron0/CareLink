#!/usr/bin/env bash
set -euo pipefail
# Instala deps (runtime + dev) del servicio y ejecuta tests, lint y typecheck
python -m pip install -r services/api-gateway-identity/requirements.txt
python -m pip install -r services/api-gateway-identity/dev-requirements.txt

echo "== Running tests =="
python -m pytest -q services/api-gateway-identity/tests/test_register.py -q
python -m pytest -q services/api-gateway-identity/tests/test_tenant_register.py -q

echo "== Running ruff check (lint) =="
python -m ruff check services/api-gateway-identity || true

echo "== Running mypy (typecheck) =="
python -m mypy services/api-gateway-identity/app || true

echo "== Done =="
