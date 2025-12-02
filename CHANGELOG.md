## 3.3.2

- Fix: add `android:exported="false"` and `android:foregroundServiceType="connectedDevice"` to the service declaration in AndroidManifest.xml. Related: https://console.firebase.google.com/project/powerpal-production/crashlytics/app/android:net.powerpal.PowerPal/issues/3ecf6624fa8b468a6158d6eea755e3de

## 3.3.0

- Fix: add null check for reactContext in onHostResume and onHostDestroy of RNNordicDfuModule