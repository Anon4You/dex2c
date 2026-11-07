package alienkrishn.dex2c;

final class CompiledMethod {

    private final String source;
    private final String prototype;

    CompiledMethod(String source, String prototype) {
        this.source = source;
        this.prototype = prototype;
    }

    String source() {
        return source;
    }

    String prototype() {
        return prototype;
    }
}
