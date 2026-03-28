from fastapi.testclient import TestClient
from app.main import app


client = TestClient(app)


def test_tenant_register_success():
    payload = {
        "legal_name": "Clinica Santa Maria",
        "tax_id": "900123456-7",
        "contact_email": "admin@clinicamaria.com",
        "country": "CO",
        "timezone": "America/Bogota",
    }
    resp = client.post("/api/v1/tenants/register", json=payload)
    assert resp.status_code == 201
    data = resp.json()
    assert data.get("id")
    assert data.get("slug") == "clinica-santa-maria"
    assert data.get("contact_email") == "admin@clinicamaria.com"
