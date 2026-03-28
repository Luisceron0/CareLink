from fastapi import FastAPI

app = FastAPI(title="CareLink Notification Service")

@app.get("/")
async def root():
    return {"service": "notification", "status": "ok"}
