from fastapi.testclient import TestClient
from app.main import app


client = TestClient(app)


def test_register_success():
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "user@example.com", "password": "strongpassword"},
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data.get("id")
    assert data.get("email") == "user@example.com"


def test_register_validation_error():
    # invalid email and short password -> validation error (422)
    resp = client.post(
        "/api/v1/auth/register", json={"email": "bad", "password": "short"}
    )
    assert resp.status_code == 422
