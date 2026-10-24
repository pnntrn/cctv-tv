# Media3 supplies its own consumer rules. Models use explicit JSON, no reflection.
# The pinned libVLC AAR has no consumer rules. JNI looks up these classes/members by name.
-keep class org.videolan.libvlc.** { *; }
