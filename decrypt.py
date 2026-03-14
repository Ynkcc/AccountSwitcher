import struct
import os
import json
import argparse

class MSDKTea:
    def __init__(self, key):
        if len(key) != 16:
            raise ValueError("TEA key must be exactly 16 bytes")
        self.key = key
        self.rounds = 16
        self.delta = 0x9e3779b9

    def _encrypt_block(self, v):
        v0, v1 = struct.unpack(">2I", v)
        k = struct.unpack(">4I", self.key)
        sum_value = 0
        for _ in range(self.rounds):
            sum_value = (sum_value + self.delta) & 0xffffffff
            v0 = (v0 + (((v1 << 4) + k[0]) ^ (v1 + sum_value) ^ ((v1 >> 5) + k[1]))) & 0xffffffff
            v1 = (v1 + (((v0 << 4) + k[2]) ^ (v0 + sum_value) ^ ((v0 >> 5) + k[3]))) & 0xffffffff
        return struct.pack(">2I", v0, v1)

    def _decrypt_block(self, v):
        v0, v1 = struct.unpack(">2I", v)
        k = struct.unpack(">4I", self.key)
        sum_value = (self.delta * self.rounds) & 0xffffffff
        for _ in range(self.rounds):
            v1 = (v1 - (((v0 << 4) + k[2]) ^ (v0 + sum_value) ^ ((v0 >> 5) + k[3]))) & 0xffffffff
            v0 = (v0 - (((v1 << 4) + k[0]) ^ (v1 + sum_value) ^ ((v1 >> 5) + k[1]))) & 0xffffffff
            sum_value = (sum_value - self.delta) & 0xffffffff
        return struct.pack(">2I", v0, v1)

    @staticmethod
    def _xor8(a, b):
        return bytes(x ^ y for x, y in zip(a, b))

    def _strip_padding(self, out):
        if not out:
            raise ValueError("Decrypted stream is empty")
        pos = (out[0] & 7) + 3
        end = len(out) - 7
        if pos > end:
            raise ValueError("Invalid padding structure")
        return {
            "payload": out[pos:end],
            "raw_plain": out,
            "prefix": out[:pos],
            "suffix": out[end:],
            "pos": pos,
        }

    def _build_padded_plain(self, payload, randomize_padding=True):
        padlen = (8 - ((len(payload) + 10) % 8)) % 8
        if randomize_padding:
            first_byte = (os.urandom(1)[0] & 0xF8) | (padlen & 0x07)
            header = bytes([first_byte]) + os.urandom(padlen + 2)
        else:
            header = bytes([(padlen & 7)]) + (b"\x00" * (padlen + 2))
        tail = b"\x00" * 7
        out = header + payload + tail
        if len(out) % 8 != 0:
            raise ValueError("Generated padded data is not 8-byte aligned")
        return out

    def decrypt_raw(self, data):
        if len(data) < 16 or len(data) % 8 != 0:
            raise ValueError("Cipher data length is invalid")

        # MSDK Variant TEA CBC Decryption
        # Block 0 initialization
        d0 = self._decrypt_block(data[:8])
        out = d0
        prev_d = d0
        prev_c = data[:8]

        for i in range(8, len(data), 8):
            cipher = data[i:i+8]
            # MSDK XOR Chain: P[i] = TEA_Dec(C[i] ^ prev_D) ^ prev_C
            xored = self._xor8(cipher, prev_d)
            dec = self._decrypt_block(xored)
            plain = self._xor8(dec, prev_c)
            out += plain
            prev_d = dec
            prev_c = cipher
        return out

    def decrypt(self, data):
        out = self.decrypt_raw(data)
        return self._strip_padding(out)["payload"]

    def decrypt_with_metadata(self, data):
        out = self.decrypt_raw(data)
        return self._strip_padding(out)

    def encrypt(self, payload, randomize_padding=True):
        out = self._build_padded_plain(payload, randomize_padding=randomize_padding)

        c0 = self._encrypt_block(out[:8])
        result = c0
        prev_d = out[:8]
        prev_c = c0

        for i in range(8, len(out), 8):
            plain = out[i:i+8]
            dec = self._xor8(plain, prev_c)
            enc = self._encrypt_block(dec)
            cipher = self._xor8(enc, prev_d)
            result += cipher
            prev_d = dec
            prev_c = cipher

        return result

def parse_json_payload(payload_bytes):
    try:
        text = payload_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"Payload is not UTF-8 JSON: {exc}") from exc

    try:
        obj = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Payload is not valid JSON: {exc}") from exc
    return obj, text


def main():
    parser = argparse.ArgumentParser(description="Convert between login.dat and JSON")
    mode_group = parser.add_mutually_exclusive_group(required=True)
    mode_group.add_argument("--dat2json", action="store_true", help="Decrypt dat and write JSON")
    mode_group.add_argument("--json2dat", action="store_true", help="Read JSON and encrypt to dat")
    parser.add_argument("--input", default=None, help="Input file path")
    parser.add_argument("--output", default=None, help="Output file path")
    parser.add_argument("--key", default="ITOPITOPITOPITOP", help="16-byte TEA key as UTF-8 text")
    args = parser.parse_args()

    key = args.key.encode("utf-8")
    if len(key) != 16:
        raise ValueError("Key must be exactly 16 bytes after UTF-8 encoding")

    if args.dat2json:
        input_path = args.input or "login.dat"
        output_path = args.output or "login.json"
    else:
        input_path = args.input or "login.json"
        output_path = args.output or "login.dat"

    if not os.path.exists(input_path):
        raise FileNotFoundError(f"Input file not found: {input_path}")

    tea = MSDKTea(key)

    if args.dat2json:
        with open(input_path, "rb") as f:
            cipher_data = f.read()
        payload = tea.decrypt(cipher_data)
        json_obj, _ = parse_json_payload(payload)
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(json_obj, f, ensure_ascii=False, indent=2)
        print("dat2json successful")
        print("Input:", input_path)
        print("Output:", output_path)
        print("JSON type:", type(json_obj).__name__)
    else:
        with open(input_path, "r", encoding="utf-8") as f:
            json_obj = json.load(f)
        payload = json.dumps(json_obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        cipher_data = tea.encrypt(payload, randomize_padding=True)
        with open(output_path, "wb") as f:
            f.write(cipher_data)
        print("json2dat successful")
        print("Input:", input_path)
        print("Output:", output_path)
        print("Payload bytes:", len(payload))

if __name__ == "__main__":
    main()
