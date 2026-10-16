#!/usr/bin/env python3
"""Create a fixed synthetic image package for the isolated SDK attachment test."""

import hashlib
from pathlib import Path
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib


PACKAGE_UID = "08a051c9-320b-4851-a31c-ff1e7ea65d87"
IMAGE_ENTRY = "fixture/checker.png"


def chunk(kind, data):
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data)))


def image():
    # Pixel data only: no camera, EXIF, location, text or external source file.
    pixels = b"".join(b"\0" + b"".join(
        b"\xff\0\0" if (x // 8 + y // 8) % 2 else b"\0\xff\xff"
        for x in range(64)) for y in range(64))
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


def create(destination):
    root = ET.Element("MissionPackageManifest", version="2")
    config = ET.SubElement(root, "Configuration")
    for name, value in [("uid", PACKAGE_UID), ("name", "TMM-IMAGE-PROBE"),
                        ("onReceiveImport", "true"), ("onReceiveDelete", "false")]:
        ET.SubElement(config, "Parameter", name=name, value=value)
    contents = ET.SubElement(root, "Contents")
    ET.SubElement(contents, "Content", zipEntry=IMAGE_ENTRY, ignore="false")
    png = image()
    with zipfile.ZipFile(destination, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in [("MANIFEST/manifest.xml", ET.tostring(root, encoding="utf-8",
                                                               xml_declaration=True)),
                           (IMAGE_ENTRY, png)]:
            entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    verify(destination)
    print("Package UID:", PACKAGE_UID)
    print("Image SHA-256:", hashlib.sha256(png).hexdigest())


def verify(destination):
    # Check the actual archive, not only the in-memory source objects.
    with zipfile.ZipFile(destination) as archive:
        assert archive.namelist() == ["MANIFEST/manifest.xml", IMAGE_ENTRY]
        assert archive.testzip() is None
        manifest = archive.read("MANIFEST/manifest.xml")
        assert not any(value in manifest.lower() for value in
                       [b"localpath", b"imageref", b"file:", b"content:", b"http:", b"https:"])
        root = ET.fromstring(manifest)
        assert root.find("Contents/Content").get("zipEntry") == IMAGE_ENTRY
        png = archive.read(IMAGE_ENTRY)
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
    offset, chunks, pixels = 8, [], b""
    while offset < len(png):
        size = struct.unpack_from(">I", png, offset)[0]
        kind = png[offset + 4:offset + 8]
        data = png[offset + 8:offset + 8 + size]
        crc = struct.unpack_from(">I", png, offset + 8 + size)[0]
        assert crc == zlib.crc32(kind + data)
        chunks.append(kind)
        if kind == b"IDAT":
            pixels += zlib.decompress(data)
        offset += 12 + size
    assert chunks == [b"IHDR", b"IDAT", b"IEND"]
    assert len(pixels) == 64 * (1 + 64 * 3)
    assert png == image()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 scripts/create-attachment-fixture.py OUTPUT.zip (must not exist)")
    create(Path(sys.argv[1]))
