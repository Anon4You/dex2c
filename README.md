# dex2c

Dalvik-to-C++ JNI compiler for Termux.

`dex2c` takes an APK, compiles selected Java/Kotlin methods from Dalvik
bytecode into C++ JNI functions, marks the original methods `native`, and
repacks the APK with a generated `libdex2c.so`. At runtime the library loads
before the application class and the patched methods call into the C++ code.

This is a full Java port of [amimo/dex2c](https://github.com/amimo/dex2c),
packaged as a single runnable jar. The C++ runtime is bundled inside the jar,
so no external project template is needed.

## Requirements

- JDK 17 or newer
- Android NDK
- CMake (the NDK's bundled CMake works)
- Android SDK build-tools are not invoked by the tool; Android Studio's
  SDK/NDK/CMake layout is auto-detected when present

On Termux, the default lookup paths are:

```text
$PREFIX/opt/android-sdk
$PREFIX/opt/android-sdk/ndk/<version>
$PREFIX/opt/android-ndk
$PREFIX/opt/android-cmake/<version>/bin/cmake
```

Paths can also be set with `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
`ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, or overridden with `--sdk`, `--ndk`
and `--cmake`.

## Build

```sh
./gradlew shadowJar
```

The runnable jar is written to `build/libs/dex2c.jar`.

## Usage

```sh
java -jar build/libs/dex2c.jar -a app.apk -o app-dex2c.apk -f filter.txt
```

Sign the output with the bundled Android test key:

```sh
java -jar build/libs/dex2c.jar -a app.apk -o app-dex2c.apk -f filter.txt -s
```

Other common options:

```sh
# Compile using RegisterNatives instead of exported JNI symbols
java -jar build/libs/dex2c.jar -a app.apk -o out.apk -f filter.txt -d

# Obfuscate embedded string constants
java -jar build/libs/dex2c.jar -a app.apk -o out.apk -f filter.txt -p

# Build only selected ABIs
java -jar build/libs/dex2c.jar -a app.apk -o out.apk -f filter.txt \
  --abi arm64-v8a,armeabi-v7a

# Generate the native project without building it
java -jar build/libs/dex2c.jar -a app.apk -o out.apk -f filter.txt \
  --no-build --source-dir project --project-archive project.zip
```

Run `java -jar build/libs/dex2c.jar --help` for the full option list.

## Filters

A filter file contains one rule per line. Blank lines and lines starting with
`#` are ignored.

| Rule | Meaning |
| --- | --- |
| `regex` | Compile matching methods |
| `=fullName` | Compile only an exact match |
| `!regex` | Keep matching methods as Dalvik (never compile) |

The full method name has no `->`:

```text
Lcom/example/app/Foo;method(I)V
```

Examples:

```text
# Compile every method in Utils
Lcom/example/app/Utils;.*

# Compile one exact method
=Lcom/example/app/MainActivity;onCreate(Landroid/os/Bundle;)V

# Never compile debug helpers
!Lcom/example/app/Utils;debug.*

# Catch-all (refused unless --allow-global is passed)
.*
```

Methods annotated with `@Dex2C` (class or method annotation) are compiled
automatically. Native methods, `<clinit>`, and overloads that differ only by
return type are skipped.

## How it works

1. Parse all `classes*.dex` files and select methods with the filter.
2. Convert Dalvik bytecode to SSA-style IR and emit C++ JNI source.
3. Patch the dex files: methods become `native`, and the app class loads
   `libdex2c.so` from a static initializer.
4. Build `libdex2c.so` with the NDK for the requested ABIs.
5. Repack the APK and remove stale signatures.
6. Optionally sign the result.

If the APK has no `Application` class, a loader class is created and set in
the manifest. Use `--custom-loader` to choose its name.

## ABIs

When the input APK contains `lib/<abi>/` directories, only those ABIs are
built. Otherwise the default is `arm64-v8a` plus `armeabi-v7a`. Supported
ABIs are `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, and `riscv64`.
`--ignore-unsupported-abis` skips APK ABIs the NDK cannot build.

## Signing

The output APK is **unsigned by default**. With `-s`/`--sign`, it is signed
with the bundled Android test key (the same key used by Qrylz). This key is
public and must not be used for production releases.

## Notes and limitations

- APK input only.
- `--api` defaults to 24.
- `--dynamic-register` is useful for classes loaded through custom class
  loaders.
- Methods using bytecode the compiler cannot handle are skipped with a
  warning; the rest of the APK is still processed.
- `arm64-v8a` is the primary tested ABI. Other ABIs build but have not been
  exercised as heavily.

## Credits

- Upstream design and runtime: [amimo/dex2c](https://github.com/amimo/dex2c)
- APK reading/repacking: [ARSCLib](https://github.com/REAndroid/ARSCLib)
- Dex parsing/writing: [smali/dexlib2](https://github.com/google/smali)
- APK signing: [apksig](https://android.googlesource.com/platform/tools/apksig)

The bundled native runtime is licensed under Apache-2.0; see
`native/nc/LICENSE`. The rest of this project is MIT licensed; see `LICENSE`.
