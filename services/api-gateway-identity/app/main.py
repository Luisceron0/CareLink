from uuid import uuid4
import os
from datetime import datetime, timedelta

from fastapi import FastAPI, HTTPException
import jwt

from .schemas import (
    RegisterRequest,
    RegisterResponse,
    TenantRegisterRequest,
    TenantRegisterResponse,
    LoginRequest,
    LoginResponse,
)
from passlib.hash import argon2
from .db import SessionLocal, Tenant, User
from contextlib import contextmanager


@contextmanager
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

app = FastAPI()

# SECURITY: In production set `IDENTITY_JWT_SECRET` via secure env var or secret manager.
SECRET_KEY = os.environ.get("IDENTITY_JWT_SECRET", "dev-secret")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60


@app.post("/api/v1/auth/register", response_model=RegisterResponse, status_code=201)
async def register(body: RegisterRequest):
    # SECURITY: Validate input with Pydantic. In production this handler must:
    # - encrypt PHI before persistence
    # - write audit log for PHI writes
    # - validate tenant context and rate-limit to avoid abuse
    # Here we implement a minimal, non-persistent example for tests.
    # Hash password using Argon2id and persist
    password_hash = argon2.hash(body.password)
    user_id = str(uuid4())
    with get_db() as db:
        user = User(id=user_id, email=body.email, password_hash=password_hash, tenant_id=None)
        db.add(user)
        db.commit()
    return RegisterResponse(id=user_id, email=body.email)


@app.post(
    "/api/v1/tenants/register",
    response_model=TenantRegisterResponse,
    status_code=201,
)
async def tenant_register(body: TenantRegisterRequest):
    # SECURITY: Validate input with Pydantic. Production must:
    # - create tenant schema/DB separation
    # - send verification email and provision tenant resources
    # Here we return a minimal, non-persistent representation for tests.
    tenant_id = str(uuid4())
    slug = body.legal_name.lower().replace(" ", "-")
    with get_db() as db:
        tenant = Tenant(id=tenant_id, legal_name=body.legal_name, slug=slug, contact_email=body.contact_email)
        db.add(tenant)
        db.commit()
    return TenantRegisterResponse(id=tenant_id, slug=slug, contact_email=body.contact_email)


@app.post("/api/v1/auth/login", response_model=LoginResponse)
async def login(body: LoginRequest):
    with get_db() as db:
        user = db.query(User).filter(User.email == body.email).first()
        if not user:
            raise HTTPException(status_code=401, detail="Invalid credentials")
        if not argon2.verify(body.password, user.password_hash):
            raise HTTPException(status_code=401, detail="Invalid credentials")

    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode = {"sub": user.id, "email": user.email, "exp": expire}
    token = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return LoginResponse(access_token=token)
