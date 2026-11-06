# Gson's versioned draft snapshots use Kotlin field names as their durable keys.
# Planner decision traces may nest other types under this package.
-keepclassmembers class com.training.trackplanner.data.** {
    <fields>;
}
-keepattributes Signature,InnerClasses,EnclosingMethod
