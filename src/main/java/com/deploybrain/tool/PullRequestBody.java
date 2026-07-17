package com.deploybrain.tool;

public class PullRequestBody {

    public static String build(String failureType, double confidence, String rootCause,
                               String evidenceLines, String fixDescription, String filePath) {
        return String.format("""
            ## DeployBrain Diagnosis

            **Failure type:** %s (confidence: %.0f%%)

            ## Root cause

            %s

            ## Evidence from build logs
            %s
            
            ## Fix applied

            %s

            ## Files changed

            `%s`

            ---
            *This fix was generated automatically by DeployBrain. Please review carefully before merging.*
            """,
                failureType,
                confidence * 100,
                rootCause != null ? rootCause : "Not specified",
                truncate(evidenceLines, 1000),
                fixDescription,
                filePath
        );
    }

    public static String buildTitle(String failureType, String repoShortContext) {
        return String.format("DeployBrain: fix %s in %s", failureType, repoShortContext);
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "N/A";
        return text.length() > maxChars ? text.substring(0, maxChars) + "\n... [truncated]" : text;
    }
}