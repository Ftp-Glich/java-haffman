import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HuffmanCoder {

    private static final byte[] MAGIC = new byte[] { 'H','F','M','1' };

    static class Node implements Comparable<Node> {
        final long freq;
        final Integer symbol;
        final Node left, right;

        Node(long freq, Integer symbol, Node left, Node right) {
            this.freq = freq;
            this.symbol = symbol;
            this.left = left;
            this.right = right;
        }
        boolean isLeaf() { return symbol != null; }

        public int compareTo(Node o) {
            int c = Long.compare(this.freq, o.freq);
            if (c != 0) return c;
            if (this.isLeaf() && !o.isLeaf()) return -1;
            if (!this.isLeaf() && o.isLeaf()) return 1;
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean encode = false, decode = false, unicode = false;
        String in = null, out = null;

        for (int i=0;i<args.length;i++) {
            switch (args[i]) {
                case "-e": encode = true; break;
                case "-d": decode = true; break;
                case "-u": unicode = true; break;
                case "-i": if (i+1<args.length) in = args[++i]; break;
                case "-o": if (i+1<args.length) out = args[++i]; break;
                default: printUsage(); return;
            }
        }

        if (in==null || out==null || (encode == decode)) {
            System.err.println("Invalid arguments.");
            printUsage();
            return;
        }

        if (encode) {
            if (unicode) encodeUnicode(in, out);
            else encodeBytes(in, out);
        } else {
            decodeFile(in, out);
        }
    }

    private static void printUsage() {
        System.out.println("HuffmanCoder - command-line Huffman encoder/decoder");
        System.out.println("Usage:");
        System.out.println(" java HuffmanCoder -e -i <in> -o <out>          (encode, raw bytes mode)");
        System.out.println(" java HuffmanCoder -e -u -i <in> -o <out>       (encode, unicode code points)");
        System.out.println(" java HuffmanCoder -d -i <in> -o <out>          (decode)");
    }

    private static void encodeBytes(String inFile, String outFile) throws IOException {
        byte[] all = Files.readAllBytes(Path.of(inFile));

        Map<Integer, Long> freq = new HashMap<>();
        for (byte b : all) freq.put(b & 0xFF, freq.getOrDefault(b & 0xFF, 0L) + 1);

        Node root = buildTree(freq);
        if (root == null) throw new IllegalArgumentException("Empty input");

        Map<Integer, String> codes = new HashMap<>();
        if (root.isLeaf()) codes.put(root.symbol, "0");
        else buildCodes(root, "", codes);

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)))) {
            dos.write(MAGIC);
            dos.writeByte(0);
            dos.writeInt(freq.size());
            for (var e : freq.entrySet()) {
                dos.writeInt(e.getKey());
                dos.writeLong(e.getValue());
            }

            long totalBits = 0;
            ByteArrayOutputStream bits = new ByteArrayOutputStream();
            int cur = 0, filled = 0;

            for (byte b : all) {
                String code = codes.get(b & 0xFF);
                for (char bit : code.toCharArray()) {
                    cur = (cur << 1) | (bit == '1' ? 1 : 0);
                    filled++;
                    if (filled == 8) {
                        bits.write((byte)cur);
                        cur = 0;
                        filled = 0;
                    }
                    totalBits++;
                }
            }

            if (filled > 0) {
                cur <<= (8 - filled);
                bits.write((byte)cur);
            }

            dos.writeLong(totalBits);
            bits.writeTo(dos);
        }
    }

    private static void encodeUnicode(String inFile, String outFile) throws IOException {
        String text = Files.readString(Path.of(inFile), StandardCharsets.UTF_8);
        int[] cps = text.codePoints().toArray();

        Map<Integer, Long> freq = new HashMap<>();
        for (int cp : cps) freq.put(cp, freq.getOrDefault(cp, 0L) + 1);

        Node root = buildTree(freq);
        if (root == null) throw new IllegalArgumentException("Empty input");

        Map<Integer, String> codes = new HashMap<>();
        if (root.isLeaf()) codes.put(root.symbol, "0");
        else buildCodes(root, "", codes);

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)))) {
            dos.write(MAGIC);
            dos.writeByte(1);
            dos.writeInt(freq.size());
            for (var e : freq.entrySet()) {
                dos.writeInt(e.getKey());
                dos.writeLong(e.getValue());
            }

            long totalBits = 0;
            ByteArrayOutputStream bits = new ByteArrayOutputStream();
            int cur = 0, filled = 0;

            for (int cp : cps) {
                String code = codes.get(cp);
                for (char bit : code.toCharArray()) {
                    cur = (cur << 1) | (bit == '1' ? 1 : 0);
                    filled++;
                    if (filled == 8) {
                        bits.write((byte)cur);
                        cur = 0;
                        filled = 0;
                    }
                    totalBits++;
                }
            }

            if (filled > 0) {
                cur <<= (8 - filled);
                bits.write((byte)cur);
            }

            dos.writeLong(totalBits);
            bits.writeTo(dos);
        }
    }

    private static void decodeFile(String inFile, String outFile) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(inFile)))) {
            byte[] m = new byte[4];
            dis.readFully(m);
            if (!Arrays.equals(m, MAGIC)) throw new IOException("Invalid magic");

            int mode = dis.readUnsignedByte();
            int symbols = dis.readInt();

            Map<Integer, Long> freq = new HashMap<>();
            for (int i=0;i<symbols;i++) {
                int sym = dis.readInt();
                long f = dis.readLong();
                freq.put(sym, f);
            }

            long totalBits = dis.readLong();

            ByteArrayOutputStream rest = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = dis.read(buf)) != -1) rest.write(buf, 0, r);
            byte[] bitBytes = rest.toByteArray();

            Node root = buildTree(freq);
            if (root == null) {
                Files.write(Path.of(outFile), new byte[0]);
                return;
            }

            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            if (root.isLeaf()) {
                int sym = root.symbol;
                long count = freq.get(sym);
                if (mode == 0) {
                    for (long i=0;i<count;i++) decoded.write((byte)(sym & 0xFF));
                } else {
                    String s = new String(Character.toChars(sym));
                    byte[] b = s.getBytes(StandardCharsets.UTF_8);
                    for (long i=0;i<count;i++) decoded.write(b);
                }
                Files.write(Path.of(outFile), decoded.toByteArray());
                return;
            }

            long bitsRead = 0;
            int byteIndex = 0, bitIndex = 0;
            Node node = root;

            while (bitsRead < totalBits) {
                int b = bitBytes[byteIndex] & 0xFF;
                int bit = (b >> (7 - bitIndex)) & 1;

                bitIndex++;
                if (bitIndex == 8) {
                    bitIndex = 0;
                    byteIndex++;
                }

                bitsRead++;
                node = (bit == 0) ? node.left : node.right;

                if (node.isLeaf()) {
                    int sym = node.symbol;
                    if (mode == 0) {
                        decoded.write((byte)(sym & 0xFF));
                    } else {
                        decoded.write(new String(Character.toChars(sym)).getBytes(StandardCharsets.UTF_8));
                    }
                    node = root;
                }
            }

            Files.write(Path.of(outFile), decoded.toByteArray());
        }
    }

    private static Node buildTree(Map<Integer, Long> freq) {
        if (freq.isEmpty()) return null;
        PriorityQueue<Node> pq = new PriorityQueue<>();
        for (var e : freq.entrySet()) pq.add(new Node(e.getValue(), e.getKey(), null, null));
        while (pq.size() > 1) {
            Node a = pq.poll(), b = pq.poll();
            pq.add(new Node(a.freq + b.freq, null, a, b));
        }
        return pq.poll();
    }

    private static void buildCodes(Node n, String p, Map<Integer,String> out) {
        if (n.isLeaf()) {
            out.put(n.symbol, p.length()==0 ? "0" : p);
            return;
        }
        buildCodes(n.left, p + '0', out);
        buildCodes(n.right, p + '1', out);
    }
}
