package alienkrishn.dex2c;

import com.android.tools.smali.dexlib2.iface.DexFile;

final class DexSource {

    private final String entryName;
    private final DexFile dexFile;

    DexSource(String entryName, DexFile dexFile) {
        this.entryName = entryName;
        this.dexFile = dexFile;
    }

    String entryName() {
        return entryName;
    }

    DexFile dexFile() {
        return dexFile;
    }
}
