from fastapi import FastAPI, HTTPException
from contextlib import asynccontextmanager
import os
import time
import logging

from schemas import ClassifyRequest, ClassifyResponse, HealthResponse
from classifier import ClassifierService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

MODEL_PATH = os.path.join(os.path.dirname(__file__), "classifier.joblib")

classifier_service: ClassifierService = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global classifier_service
    logger.info("Loading classifier.joblib at startup...")
    start = time.time()
    classifier_service = ClassifierService(MODEL_PATH)
    logger.info(f"Model loaded in {time.time() - start:.2f}s")
    yield
    logger.info("Shutting down ML service")


app = FastAPI(title="DeployBrain ML Classifier", lifespan=lifespan)


@app.get("/health", response_model=HealthResponse)
def health():
    return HealthResponse(
        status="UP",
        model_loaded=classifier_service is not None and classifier_service.is_loaded()
    )


@app.post("/classify", response_model=ClassifyResponse)
def classify(request: ClassifyRequest):
    if classifier_service is None or not classifier_service.is_loaded():
        raise HTTPException(status_code=503, detail="Model not loaded")

    try:
        failure_type, confidence, evidence_lines = classifier_service.predict(request.log_text)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Classification failed for build {request.build_id}: {e}")
        raise HTTPException(status_code=500, detail="Classification failed")

    return ClassifyResponse(
        failure_type=failure_type,
        confidence=confidence,
        evidence_lines=evidence_lines
    )