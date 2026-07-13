import joblib
import os
import numpy as np
from typing import List, Tuple

# CRITICAL: this MUST match feature_engineering.py's truncate_text()
# from Day 6-7 EXACTLY. The model was trained on text truncated to
# this exact limit - predicting on differently-truncated text feeds
# the vectorizer input it never saw during training and silently
# degrades accuracy with no error to indicate why.
MAX_TEXT_LENGTH = 50000

def truncate_text(text: str) -> str:
    if not isinstance(text, str):
        return ""
    return text[:MAX_TEXT_LENGTH]


class ClassifierService:

    def __init__(self, model_path: str):
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"classifier.joblib not found at {model_path}")

        bundle = joblib.load(model_path)
        self.vectorizer = bundle['vectorizer']
        self.model = bundle['model']
        self.classes = bundle['classes']
        self.feature_names = self.vectorizer.get_feature_names_out()

    def is_loaded(self) -> bool:
        return self.model is not None and self.vectorizer is not None

    def predict(self, log_text: str) -> Tuple[str, float, List[str]]:
        if not log_text or not log_text.strip():
            raise ValueError("log_text is empty or whitespace-only")

        cleaned_text = truncate_text(log_text)
        text_vec = self.vectorizer.transform([cleaned_text])

        prediction = self.model.predict(text_vec)[0]
        probabilities = self.model.predict_proba(text_vec)[0]
        confidence = float(max(probabilities))

        evidence_lines = self._extract_evidence_lines(cleaned_text, text_vec, prediction)

        return prediction, confidence, evidence_lines

    def _extract_evidence_lines(self, text: str, text_vec, predicted_class: str, top_n_terms: int = 15, max_lines: int = 3) -> List[str]:
        """
        Finds the log lines that most influenced the prediction, using
        the model's OWN learned coefficient weights for the predicted
        class - not a separate keyword heuristic. This is what makes
        the evidence genuinely tied to why the model made this decision.
        """
        try:
            class_idx = list(self.classes).index(predicted_class)
        except ValueError:
            return []

        # CalibratedClassifierCV wraps multiple fitted LinearSVC instances
        # (one per CV fold) - average their coefficients for stability
        # rather than relying on just one fold's weights.
        coef_sum = None
        fold_count = 0

        for calibrated_clf in self.model.calibrated_classifiers_:
            # sklearn 1.1+ uses .estimator, older versions use .base_estimator
            base = getattr(calibrated_clf, 'estimator', None) or getattr(calibrated_clf, 'base_estimator', None)
            if base is None or not hasattr(base, 'coef_'):
                continue

            fold_coef = base.coef_[class_idx] if base.coef_.shape[0] > 1 else base.coef_[0]
            coef_sum = fold_coef if coef_sum is None else coef_sum + fold_coef
            fold_count += 1

        if coef_sum is None or fold_count == 0:
            return []

        avg_coef = coef_sum / fold_count

        # Get indices of features actually present in this document
        doc_indices = text_vec.nonzero()[1]
        if len(doc_indices) == 0:
            return []

        # Rank present features by their weight for the predicted class
        doc_weights = [(idx, avg_coef[idx]) for idx in doc_indices]
        doc_weights.sort(key=lambda x: x[1], reverse=True)
        top_terms = set(self.feature_names[idx] for idx, _ in doc_weights[:top_n_terms])

        # Find actual log lines containing these top-weighted terms
        lines = text.split("\n")
        scored_lines = []
        for line in lines:
            line_lower = line.lower()
            match_count = sum(1 for term in top_terms if term in line_lower)
            if match_count > 0:
                scored_lines.append((line.strip(), match_count))

        scored_lines.sort(key=lambda x: x[1], reverse=True)
        return [line for line, _ in scored_lines[:max_lines]] if scored_lines else []