# Keep Room entity/metadata (needed for schema)
-keep class com.pakarai.alarme.data.** { *; }

# Accessibility service must keep handler methods
-keepattributes *Annotation*
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent);
    public void onInterrupt();
}
-keep class * extends android.app.Service