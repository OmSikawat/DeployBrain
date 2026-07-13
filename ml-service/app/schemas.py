from pydantic import BaseModel
from typing import List

class ClassifyRequest(BaseModel):
    log_text: str
    build_id: str

class ClassifyResponse(BaseModel):
    failure_type: str
    confidence: float
    evidence_lines: List[str]

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool