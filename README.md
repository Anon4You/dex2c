# dex2c

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/java-17-blue.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/gradle-9-green.svg)](https://gradle.org/)

Dalvik-to-C++ JNI compiler for Termux.

`dex2c` takes an Android APK, compiles selected Java/Kotlin methods from
Dalvik bytecode into C++ JNI functions, marks the original methods `native`,
and repacks the APK with a generated `libdex2c.so`. At runtime the native
library loads before the application class, and the patched methods call into
the compiled code.

This is a full Java port of [codehasan/dex2c](https://github.com/codehasan/dex2c),
packaged as a single runnable jar. The C++ runtime is bundled inside the jar,
so no external project template is needed.

## Why dex2c

### Faster compiled methods

Selected methods are translated to C++ and built ahead of time with the
Android NDK in Release mode. At runtime those methods execute as optimized
native machine code instead of Dalvik bytecode, with no bytecode dispatch and
no JIT warm-up for the compiled bodies. The gain is largest in loops,
arithmetic, string handling, and array-heavy code.

JNI calls still have a boundary cost, so selective compilation works best:
compile the methods that do real work and leave small glue methods in Dalvik.

### No apktool round trip

`dex2c` does not unpack the APK into smali and resource trees with apktool or
apkeditor. It reads DEX with dexlib2, patches only the selected methods, reads
the binary manifest with ARSCLib, and repacks the ZIP directly. Avoiding a
full decode/encode cycle keeps memory use down, saves build time, and avoids
the apktool recompilation step that can fail on Termux.

### What you get

- TermuxVoid package with automatic SDK, NDK, and CMake discovery.
- Single runnable jar; the C++ runtime is bundled, so no project template is
  needed.
- Multi-dex APK support.
- Regex, exact-match, and keep filters, plus `@Dex2C` annotations.
- Per-ABI builds for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, and
  `riscv64`.
- `RegisterNatives` mode for APKs that load classes through custom class
  loaders.
- Optional string-constant obfuscation.
- Automatic loader generation when the APK has no `Application` class.
- Native project export without building (`--no-build`, `--source-dir`,
  `--project-archive`).
- Optional signing with the bundled test key.

## Installation

From the [TermuxVoid repository](https://termuxvoid.github.io):

```bash
pkg install dex2c
```

This installs the launcher and its build dependencies. Run `dex2c --help` to
verify the installation.

## Quick start

Compile a single class into native code:

```bash
dex2c -a app.apk -o app-dex2c.apk -f filter.txt
```

Example `filter.txt`:

```text
Lcom/example/app/Utils;
```

The output is unsigned by default. To sign it with the bundled Android test
key:

```bash
dex2c -a app.apk -o app-dex2c.apk -f filter.txt -s
```

## Requirements

When installing from TermuxVoid, the required tools are installed for you.

Building from source or running the jar directly requires:

- JDK 17 or newer
- Android NDK
- CMake (the NDK-bundled CMake works)
- Android SDK is optional; `dex2c` does not invoke `aapt`, `d8`, or the SDK
  build-tools

On Termux, the following paths are detected automatically:

```text
$PREFIX/opt/android-sdk
$PREFIX/opt/android-sdk/ndk/<version>
$PREFIX/opt/android-ndk
$PREFIX/opt/android-cmake/<version>/bin/cmake
```

Paths can also be supplied with `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
`ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`, or overridden with `--sdk`, `--ndk`
and `--cmake`.

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
Lcom/example/app/Utils;

# Compile one exact method
=Lcom/example/app/MainActivity;onCreate(Landroid/os/Bundle;)V

# Never compile debug helpers
!Lcom/example/app/Utils;debug.*

# Compile every method under a package
Lcom/example/app/
```

The `find()` matcher is used, so `Lcom/example/app/` matches every method in
that package. The catch-all rule `.*` is refused unless `--allow-global` is
passed; it is not recommended.

Methods annotated with `@Dex2C` on the class or method are compiled
automatically. Native methods, `<clinit>`, and overloads that differ only by
return type are skipped. Use `--skip-synthetic` to skip synthetic methods.

## Usage

Compile with `RegisterNatives` instead of exported JNI symbols:

```bash
dex2c -a app.apk -o out.apk -f filter.txt -d
```

Obfuscate embedded string constants:

```bash
dex2c -a app.apk -o out.apk -f filter.txt -p
```

Build only selected ABIs:

```bash
dex2c -a app.apk -o out.apk -f filter.txt \
  --abi arm64-v8a,armeabi-v7a
```

Generate the native project without building it:

```bash
dex2c -a app.apk -o out.apk -f filter.txt \
  --no-build --source-dir project --project-archive project.zip
```

Common options:

| Option | Description |
| --- | --- |
| `-a, --input <apk>` | Input APK |
| `-o, --output <apk>` | Output APK (default: `<input>-dex2c.apk`) |
| `-f, --filter <file>` | Method filter file |
| `--filter-line <regex>` | Additional filter rule |
| `-s, --sign` | Sign with the bundled Android test key |
| `-d, --dynamic-register` | Export native methods with `RegisterNatives` |
| `-p, --obfuscate` | Obfuscate embedded strings |
| `--abi <abi[,abi]>` | ABIs to build |
| `--api <level>` | Minimum Android API (default: 24) |
| `--no-build` | Generate native sources only |
| `--source-dir <dir>` | Write and keep the native project |
| `--project-archive <zip>` | Write the native project to a zip |
| `--custom-loader <class>` | Loader class when the APK has no `Application` class |
| `--ignore-unsupported-abis` | Skip APK ABIs the NDK cannot build |
| `-v, --verbose` | Verbose output |

Run `dex2c --help` for the full option list.

## How it works

1. Parse all `classes*.dex` files and select methods with the filter.
2. Convert Dalvik bytecode to SSA-style IR and emit C++ JNI source.
3. Patch the dex files: compiled methods become `native`, and the application
   class loads `libdex2c.so` from a static initializer.
4. Build `libdex2c.so` with the NDK for the requested ABIs.
5. Repack the APK and remove stale signatures.
6. Optionally sign the result.

If the APK has no `Application` class, a loader class is generated and set in
the manifest. Use `--custom-loader` to choose its name.

## ABIs

When the input APK contains `lib/<abi>/` directories, only those ABIs are
built. Otherwise the default is `arm64-v8a` plus `armeabi-v7a`. Supported ABIs
are `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, and `riscv64`.
`--ignore-unsupported-abis` skips APK ABIs the NDK cannot build instead of
failing the run.

## Signing

The output APK is **unsigned by default**. With `-s`/`--sign`, it is signed
with the bundled Android test key (the same key used by Qrylz). This key is
public and must not be used for production releases.

## Build from source

```bash
./gradlew shadowJar
```

The runnable jar is written to `build/libs/dex2c.jar`. Copy it to your
working directory and run it by name:

```bash
cp build/libs/dex2c.jar .
java -jar dex2c.jar -a app.apk -o app-dex2c.apk -f filter.txt
```

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/alienkrishn/dex2c/Dex2C.java` | Command line interface |
| `.../Pipeline.java` | End-to-end compile, patch, build, and sign pipeline |
| `.../DexSource.java`, `.../ApkInfo.java` | APK and multi-dex inspection |
| `.../IrBuilder.java`, `.../Graph.java`, `.../Dex2CCompiler.java` | Dalvik-to-C++ compiler |
| `.../DexPatcher.java`, `.../ApkRepacker.java` | DEX patching and APK repacking |
| `.../NativeProject.java`, `.../Toolchain.java` | Native project generation and NDK build |
| `.../Signer.java` | APK signing |
| `native/nc/` | Bundled C++ runtime |

## Limitations

- APK input only.
- `--api` defaults to 24.
- Methods using bytecode the compiler cannot handle are skipped with a
  warning; the rest of the APK is still processed.
- `arm64-v8a` is the primary tested ABI. Other ABIs build but have not been
  exercised as heavily.
- `--dynamic-register` is useful for classes loaded through custom class
  loaders.

## Credits

- Upstream design and runtime: [codehasan/dex2c](https://github.com/codehasan/dex2c)
- APK reading/repacking: [ARSCLib](https://github.com/REAndroid/ARSCLib)
- Dex parsing/writing: [smali/dexlib2](https://github.com/google/smali)
- APK signing: [apksig](https://android.googlesource.com/platform/tools/apksig)
- Command line interface: [Picocli](https://github.com/remkop/picocli)

The bundled native runtime is licensed under Apache-2.0; see
`native/nc/LICENSE`. The rest of this project is MIT licensed; see `LICENSE`.
