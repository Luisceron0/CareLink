from pydantic import BaseModel, EmailStr, Field


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8)


class RegisterResponse(BaseModel):
    id: str
    email: EmailStr


class TenantRegisterRequest(BaseModel):
    legal_name: str
    tax_id: str
    contact_email: EmailStr
    country: str
    timezone: str


class TenantRegisterResponse(BaseModel):
    id: str
    slug: str
    contact_email: EmailStr


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8)


class LoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
