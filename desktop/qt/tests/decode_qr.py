"""Independent scanner check: compare pixels from C++ with the exact UTF-8 payload."""
import sys
from pathlib import Path
from PIL import Image
import zxingcpp
folder = Path(sys.argv[1])
expected = (folder / 'voucher-qr-test.txt').read_text(encoding='utf-8')
results = zxingcpp.read_barcodes(Image.open(folder / 'voucher-qr-test.png'))
assert len(results) == 1, f'Expected one QR, found {len(results)}'
assert results[0].text == expected, 'Decoded QR payload differs'
print('Independent QR decoding passed')
