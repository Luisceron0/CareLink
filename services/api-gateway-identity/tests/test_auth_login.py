from fastapi.testclient import TestClient
from app.main import app


client = TestClient(app)


def test_register_and_login():
    # register
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "jane@example.com", "password": "strongpassword"},
    )
    assert resp.status_code == 201

    # login
    resp2 = client.post(
        "/api/v1/auth/login", json={"email": "jane@example.com", "password": "strongpassword"}
    )
    assert resp2.status_code == 200
    data = resp2.json()
    assert data.get("access_token")
    assert data.get("token_type") == "bearer"
