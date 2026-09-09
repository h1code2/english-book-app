# Room 生成的实现类由 consumer rules 保留；以下为应用额外规则
-keep class org.english.book.data.** { *; }
-dontwarn androidx.room.paging.**

# WordTapSpeaker 通过 lambda 回调；保留以防混淆影响（保守起见）
-keep class org.english.book.ui.WordTapSpeaker { *; }

# sherpa-onnx JNI 绑定（反射/JNI 按名调用，保守 keep）
-keep class com.k2fsa.sherpa.onnx.** { *; }
