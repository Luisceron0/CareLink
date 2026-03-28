import os
from sqlalchemy import create_engine, String, Text
from sqlalchemy.orm import sessionmaker, DeclarativeBase, Mapped, mapped_column

DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///./identity_dev.db")

# WARNING: For production use PostgreSQL and connection pooling. This is a dev minimal setup.
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {})
SessionLocal = sessionmaker(bind=engine)


class Base(DeclarativeBase):
    pass


class Tenant(Base):
    __tablename__ = "tenants"
    id: Mapped[str] = mapped_column(String, primary_key=True, index=True)
    legal_name: Mapped[str] = mapped_column(String, nullable=False)
    slug: Mapped[str] = mapped_column(String, nullable=False, unique=True)
    contact_email: Mapped[str] = mapped_column(String, nullable=False)


class User(Base):
    __tablename__ = "users"
    id: Mapped[str] = mapped_column(String, primary_key=True, index=True)
    email: Mapped[str] = mapped_column(String, nullable=False, unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(Text, nullable=False)
    tenant_id: Mapped[str] = mapped_column(String, nullable=True)


def init_db():
    # Create tables automatically in dev when using SQLite
    Base.metadata.create_all(bind=engine)


# Initialize DB for local dev/tests
try:
    init_db()
except Exception:
    # If DB unavailable (e.g., remote Postgres not configured) skip init; migrations should handle it
    pass
