# ATAK instantiates this entry by the class name in assets/plugin.xml.
-keep class se.tmm.sensorplanner.plugin.SensorPlannerPlugin {
    public <init>(gov.tak.api.plugin.IServiceController);
    public void onStart();
    public void onStop();
}
