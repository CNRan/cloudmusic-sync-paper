package cn.cloudmusic.paper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class Codec {
    private Codec() {}

    static String readString(byte[] data, int maxBytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int length = readVarInt(in);
        if (length < 0 || length > maxBytes || length > in.available()) throw new IOException("invalid string length");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    static PacketReader reader(byte[] data) {
        return new PacketReader(new DataInputStream(new ByteArrayInputStream(data)));
    }

    static byte[] empty() {
        return new byte[0];
    }

    static byte[] strings(String... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (String value : values) writeString(out, value);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static byte[] play(String songId, String title, String artist, String audioUrl, String coverUrl, long start, long duration) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeString(out, songId);
            writeString(out, title);
            writeString(out, artist);
            writeString(out, audioUrl);
            writeString(out, coverUrl);
            writeVarLong(out, start);
            writeVarLong(out, duration);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static byte[] queue(String json) {
        return strings(json);
    }

    static byte[] ints(int... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            for (int value : values) writeVarInt(out, value);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static void writeVarLong(DataOutputStream out, long value) throws IOException {
        while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
            out.writeByte(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte((int) value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (shift < 35) {
            int next = in.readUnsignedByte();
            result |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) return result;
            shift += 7;
        }
        throw new IOException("varint too long");
    }

    static final class PacketReader {
        private final DataInputStream in;

        private PacketReader(DataInputStream in) {
            this.in = in;
        }

        int varInt() throws IOException {
            return readVarInt(in);
        }

        String string(int maxBytes) throws IOException {
            int length = readVarInt(in);
            if (length < 0 || length > maxBytes || length > in.available()) throw new IOException("invalid string length");
            return new String(in.readNBytes(length), StandardCharsets.UTF_8);
        }
    }
}
