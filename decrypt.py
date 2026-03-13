import struct
import os

class MSDKTea:
    def __init__(self, key):
        self.key = key
        self.rounds = 16
        self.delta = 0x9e3779b9

    def _decrypt_block(self, v):
        v0, v1 = struct.unpack(">2I", v)
        k = struct.unpack(">4I", self.key)
        sum = (self.delta * self.rounds) & 0xffffffff
        for _ in range(self.rounds):
            v1 = (v1 - (((v0 << 4) + k[2]) ^ (v0 + sum) ^ ((v0 >> 5) + k[3]))) & 0xffffffff
            v0 = (v0 - (((v1 << 4) + k[0]) ^ (v1 + sum) ^ ((v1 >> 5) + k[1]))) & 0xffffffff
            sum = (sum - self.delta) & 0xffffffff
        return struct.pack(">2I", v0, v1)

    def decrypt(self, data):
        if len(data) < 16 or len(data) % 8 != 0:
            return None
        
        # MSDK Variant TEA CBC Decryption
        # Block 0 initialization
        d0 = self._decrypt_block(data[:8])
        out = d0
        prev_d = d0
        prev_c = data[:8]
        
        for i in range(8, len(data), 8):
            cipher = data[i:i+8]
            # MSDK XOR Chain: P[i] = TEA_Dec(C[i] ^ prev_D) ^ prev_C
            xored = bytes(a ^ b for a, b in zip(cipher, prev_d))
            dec = self._decrypt_block(xored)
            plain = bytes(a ^ b for a, b in zip(dec, prev_c))
            out += plain
            prev_d = dec # Key difference: use raw TEA output as feedback
            prev_c = cipher

        # Remove MSDK/OICQ padding
        # Header: first 3 bits (mask 0x07) indicate number of padding bytes at start, plus 3 constant bytes
        pos = (out[0] & 7) + 3
        # Trailer: last 7 bytes are ignored
        return out[pos:-7]

if __name__ == "__main__":
    # The key "ITOPITOPITOPITOP" is hardcoded/generated in libMSDKCore.so
    key = b"ITOPITOPITOPITOP"
    file_path = "itop_login.txt"
    
    if os.path.exists(file_path):
        try:
            with open(file_path, "rb") as f:
                data = f.read()
            
            tea = MSDKTea(key)
            result = tea.decrypt(data)
            if result:
                print("Decryption Successful!")
                print("-" * 20)
                print(f"JWT Token:\n{result.decode('utf-8', errors='ignore')}")
                print("-" * 20)
            else:
                print("Decryption failed (invalid data or key).")
        except Exception as e:
            print(f"An error occurred: {e}")
    else:
        print(f"Error: Target file not found at {file_path}")