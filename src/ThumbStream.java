import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

/**
 * ThumbStream allows thumbnail extraction from JPEG.
 * To use it, define the jpeg file when constructing the ThumbStream.
 * Then read from the stream.
 * Finally, remember to close the stream to avoid leaks.
 *
 * Stuff todo: return info about thumbnail.
 */
public class ThumbStream extends InputStream {

	// Standard markers
	// @see http://www.media.mit.edu/pia/Research/deepview/exif.html
	private final int[] JPEG_SOI = {0xff, 0xd8}; // start of image
	private final int[] JPEG_EOI = {0xff, 0xd9}; // end of image
	private final int[] EXIF_HEADER = {0x45, 0x78, 0x69, 0x66, 0x00, 0x00}; // Exif<blank><blank>
	private final int[] MOTOROLA_ALIGN = {0x4d, 0x4d}; // Big-Endian
	private final int[] INTEL_ALIGN = {0x49, 0x49}; // Little-Endian
	private final int[] TIFF_HEADER_TAIL = {0x00, 0x00, 0x00, 0x08};
	// Thumb-markers
	private final int[] THUMB_WIDTH = {0x01, 0x00};
	private final int[] THUMB_HEIGHT = {0x01, 0x01};
	private final int[] COMPRESSION_TYPE = {0x01, 0x03};
	private final int[] JPEG_OFFSET = {0x02, 0x01};
	private final int[] JPEG_SIZE = {0x02, 0x02};
	private final int[] BIT_PER_SAMPLE = {0x01, 0x02};

	// other constants
	private final int IFD_ENTRY_SIZE = 12;
	private final int BYTE_SIZE = 8;
	private final int JPEG_COMPRESSION = 6;
	private final int TIFF_COMPRESSION = 1;

	// Stream/data related fields
	private FileInputStream in;
	private ArrayDeque<Integer> bufferQueue;
	private int offset;

	// Thumbnail info
	private boolean hasThumbnail;
	private int compressionType;
	private int thumbLength;
	private int thumbOffset;
	private int samplesPerPixel;

	private boolean isBigEndian;
	
	public ThumbStream(String file) throws IOException {
		in = new FileInputStream(file);
		bufferQueue = new ArrayDeque<Integer>();

		// Look at example results from exiftool
		offset = -IFD_ENTRY_SIZE;

		hasThumbnail = false;
		compressionType = -1;

		processThumbnail();
	}
	
	private boolean getBigEndian(){
		return isBigEndian;
	}

	public boolean hasThumbnail() {
		return hasThumbnail;
	}

	/**
	 * Get the width of the thumbnail
	 * @return
	 */
	public int xThumb() {
		return -1;
	}

	/**
	 * Get the height of the thumbnail
	 * @return
	 */
	public int yThumb() {
		return -1;
	}

	/**
	 *
	 * @return
	 */
	public int getSamplesPerPixel() {
		if (compressionType == TIFF_COMPRESSION)
			return samplesPerPixel;

		return -1;
	}

	/**
	 * Get the type of thumbnail
	 * 6 - JPEG
	 * 1 - TIFF/no compression
	 * @return
	 */
	public int thumbType() {
		return compressionType;
	}

