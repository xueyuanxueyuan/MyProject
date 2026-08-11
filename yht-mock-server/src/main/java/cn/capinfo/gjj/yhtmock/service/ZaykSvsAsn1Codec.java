package cn.capinfo.gjj.yhtmock.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Minimal DER codec for the zayk SVS socket protocol used by ZaSVSApi.
 */
final class ZaykSvsAsn1Codec {

    private static final DateTimeFormatter GENERALIZED_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'");

    private ZaykSvsAsn1Codec() {
    }

    static SvsRequest decodeRequest(byte[] packet) {
        DerValue outer = readSingle(packet);
        if (outer.tagClass != 0 || outer.tagNo != 16 || !outer.constructed) {
            throw new IllegalArgumentException("SVS request is not a DER SEQUENCE");
        }
        List<DerValue> items = readAll(outer.value);
        if (items.size() < 3) {
            throw new IllegalArgumentException("SVS request has too few fields");
        }
        int version = asInt(items.get(0));
        int opType = asInt(items.get(1));
        DerValue taggedBody = items.get(2);
        if (taggedBody.tagClass != 2 || taggedBody.tagNo != opType) {
            throw new IllegalArgumentException("SVS request opType/tag mismatch: op=" + opType + ", tag=" + taggedBody.tagNo);
        }
        return new SvsRequest(version, opType, readAll(taggedBody.value));
    }

    static byte[] encodeBytesResponse(int opType, byte[] bytes) {
        return encodeOuter(opType, taggedSequence(opType, integer(0), octet(bytes)));
    }

    static byte[] encodeSuccessCodeResponse(int opType) {
        return encodeOuter(opType, taggedInteger(opType, 0));
    }

    static byte[] encodeErrorResponse(int opType, int errCode) {
        return encodeOuter(opType, taggedInteger(opType, errCode));
    }

