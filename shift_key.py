key = "e1f0a680af334194a9d48fbadddfe6b7.pyUZnxkfgsf5GJw9"
shifted = ''.join(chr(ord(c) - 1) for c in key)
print("原始:", key, len(key))
print("移位:", shifted, len(shifted))
# 验证还原
restored = ''.join(chr(ord(c) + 1) for c in shifted)
print("还原:", restored)
print("匹配:", restored == key)
