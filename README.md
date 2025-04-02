This is an Android app to get ECG data from a [Polar H10 chest-strap
heart rate
sensor](https://www.polar.com/us-en/sensors/h10-heart-rate-sensor) and
record microphone activity .

It uses the [Polar SDK](https://github.com/polarofficial/polar-ble-sdk) and
[Android Plot](https://github.com/halfhp/androidplot).

I adapted much of the code, from the [AndroidPolarPVC2](https://github.com/kbroman/AndroidPolarPVC2) app, and it's references. Also learned a lot from it.

The Device ID is hard-coded in [`MainActivity.kt`](https://github.com/LuisContrerasOe/AndroidVitalsApp/blob/main/app/src/main/java/org/kbroman/android/polarpvc2/MainActivity.kt#L35).

---

Licensed under the [MIT license](LICENSE). (See <https://en.wikipedia.org/wiki/MIT_License>.)
