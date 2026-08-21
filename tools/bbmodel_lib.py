"""Leitura de arquivos .bbmodel do Blockbench.

Dois formatos aparecem na pasta do dono:

* JSON puro, do "Save Project" / export normal — o que ele manda pelo Downloads;
* JSON comprimido com prefixo `<lz>`, que e o formato dos autosaves em
  `AppData/Roaming/Blockbench/backups`.

O comprimido NAO e LZString, apesar do prefixo. Blockbench usa
`LZUTF8.compress(json, {outputEncoding: 'StorageBinaryString'})` — conferido lendo
`app.asar` da instalacao, nao de memoria. Sao dois passos independentes:

1. StorageBinaryString -> bytes: 15 bits uteis por char UTF-16, com `\\0` escapado
   como U+8002 e um marcador final `0x8000 | (len % 2)`;
2. bytes -> bytes: LZ77 sobre UTF-8, onde um byte `11xxxxxx` seguido de byte que
   *nao* seja continuacao UTF-8 abre um ponteiro (tamanho nos 5 bits baixos,
   distancia em 1 ou 2 bytes).

`load_bbmodel` aceita os dois formatos.

Um dono por arquivo: quem le .bbmodel usa este modulo. Ver memoria
[[worldgen-script-ownership]].
"""

import json
import os


def _binary_string_to_bytes(text):
    """Encoding.BinaryString.decode do LZUTF8."""
    out = bytearray()
    carry = 0
    used = 0
    for char in text:
        value = ord(char)
        if value >= 32768:
            if value == 32769:      # marcador de comprimento impar
                del out[-1:]
            used = 0
            continue
        if used == 0:
            carry = value
        else:
            merged = (carry << used) | (value >> (15 - used))
            out.append((merged >> 8) & 0xFF)
            out.append(merged & 0xFF)
            carry = value & ((1 << (15 - used)) - 1)
        used = 0 if used == 15 else used + 1
    return bytes(out)


def _lzutf8_decompress_block(data):
    """Decompressor.decompressBlock do LZUTF8."""
    out = bytearray()
    index = 0
    total = len(data)
    while index < total:
        head = data[index]
        if head >> 6 != 3:
            out.append(head)
            index += 1
            continue
        wide = (head >> 5) == 7
        if index == total - 1 or (index == total - 2 and wide):
            break                                   # sequencia truncada no fim
        if data[index + 1] >> 7 == 1:
            out.append(head)                        # continuacao UTF-8, literal
            index += 1
            continue
        length = head & 31
        if wide:
            distance = (data[index + 1] << 8) | data[index + 2]
            index += 3
        else:
            distance = data[index + 1]
            index += 2
        start = len(out) - distance
        for offset in range(length):
            out.append(out[start + offset])
    return bytes(out)


def decompress_lzutf8_storage(text):
    """Desfaz `<lz>` de autosave do Blockbench e devolve a string JSON."""
    raw = _binary_string_to_bytes(text.replace("耂", "\0"))
    return _lzutf8_decompress_block(raw).decode("utf-8", errors="strict")


