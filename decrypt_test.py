import base64, sys
try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "cryptography"])
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

encrypted_b64 = 'AcpU8cm4nIfO+b0BQUZvG41D2SPnCNWVErIsx/jGW2viuheGeHQ1DsvafqjFROnI+m5lOvqa4j9vz20TJw/KLjkCUYWGvSR72GSY3UR07TjCOA=='
raw = base64.b64decode(encrypted_b64)
print('Total bytes:', len(raw))
print('Version byte:', raw[0])
key = b'ShrimpFarm2024!!'
iv = raw[1:13]; ct = raw[13:-16]; tag = raw[-16:]
c = Cipher(algorithms.AES(key), modes.GCM(iv, None))
d = c.decryptor()
p = d.update(ct) + d.finalize_with_tag(tag)
print('Decrypted:', repr(p.decode()))