	@Override
	public int read() throws IOException {
		if (compressionType == JPEG_COMPRESSION || compressionType == TIFF_COMPRESSION || true /* REMOVE THE TRUE */ && !bufferQueue.isEmpty()) {
			return bufferQueue.pop();
		} else {
			return -1;
		}

	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	/**
	 * Get the thumbnail data
	 * @return
	 */
	public int[][] thumbData() {
		return null;
	}

	private void processThumbnail() throws IOException {
		// TODO - check that it is JPEG and has EXIF

		isBigEndian = checkEndianess();

		int entries = IFD0Entries(isBigEndian);
		int ifd1loc = IFD1Location(entries, isBigEndian);
		hasThumbnail = ifd1loc > 0;
		int ifd1entries = IFD1Entries(ifd1loc, isBigEndian);
		updateIFD1Entries(ifd1entries, isBigEndian);

		// Get thumbnail data
		fillBufferQueue(isBigEndian);
	}

	private boolean checkEndianess() throws IOException {
		int b = findBytes(EXIF_HEADER, true);
		
			int b_0 = readFile();
			int b_1 = readFile();

			boolean endianess = false;
			if (b_0 == MOTOROLA_ALIGN[0] && b_1 == MOTOROLA_ALIGN[1])
				endianess = true;
			else if (b_0 == INTEL_ALIGN[0] && b_1 == INTEL_ALIGN[1])
				endianess = false;
			else
				throw new IOException();

		return endianess;
	}

	/*
	 * Find TIFF Header and then check IFD0 Entries
	 */
	private int IFD0Entries(boolean isBigEndian) throws IOException {
		int tiff_tail = findTIFF_HEADER_TAIL(isBigEndian);
		if (tiff_tail == -1) return -1;

		int b_0 = readFile();
		int b_1 = readFile();
		return eval(new int[] {b_0, b_1}, isBigEndian);
	}

	private int IFD1Entries(int IFD1Location, boolean isBigEndian) throws IOException {
		while (offset < IFD1Location)
			readFile();

		int b_0 = readFile();
		int b_1 = readFile();
		return eval(new int[] {b_0, b_1}, isBigEndian);
	}

	private void updateIFD1Entries(int numberEntries, boolean isBigEndian) throws IOException {

		for(int i=0; i < numberEntries; i++) {
			int[] entry = new int[IFD_ENTRY_SIZE];
			readFile(entry);

			// Test width

			// Test heigh
		}
	}

	// Fill the buffer queue with the thumnail data
	// TODO - test for thumbnail data that is not in JPEG format
	private void fillBufferQueue(boolean isBigEndian) throws IOException {
		findBytes(JPEG_SOI, true);

		bufferQueue.add(JPEG_SOI[0]);
		bufferQueue.add(JPEG_SOI[1]);

		int index = 0;
		while(true) {

			int b = readFile();
			if (b == -1) break;

			if (b == JPEG_EOI[index]) {
				index++;
			} else {
				index = 0;
			}

			bufferQueue.add(b);
			if (index == JPEG_EOI.length)
				break;
		}
	}

	private int findTIFF_HEADER_TAIL(boolean isBigEndian) throws IOException {
		return findBytes(TIFF_HEADER_TAIL, isBigEndian);

	}

	private int findBytes(int[] bytes, boolean isBigEndian) throws IOException {
		int index = 0;

		while (true) {
			int b = readFile();
			if (b == -1) return -1;

			if (isBigEndian && b == bytes[index] || !isBigEndian && b == bytes[bytes.length - index - 1]) {
				index++;
			} else {
				index = 0;
			}

			if (index == bytes.length)
				return offset;
		}
	}


	private int IFD1Location(int IFD0entries, boolean isBigEndian) throws IOException {
		int ifd0offset = IFD0entries * (IFD_ENTRY_SIZE);

		for(int i = 0; i < ifd0offset; i++) {
			readFile();
		}

		int b_0 = readFile();
		int b_1 = readFile();
		int b_2 = readFile();
		int b_3 = readFile();

		return eval(new int[] {b_0, b_1, b_2, b_3}, isBigEndian);
	}

	private int readFile() throws IOException {
		int b = in.read();
		offset++;
		return b;
	}

	private void readFile(int[] out) throws IOException {
		for(int i = 0; i < out.length; i++)
			out[i] = readFile();
	}

	private int eval(int[] value, boolean isBigEndian) {
		int retval = 0;
		if (isBigEndian) {
			for(int i = value.length - 1, j = 0; i >=0; i--, j++) {
				retval |= value[i] << j*BYTE_SIZE;
			}
		}  else {
			for(int i = 0; i < value.length; i++) {
				retval |= value[i] << i*BYTE_SIZE;
			}
		}

		return retval;
	}
}