    static byte[] readDerPacket(java.io.InputStream input) throws IOException {
        int first = input.read();
        if (first < 0) {
            return null;
        }
        int lenFirst = input.read();
        if (lenFirst < 0) {
            throw new IOException("Unexpected EOF while reading DER length");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(first);
        out.write(lenFirst);
        int length;
        if ((lenFirst & 0x80) == 0) {
            length = lenFirst;
        } else {
            int count = lenFirst & 0x7F;
            if (count == 0 || count > 4) {
                throw new IOException("Unsupported DER length form");
            }
            byte[] lenBytes = input.readNBytes(count);
            if (lenBytes.length != count) {
                throw new IOException("Unexpected EOF while reading DER long length");
            }
            out.write(lenBytes);
            length = 0;
            for (byte b : lenBytes) {
                length = (length << 8) | (b & 0xFF);
            }
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new IOException("Unexpected EOF while reading DER value");
        }
        out.write(value);
        return out.toByteArray();
    }

    static int asInt(DerValue value) {
        if (value == null || value.tagClass != 0 || value.tagNo != 2) {
            return 0;
        }
        return new BigInteger(value.value).intValue();
    }

    static byte[] asBytes(DerValue value) {
        if (value == null) {
            return new byte[0];
        }
        return value.value == null ? new byte[0] : value.value;
    }

    static String asUtf8(DerValue value) {
        return new String(asBytes(value), StandardCharsets.UTF_8);
    }

    static DerValue firstOctet(List<DerValue> values) {
        for (DerValue value : values) {
            if (value.tagClass == 0 && value.tagNo == 4) {
                return value;
            }
        }
        return null;
    }

    static DerValue octetAt(List<DerValue> values, int index) {
        int current = 0;
        for (DerValue value : values) {
            if (value.tagClass == 0 && value.tagNo == 4) {
                if (current == index) {
                    return value;
                }
                current++;
            }
        }
        return null;
    }

    static DerValue lastOctet(List<DerValue> values) {
        DerValue result = null;
        for (DerValue value : values) {
            if (value.tagClass == 0 && value.tagNo == 4) {
                result = value;
            }
        }
        return result;
    }

    static DerValue taggedAt(List<DerValue> values, int tagNo) {
        for (DerValue value : values) {
            if (value.tagClass == 2 && value.tagNo == tagNo) {
                return value;
            }
        }
        return null;
    }

    static List<DerValue> octets(List<DerValue> values) {
        List<DerValue> result = new ArrayList<>();
        for (DerValue value : values) {
            if (value.tagClass == 0 && value.tagNo == 4) {
                result.add(value);
            }
        }
        return result;
    }

    private static byte[] encodeOuter(int opType, byte[] taggedBody) {
        return sequence(integer(1), integer(opType), taggedBody, generalizedTime());
    }

    private static byte[] taggedSequence(int tagNo, byte[]... children) {
        return tlv(contextConstructedTag(tagNo), concat(children));
    }

    private static byte[] taggedInteger(int tagNo, int value) {
        return tlv(contextConstructedTag(tagNo), integer(value));
    }

    private static byte[] sequence(byte[]... children) {
        return tlv(new byte[]{0x30}, concat(children));
    }

    private static byte[] integer(int value) {
        return tlv(new byte[]{0x02}, BigInteger.valueOf(value).toByteArray());
    }

    private static byte[] octet(byte[] value) {
        return tlv(new byte[]{0x04}, value == null ? new byte[0] : value);
    }

    private static byte[] generalizedTime() {
        String text = ZonedDateTime.now(ZoneOffset.UTC).format(GENERALIZED_TIME_FORMATTER);
        return tlv(new byte[]{0x18}, text.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] contextConstructedTag(int tagNo) {
        if (tagNo < 31) {
            return new byte[]{(byte) (0xA0 | tagNo)};
        }
        return new byte[]{(byte) 0xBF, (byte) tagNo};
    }

    private static byte[] tlv(byte[] tag, byte[] value) {
        byte[] length = length(value.length);
        byte[] result = new byte[tag.length + length.length + value.length];
        System.arraycopy(tag, 0, result, 0, tag.length);
        System.arraycopy(length, 0, result, tag.length, length.length);
        System.arraycopy(value, 0, result, tag.length + length.length, value.length);
        return result;
    }

    private static byte[] length(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        }
        int tmp = length;
        int count = 0;
        while (tmp > 0) {
            count++;
            tmp >>= 8;
        }
        byte[] result = new byte[count + 1];
        result[0] = (byte) (0x80 | count);
        for (int i = count; i > 0; i--) {
            result[i] = (byte) (length & 0xFF);
            length >>= 8;
        }
        return result;
    }

    private static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array == null ? 0 : array.length;
        }
        byte[] result = new byte[size];
        int pos = 0;
        for (byte[] array : arrays) {
            if (array == null) {
                continue;
            }
            System.arraycopy(array, 0, result, pos, array.length);
            pos += array.length;
        }
        return result;
    }

    private static DerValue readSingle(byte[] bytes) {
        ParseResult result = read(bytes, 0, bytes.length);
        if (result.nextOffset != bytes.length) {
            throw new IllegalArgumentException("DER packet has trailing data");
        }
        return result.value;
    }

    private static List<DerValue> readAll(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }
        List<DerValue> values = new ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            ParseResult result = read(bytes, offset, bytes.length);
            values.add(result.value);
            offset = result.nextOffset;
        }
        return values;
    }

    private static ParseResult read(byte[] bytes, int offset, int limit) {
        if (offset >= limit) {
            throw new IllegalArgumentException("DER offset is out of range");
        }
        int first = bytes[offset++] & 0xFF;
        int tagClass = (first & 0xC0) >> 6;
        boolean constructed = (first & 0x20) != 0;
        int tagNo = first & 0x1F;
        if (tagNo == 0x1F) {
            tagNo = 0;
            int b;
            do {
                if (offset >= limit) {
                    throw new IllegalArgumentException("Invalid DER high tag");
                }
                b = bytes[offset++] & 0xFF;
                tagNo = (tagNo << 7) | (b & 0x7F);
            } while ((b & 0x80) != 0);
        }
        if (offset >= limit) {
            throw new IllegalArgumentException("Missing DER length");
        }
        int lenFirst = bytes[offset++] & 0xFF;
        int length;
        if ((lenFirst & 0x80) == 0) {
            length = lenFirst;
        } else {
            int count = lenFirst & 0x7F;
            if (count == 0 || count > 4 || offset + count > limit) {
                throw new IllegalArgumentException("Unsupported DER length");
            }
            length = 0;
            for (int i = 0; i < count; i++) {
                length = (length << 8) | (bytes[offset++] & 0xFF);
            }
        }
        if (offset + length > limit) {
            throw new IllegalArgumentException("DER value exceeds packet length");
        }
        byte[] value = Arrays.copyOfRange(bytes, offset, offset + length);
        return new ParseResult(new DerValue(tagClass, constructed, tagNo, value), offset + length);
    }

    record SvsRequest(int version, int opType, List<DerValue> body) {
    }

    record DerValue(int tagClass, boolean constructed, int tagNo, byte[] value) {
    }

    private record ParseResult(DerValue value, int nextOffset) {
    }
}