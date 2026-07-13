from sklearn.feature_extraction.text import TfidfVectorizer

MAX_TEXT_LENGTH = 50000

def truncate_text(text) -> str:
    """
    Caps any single log_text to 50,000 characters. Applied identically
    at both training time and Day 8 serving time to keep vectorizer
    vocabulary/IDF weights consistent between the two.
    """
    if not isinstance(text, str):
        return ""
    return text[:MAX_TEXT_LENGTH]

def build_vectorizer() -> TfidfVectorizer:
    """
    Pure TF-IDF, no structured features - simplicity choice.
    sublinear_tf dampens the effect of very frequent terms, which
    matters given the wide text-length variance in this dataset.
    """
    return TfidfVectorizer(
        max_features=5000,
        ngram_range=(1, 2),
        sublinear_tf=True,
        min_df=2
    )