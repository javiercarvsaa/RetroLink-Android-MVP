package cl.retrolink.app.net;

final class StreamProtocol {
    static final int HELLO = 0x524C4332; // RLC2
    static final int CMD_VIEW = 0x56494557; // VIEW
    static final int FRAME = 0x524C4632; // RLF2
    static final int FLAG_PRE_CROPPED = 1;
    static final int FLAG_DK64_RAW = 1 << 1;

    private StreamProtocol() {}
}