def _decompress(length, reset_value, get_next_value):
    """Nucleo do LZString.decompress, identico ao JS de referencia."""
    dictionary = {}
    enlarge_in = 4
    dict_size = 4
    num_bits = 3
    entry = ""
    result = []

    data_val = get_next_value(0)
    data_position = reset_value
    data_index = 1

    for i in range(3):
        dictionary[i] = i

    bits = 0
    maxpower = 2 ** 2
    power = 1
    while power != maxpower:
        resb = data_val & data_position
        data_position >>= 1
        if data_position == 0:
            data_position = reset_value
            data_val = get_next_value(data_index)
            data_index += 1
        bits |= (1 if resb > 0 else 0) * power
        power <<= 1

    if bits == 0:
        bits = 0
        maxpower = 2 ** 8
        power = 1
        while power != maxpower:
            resb = data_val & data_position
            data_position >>= 1
            if data_position == 0:
                data_position = reset_value
                data_val = get_next_value(data_index)
                data_index += 1
            bits |= (1 if resb > 0 else 0) * power
            power <<= 1
        c = chr(bits)
    elif bits == 1:
        bits = 0
        maxpower = 2 ** 16
        power = 1
        while power != maxpower:
            resb = data_val & data_position
            data_position >>= 1
            if data_position == 0:
                data_position = reset_value
                data_val = get_next_value(data_index)
                data_index += 1
            bits |= (1 if resb > 0 else 0) * power
            power <<= 1
        c = chr(bits)
    elif bits == 2:
        return ""

    dictionary[3] = c
    w = c
    result.append(c)

    while True:
        if data_index > length:
            return ""

        bits = 0
        maxpower = 2 ** num_bits
        power = 1
        while power != maxpower:
            resb = data_val & data_position
            data_position >>= 1
            if data_position == 0:
                data_position = reset_value
                data_val = get_next_value(data_index)
                data_index += 1
            bits |= (1 if resb > 0 else 0) * power
            power <<= 1

        c = bits
        if c == 0:
            bits = 0
            maxpower = 2 ** 8
            power = 1
            while power != maxpower:
                resb = data_val & data_position
                data_position >>= 1
                if data_position == 0:
                    data_position = reset_value
                    data_val = get_next_value(data_index)
                    data_index += 1
                bits |= (1 if resb > 0 else 0) * power
                power <<= 1
            dictionary[dict_size] = chr(bits)
            dict_size += 1
            c = dict_size - 1
            enlarge_in -= 1
        elif c == 1:
            bits = 0
            maxpower = 2 ** 16
            power = 1
            while power != maxpower:
                resb = data_val & data_position
                data_position >>= 1
                if data_position == 0:
                    data_position = reset_value
                    data_val = get_next_value(data_index)
                    data_index += 1
                bits |= (1 if resb > 0 else 0) * power
                power <<= 1
            dictionary[dict_size] = chr(bits)
            dict_size += 1
            c = dict_size - 1
            enlarge_in -= 1
        elif c == 2:
            return "".join(result)

        if enlarge_in == 0:
            enlarge_in = 2 ** num_bits
            num_bits += 1

        if c in dictionary:
            entry = dictionary[c]
        else:
            if c == dict_size:
                entry = w + w[0]
            else:
                return None

        result.append(entry)

        dictionary[dict_size] = w + entry[0]
        dict_size += 1
        enlarge_in -= 1

        w = entry

        if enlarge_in == 0:
            enlarge_in = 2 ** num_bits
            num_bits += 1


def decompress_from_utf16(compressed):
    """LZString.decompressFromUTF16 — o que o Blockbench usa nos autosaves."""
    if compressed is None:
        return ""
    if compressed == "":
        return None
    return _decompress(len(compressed), 16384,
                       lambda index: ord(compressed[index]) - 32)


def load_bbmodel(path):
    """Devolve o dict do projeto, seja JSON puro ou autosave comprimido."""
    with open(path, "r", encoding="utf-8", newline="") as handle:
        raw = handle.read()
    if raw.startswith("<lz>"):
        raw = decompress_lzutf8_storage(raw[4:])
    if raw and raw[0] == "﻿":
        raw = raw[1:]
    return json.loads(raw)


def newest_backup(pattern_fragment, backups_dir=None):
    """Autosave mais recente cujo nome contenha o fragmento dado."""
    if backups_dir is None:
        backups_dir = os.path.expanduser(
            "~/AppData/Roaming/Blockbench/backups")
    hits = [os.path.join(backups_dir, name)
            for name in os.listdir(backups_dir)
            if pattern_fragment in name and name.endswith(".bbmodel")]
    if not hits:
        return None
    return max(hits, key=os.path.getmtime)
