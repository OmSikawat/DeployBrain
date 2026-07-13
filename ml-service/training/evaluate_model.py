import joblib
from sklearn.metrics import classification_report, confusion_matrix
import pandas as pd

from feature_engineering import truncate_text

print("Loading final classifier and test set...")
bundle = joblib.load('classifier.joblib')
vectorizer = bundle['vectorizer']
model = bundle['model']
classes = bundle['classes']

holdout = joblib.load('test_holdout.joblib')
X_test = holdout['X_test']
y_test = holdout['y_test']

X_test_vec = vectorizer.transform(X_test)
y_pred = model.predict(X_test_vec)
y_proba = model.predict_proba(X_test_vec)

print("=" * 50)
print("FINAL CLASSIFICATION REPORT")
print("=" * 50)
print(classification_report(y_test, y_pred))

print("\n" + "=" * 50)
print("CONFUSION MATRIX")
print("=" * 50)
cm = confusion_matrix(y_test, y_pred, labels=classes)
cm_df = pd.DataFrame(cm, index=classes, columns=classes)
print(cm_df)

print("\n" + "=" * 50)
print("SAMPLE MISCLASSIFIED EXAMPLES (for interview talking points)")
print("=" * 50)
y_test_reset = y_test.reset_index(drop=True)
X_test_reset = X_test.reset_index(drop=True)

misclassified_count = 0
for i in range(len(y_test_reset)):
    if y_pred[i] != y_test_reset[i]:
        print(f"\nActual: {y_test_reset[i]}  |  Predicted: {y_pred[i]}  |  Confidence: {max(y_proba[i]):.2f}")
        print(f"Text snippet: {X_test_reset[i][:200]}...")
        misclassified_count += 1
        if misclassified_count >= 5:
            break

print(f"\n\nTotal misclassified in test set: {(y_pred != y_test_reset.values).sum()} out of {len(y_test_reset)}")