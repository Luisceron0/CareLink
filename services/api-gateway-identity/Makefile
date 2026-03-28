.PHONY: install test lint typecheck

install:
	python -m pip install -r requirements.txt
	python -m pip install -r dev-requirements.txt

test:
	python -m pytest -q tests/test_register.py -q

lint:
	python -m ruff check .

typecheck:
	python -m mypy app
