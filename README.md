[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Character set detector library for the JVM &mdash; like [vidoss/jchardet](https://github.com/vidoss/jchardet), but with a leading `K`.

# Features

 - Plain ASCII detection.
 - **UTF-8** detection (with and without BOM).
 - **UTF-16** (BE, LE) detection (with and without BOM).
 - Chinese detection (**GB 2312**, **GBK**, **GB 18030**, **Big5**).
 - Mode Line based detection in source code files of known types, e.g.:

```python
#!/usr/bin/env python3
# -*- coding: ISO-8859-15 -*-

print("Hello, World!")
``` 
