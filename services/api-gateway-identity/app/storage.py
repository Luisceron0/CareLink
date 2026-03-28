from typing import Optional, Dict
from uuid import uuid4


class InMemoryStore:
    def __init__(self):
        self.tenants: Dict[str, dict] = {}
        self.users: Dict[str, dict] = {}

    def create_tenant(self, legal_name: str, slug: str, contact_email: str) -> dict:
        tenant_id = str(uuid4())
        tenant = {"id": tenant_id, "legal_name": legal_name, "slug": slug, "contact_email": contact_email}
        self.tenants[tenant_id] = tenant
        return tenant

    def get_tenant(self, tenant_id: str) -> Optional[dict]:
        return self.tenants.get(tenant_id)

    def create_user(self, email: str, password_hash: str, tenant_id: Optional[str] = None) -> dict:
        user_id = str(uuid4())
        user = {"id": user_id, "email": email, "password_hash": password_hash, "tenant_id": tenant_id}
        self.users[email] = user
        return user

    def get_user_by_email(self, email: str) -> Optional[dict]:
        return self.users.get(email)


store = InMemoryStore()
