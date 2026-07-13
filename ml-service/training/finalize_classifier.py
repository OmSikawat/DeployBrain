import pandas as pd
import joblib
from sklearn.model_selection import train_test_split
from sklearn.svm import LinearSVC
from sklearn.calibration import CalibratedClassifierCV

from feature_engineering import truncate_text, build_vectorizer

print("Loading dataset...")
df = pd.read_csv("training_data.csv")
df["log_text"] = df["log_text"].apply(truncate_text)

X = df["log_text"]
y = df["failure_type"]

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, stratify=y, random_state=42
)

print("Fitting TF-IDF vectorizer...")
vectorizer = build_vectorizer()
X_train_vec = vectorizer.fit_transform(X_train)
X_test_vec = vectorizer.transform(X_test)

# CHANGE THESE TWO LINES if you picked RandomForest instead:
# from sklearn.ensemble import RandomForestClassifier
# base_model = RandomForestClassifier(n_estimators=200, class_weight='balanced', random_state=42, n_jobs=-1)

print("Training and calibrating final LinearSVC model...")
base_model = LinearSVC(class_weight='balanced', C=1.0, max_iter=5000)

# CalibratedClassifierCV wraps the base model and internally cross-validates
# to produce genuinely meaningful probability scores - critical since
# Day 9's confidence threshold (>=0.75) depends on real calibrated
# probabilities, not raw SVM decision function values.
calibrated_model = CalibratedClassifierCV(base_model, cv=5, method='sigmoid')
calibrated_model.fit(X_train_vec, y_train)

# Bundle vectorizer + calibrated model together as ONE object,
# so Day 8's FastAPI service only needs to load a single file.
bundle = {
    'vectorizer': vectorizer,
    'model': calibrated_model,
    'classes': list(calibrated_model.classes_)
}

joblib.dump(bundle, 'classifier.joblib')
print("\nSaved classifier.joblib - final bundled model ready for Day 8")

# save test set for evaluate_model.py to use
joblib.dump({'X_test': X_test, 'y_test': y_test}, 'test_holdout.joblib')
print("Saved test_holdout.joblib for evaluation script")