#!/usr/bin/env bash
set -euo pipefail
# Instala dependencias del servicio e ejecuta los tests desde la raíz del repo
python -m pip install -r services/api-gateway-identity/requirements.txt
python -m pytest -q services/api-gateway-identity/tests/test_register.py -q