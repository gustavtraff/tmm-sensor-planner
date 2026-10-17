# The TAK build plugin sets this to the selected host's mapping before R8 runs.
-applymapping <atak.proguard.mapping>
-repackageclasses atakplugin.tmm.sensorplanner

# Match the SDK's conservative release behavior, including persisted enum names.
-dontshrink
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,SourceFile,LineNumberTable

# ATAK instantiates this entry by the class name in assets/plugin.xml.
-keep class se.tmm.sensorplanner.plugin.SensorPlannerPlugin {
    public <init>(gov.tak.api.plugin.IServiceController);
    public void onStart();
    public void onStop();
}
