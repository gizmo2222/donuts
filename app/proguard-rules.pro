# R8 rules for the release build, added to AGP's proguard-android-optimize.txt (see app/build.gradle).
# Nothing extra is needed today: the app has no reflection, no WebView and no serialized classes, and the
# AGP defaults already keep runtime-visible annotations, @Keep members, enums, Parcelables and the
# activities named in the manifest. Add a rule here only with a comment saying what needs it.
