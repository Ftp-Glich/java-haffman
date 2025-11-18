
# Huffman Lab - Files created

Files:
- HuffmanCoder.java            : Java source code (command-line encoder/decoder)
- test1_10same.txt            : Text file with 10 identical characters ("1111111111")
- test2_20_3symbols.txt       : Text file with 20 characters "11111111112222233333"
- FORMAT_DESCRIPTION.txt      : Explanation of the encoded file binary format and how decoding works
- BUILD_AND_RUN.txt           : Instructions to compile and run, plus examples for encoding/decoding

# BUILD&RUN
1) Compile:
   Open terminal in the directory containing HuffmanCoder.java and run:
   javac HuffmanCoder.java
   This will produce HuffmanCoder.class (the requested binary class-file).

2) Encode examples:
    - Encode test1 (raw bytes mode):
      java HuffmanCoder -e -i test1_10same.txt -o test1_10same.hfm
    - Encode test2 (unicode mode, treat input as UTF-8 code points):
      java HuffmanCoder -e -u -i test2_20_3symbols.txt -o test2_20_3symbols.hfm
    - Encode a compiled class file (binary test):
      javac HuffmanCoder.java
      java HuffmanCoder -e -i HuffmanCoder.class -o HuffmanCoder_class.hfm
      (note: do not use -u for binary files)

3) Decode:
    - Decode a .hfm file back to original:
      java HuffmanCoder -d -i test2_20_3symbols.hfm -o test2_decoded.txt

4) Verify:
   On UNIX-like systems you can compare original and decoded:
   cmp --silent originalfile decodedfile && echo "UNCHANGED" || echo "DIFFER"
   Or use 'sha256sum' to compare checksums.

5) Notes about Unicode:
    - Use '-u' on encode to treat the input as a UTF-8 text file where symbols are Unicode code points.
    - When decoding, the mode (unicode vs bytes) is stored in the encoded file header, so you don't need to pass '-u' when decoding.

# Result file structure

Encoded file format (binary) - human-readable description

Header:
- 4 bytes: ASCII magic "HFM1" to identify the format
- 1 byte : mode (0 = raw-bytes mode, 1 = unicode codepoints mode)

Dictionary section:
- 4 bytes: number of distinct symbols N (big-endian int)
- Then N entries, each:
    - 4 bytes: symbol id (int)
        * If mode==0 (raw-bytes): symbol id is 0..255 representing the original byte value
        * If mode==1 (unicode): symbol id is the Unicode code point (int)
    - 8 bytes: frequency (long) -- how many times that symbol appears in original file

Encoded bitstream metadata:
- 8 bytes: total number of encoded bits (long)

Encoded data:
- Remaining bytes: the concatenation of Huffman code bits for the original symbols, packed MSB-first per byte.
  The first encoded bit is the highest-order bit of the first byte written.
  The last byte is padded with zeros on the right (least significant bits) if needed.

Decoding:
- Read the frequencies and reconstruct the Huffman tree using the same algorithm used for encoding.
- Read exactly 'total number of encoded bits' bits from the bitstream and traverse the tree bit-by-bit to extract symbols.
- If a single symbol exists in the dictionary (input had only one distinct symbol), decode by repeating that symbol frequency times.
- For unicode mode, decoded code points are written as UTF-8 bytes to the output file.

Notes:
- This format stores the symbol frequencies; therefore the decoder can rebuild the same Huffman tree.
- The implementation uses big-endian integer and long encodings via Java Data{Input,Output}Stream routines.
