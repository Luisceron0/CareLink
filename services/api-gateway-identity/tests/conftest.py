import os
import sys


# Ensure the service package directory is importable when pytest is run from repo root
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

# Ensure tests use a clean local sqlite DB for isolation
DB_FILE = os.path.join(ROOT, "test_identity.db")
# Remove any leftover test DB files from previous runs (including WAL/SHM)
for suffix in ("", "-shm", "-wal"):
    try:
        os.remove(DB_FILE + suffix)
    except FileNotFoundError:
        pass

# Use absolute path to avoid ambiguity when pytest runs from repo root
os.environ.setdefault("DATABASE_URL", f"sqlite:///{DB_FILE}")
