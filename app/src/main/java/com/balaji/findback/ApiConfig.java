package com.balaji.findback;

public class ApiConfig {
    // 🛡️ CENTRAL API KEY REPOSITORY
    // These are used as fallbacks if BuildConfig (local.properties) is empty in Release APKs.
    
    public static final String NVIDIA_API_KEY = "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";
    
    // 🔥 Placeholder - please add your real keys here if you want them to work in Release APK
    public static final String GROQ_API_KEY = "YOUR_GROQ_API_KEY";
    public static final String OPENROUTER_API_KEY = "YOUR_OPENROUTER_API_KEY";
    public static final String COHERE_API_KEY = "YOUR_COHERE_API_KEY";

    public static String getApiKey(String buildConfigKey, String configKey) {
        if (buildConfigKey != null && !buildConfigKey.isEmpty() && !buildConfigKey.equals("null")) {
            return buildConfigKey;
        }
        if (configKey != null && !configKey.isEmpty() && !configKey.startsWith("YOUR_")) {
            return configKey;
        }
        return null;
    }
}
